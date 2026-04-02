# xtimer 线上 CPU 标高排查 SOP

## 一、排查目标

xtimer 这类 CPU 高问题，最容易犯的错是：

- 一上来猜 GC
- 一上来猜机器太小
- 一上来就改线程池参数

更稳的路径是：

> 先找到最热线程，再把线程热点映射回 `SchedulerWorker / TriggerWorker / TriggerTimerTask / TaskCache / TaskMapper` 这条真实链路。

## 二、标准步骤

### 1. 看机器或容器

先确认：

- 是不是 xtimer 这个 Java 进程本身在高
- 是单核高，还是多核同时高

### 2. 找热点线程

```bash
top -Hp <pid>
```

要记住：

- `pid` 是 Java 进程
- `tid` 是 Linux 线程 ID

### 3. 把 tid 转成 nid

```bash
printf '%x\n' <tid>
```

### 4. 导线程栈

```bash
jstack <pid> | grep -A 20 <nid>
```

或者：

```bash
jcmd <pid> Thread.print -l
```

### 5. 抓 CPU 火焰图

```bash
./profiler.sh -e cpu -d 15 -f cpu.html <pid>
```

火焰图的作用不是替代线程栈，而是：

- 确认哪条调用链最热
- 看清热点到底是 empty scan、DB fallback、callback 构造还是锁竞争

### 6. 结合 xtimer 业务证据收敛根因

至少把这些证据放在一起看：

- 热点线程名
- 线程栈
- 火焰图
- `schedulerPool` / `triggerPool` 的 active / queue / reject
- Redis `rangeByScore` RT
- `taskMapper.getTasksByTimeRange` RT
- callback RT
- 同一 `minuteBucketKey` 的重复命中情况

## 三、xtimer 常见根因分类

### 1. 空 minuteBucketKey 持续扫描

典型关键词：

- `TriggerWorker`
- `TriggerTimerTask`
- empty scan
- `rangeByScore` miss

### 2. DB fallback 查询风暴

典型关键词：

- `taskMapper.getTasksByTimeRange`
- Redis miss
- 同一 `minuteBucketKey` 高频重复
- callback 上下文构造

### 3. 线程池模型失衡

典型关键词：

- `schedulerPool.queueSize`
- `triggerPool.queueSize`
- 大队列
- 背压不足

### 4. 过度序列化 / 日志构造

典型关键词：

- callback body rebuild
- `notify_http_param`
- log string build

## 四、止血和治理要分开

### 止血

- 限流
- 降批次
- 摘异常 minuteBucketKey
- 扩容

### 治理

- 改扫描退避
- 改 fallback 退避
- 改线程池边界和水位控制
- 改监控和告警

## 五、最核心的一句话

CPU 高不是一个根因，它只是一个现象。

最稳的表达是：

> 我会先把 xtimer 的热点线程和 Java 栈拿出来，再结合 queueSize、Redis RT、DB fallback RT 和 callback RT，看它到底是 empty scan、fallback 查询风暴还是线程池模型失衡，然后再决定止血和治理动作。 
