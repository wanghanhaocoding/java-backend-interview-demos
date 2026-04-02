# xtimer CPU 案例：空 minuteBucketKey 持续扫描把 TriggerWorker 的 Timer 线程打热

## 一、这个案例怎么和你的简历对上

这个案例直接挂你第一个项目：

- `ScheduleCenter 定时调度中心`
- 真实代码：`bitstorm-svr-xtimer`
- 真实角色：`SchedulerWorker / SchedulerTask / TriggerWorker / TriggerTimerTask / TaskCache`

一句话背景：

> xtimer 为了保证秒级触发，SchedulerWorker 每秒都会提交 `5 个 bucket * 2 个分钟窗口` 的分片给 SchedulerTask；抢到锁后的 TriggerWorker 会启动 Timer 对 minuteBucketKey 持续扫描一整分钟。某次高峰期里，很多分片其实没有真实到期任务，但 TriggerTimerTask 仍然每秒做 `rangeByScore`、空结果判断和 fallback guard，最终不是 callback 线程，而是 TriggerWorker 里的 Timer 线程先把 CPU 打高了。

## 二、出现过程

- 实例 CPU 持续偏高
- 真实 callback 量没有同步上涨
- `triggerPool` 未必打满，但 Timer 线程一直很热
- 日志里能看到很多空 minuteBucketKey 扫描、空命中和 fallback guard

## 三、排查过程

### 第 1 步：先确认是不是 Java 进程在高

```bash
top -Hp <pid>
```

看到的通常是：

- 某个 `Timer` 或 `trigger-*` 线程接近满核
- 热点线程名本身就会暴露方向

### 第 2 步：把热点线程映射到 Java 栈

```bash
printf '%x\n' <tid>
jstack <pid> | grep -A 20 <nid>
```

你通常会看到热点落在这条链路附近：

- `TriggerWorker.work`
- `TriggerTimerTask.run`
- `TaskCache.getTasksFromCache`
- 空 minuteBucketKey 的 `rangeByScore` 和 guard 逻辑

### 第 3 步：再看火焰图

如果环境里有 `async-profiler`：

```bash
./profiler.sh -e cpu -d 15 -f cpu.html <pid>
```

火焰图通常会把最宽的方法栈压在：

- empty scan
- key/window 构造
- 空日志拼装
- fallback guard

## 四、根因

根因不是“业务量真的高”，而是：

- `TriggerWorker` 长生命周期扫描本身就很多
- 空 minuteBucketKey 没有及时退避
- 空扫路径还夹带额外判断和对象构造
- `schedulerPool` 和 `triggerPool` 的负载没有反向约束扫描侧

## 五、解决过程

### 1. 先止血

- 降低扫描批次
- 临时摘掉异常 minuteBucketKey
- 扩容核心节点摊薄热点

### 2. 根因治理

- 对空 minuteBucketKey 尽快退避，不要无意义地每秒空扫
- 缩短 TriggerWorker 单次持有线程的生命周期
- 让 queueSize / activeCount 反向约束抢锁和扫描
- 给 empty scan、rangeByScore miss、fallback guard 次数补指标

## 六、沉淀过程

1. 固化 `top -Hp -> jstack -> 火焰图` 的排查 SOP
2. 把 empty minuteBucketKey 扫描次数列入调度看板
3. 评审时明确禁止“空扫不退避”的扫描模型

## 七、1 分钟面试回答版

> 我在 ScheduleCenter 里准备过一个比较典型的 CPU 高案例，真实场景是 xtimer 的 TriggerWorker 在空 minuteBucketKey 上持续扫描。因为 SchedulerWorker 每秒都在提交 bucket 分片，抢到锁后的 TriggerWorker 又会扫完整个分钟窗口，如果空分片没有及时退避，就会反复执行 rangeByScore 和 guard 逻辑，CPU 先被 Timer 线程打高。排查时我先用 `top -Hp` 找热点线程，再把 tid 转成 nid，用 `jstack` 确认热点落在 TriggerWorker / TriggerTimerTask / TaskCache 这条链路；如果需要，再用火焰图验证是 empty scan 而不是业务 callback 热点。处理上先限批次和扩容止血，后面把 empty scan 退避、扫描生命周期和水位背压都补上。 
