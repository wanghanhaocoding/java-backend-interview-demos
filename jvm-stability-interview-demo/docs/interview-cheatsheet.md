# JVM 稳定性问题面试速查

## 一、如果面试官问：你线上遇到过 OOM / Full GC / 死锁 / 线程卡住吗？

你可以先这样总答：

> 遇到过，类型不一样，排查路径也不一样。像我在 `ScheduleCenter / bitstorm-svr-xtimer` 这条链路里，通常先暴露出来的不是直接 OOM，而是 Full GC 频繁、调度 RT 抖动和任务触发延迟。因为 `SchedulerWorker` 每秒都在继续提 minute bucket 分片，但抢到锁后的 `TriggerWorker.work` 会持续扫接近一分钟；一旦线程池和深队列积压，再叠加 Redis `rangeByScore`、DB fallback `taskMapper.getTasksByTimeRange`、任务对象和回调上下文，这些对象会逐步晋升到老年代。只有继续恶化时，才会进一步打到积压型 OOM。

## 二、四类案例的定位关键词

### 1. OOM
- `heap dump`
- `MAT`
- `长期持有对象`
- `无上限集合`
- `积压型 OOM`

### 2. Full GC
- `Old 区高`
- `Full GC 频繁`
- `schedulerPool / triggerPool`
- `队列积压`
- `rangeByScore / taskMapper.getTasksByTimeRange`

### 3. 死锁
- `线程 dump`
- `BLOCKED`
- `timer_task / xtimer`
- `waiting to lock`
- `锁顺序不一致`

### 4. 线程定位
- `jstack`
- `jcmd Thread.print`
- `线程名`
- `方法栈`

## 三、我排查 Full GC / OOM 常用的三个方向

### 1. 先看 GC 指标
- `Old 区占用`
- `Full GC 次数`
- `停顿时间`

### 2. 再看线程池和队列积压
- `schedulerPool.activeCount / queueSize`
- `triggerPool.activeCount / queueSize`
- 单个分片扫描生命周期是不是过长

### 3. 最后看 Redis / DB RT
- Redis `rangeByScore`
- DB fallback `taskMapper.getTasksByTimeRange`
- 下游回调 RT

## 四、一个万能收尾句

> 我现在看这类问题，都会尽量区分“现象”和“根因”。比如 OOM 不是简单堆太小，Full GC 不是简单 GC 参数问题，死锁也不是简单线程多，而是对象生命周期、批处理模型、锁设计和任务投递模型出了问题。排查时我会先看 GC 指标，再看线程池/队列积压，最后看 Redis / DB / 回调 RT，然后回到 xtimer 的业务链路去收敛根因。
