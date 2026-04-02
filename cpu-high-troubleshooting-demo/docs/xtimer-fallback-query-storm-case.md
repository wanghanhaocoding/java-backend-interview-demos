# xtimer CPU 案例：Redis 抖动后 DB fallback 查询风暴把 CPU 打高

## 一、这个案例怎么和你的简历对上

这个案例同样挂在你第一个项目：

- `ScheduleCenter 定时调度中心`
- 真实代码：`bitstorm-svr-xtimer`
- 关键方法：`TriggerTimerTask.getTasksByTime -> TaskCache.getTasksFromCache -> taskMapper.getTasksByTimeRange`

一句话背景：

> xtimer 正常情况下从 Redis ZSET 里按秒拉 minuteBucketKey 对应的到期任务，但某次 Redis 抖动后，TriggerTimerTask 频繁走 `taskMapper.getTasksByTimeRange` 做 DB fallback。因为问题 minuteBucketKey 没有退避，少量 key 在几秒内被反复查询、反复组装 TaskModel 和 callback 上下文，最终 DB、日志和 CPU 一起被放大。

## 二、出现过程

- 整体流量没有显著暴涨
- 但实例 CPU 一直居高不下
- DB RT 和日志量同步上升
- 同一个 minuteBucketKey 反复出现在异常日志里

## 三、排查过程

### 第 1 步：先抓热点线程

```bash
top -Hp <pid>
```

经常能看到：

- fallback 查询线程很热
- 回调上下文构造线程也可能跟着热

### 第 2 步：结合线程栈看是不是 fallback storm

```bash
printf '%x\n' <tid>
jstack <pid> | grep -A 20 <nid>
```

通常会看到热点落在：

- `taskMapper.getTasksByTimeRange`
- `TaskCache.getTasksFromCache`
- `TriggerTimerTask.getTasksByTime`
- callback body / `notify_http_param` 构造

### 第 3 步：把线程热点和业务证据对上

不要只看线程栈，还要看：

- 同一 `minuteBucketKey` 的连续 fallback 次数
- Redis RT
- DB fallback RT
- `triggerPool.queueSize`
- callback RT

## 四、根因

真正根因一般是：

- Redis 异常把链路切到 DB fallback
- 问题 minuteBucketKey 没有退避或限频
- `taskMapper.getTasksByTimeRange` 在异常路径下被高频调用
- 查询结果、TaskModel 和 callback 上下文被反复构造

## 五、解决过程

### 1. 先止血

- 对异常 minuteBucketKey 限频
- 控制 fallback 结果集规模
- 必要时摘掉异常 bucket 或扩容节点

### 2. 根因治理

- 给 fallback 路径加退避和限流
- 让 `triggerPool` 水位反向约束 fallback 调度
- 限制 DB fallback 每次返回的任务数
- 对 minuteBucketKey 的连续异常次数做告警和熔断

## 六、沉淀过程

1. 给 minuteBucketKey fallback 次数、DB RT、callback RT 补指标
2. 固化 Redis 异常时的 fallback 水位规则
3. 在 xtimer 侧补“同 key 高频 fallback”告警

## 七、1 分钟面试回答版

> 我在 ScheduleCenter 里还准备过一个比较典型的 CPU 高案例，场景是 xtimer 的 Redis 取数异常后，TriggerTimerTask 频繁走 `taskMapper.getTasksByTimeRange` 做 DB fallback。因为问题 minuteBucketKey 没有退避，少量 key 在几秒内会被反复查询、反复构造 TaskModel 和 callback 上下文，CPU、DB 和日志会一起被放大。排查时我先用 `top -Hp` 找热点线程，再用 `jstack` 确认热点落在 fallback 查询和上下文构造上，然后再结合 Redis RT、DB RT、queueSize 和 minuteBucketKey 的重复命中证据收敛根因。处理上先限频和控结果集止血，后面把 fallback 退避、线程池水位约束和告警体系补齐。 
