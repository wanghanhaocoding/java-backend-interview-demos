# Full GC 案例：bitstorm-svr-xtimer 里分钟分片长扫描先把老年代顶高

## 一、这个案例怎么和你的简历第一个项目对上

这个案例直接对齐你简历里的第一个项目 `ScheduleCenter 定时调度中心`，真实代码对应 `bitstorm-svr-xtimer`。

我建议你面试里直接用这条链路来讲，而不是说成泛化的“调度系统容易 Full GC”：

1. `MigratorWorker` 先把未来时间窗任务迁移到 Redis `ZSet`
2. `SchedulerWorker` 通过 `@Scheduled(fixedRate = 1000)` 每秒调度一次
3. 每次会对 `5 个 bucket * 2 个分钟窗口` 提交分片任务
4. `SchedulerTask.asyncHandleSlice` 抢到分布式锁后，直接进入 `TriggerWorker.work`
5. `TriggerWorker.work` 内部用 `Timer + CountDownLatch` 持续扫完整个分钟窗口，单个分片会挂住接近 `60s`
6. `TriggerTimerTask` 每秒做 Redis `rangeByScore`，异常时走 `taskMapper.getTasksByTimeRange` 做 DB fallback
7. 到期任务再交给 `TriggerPoolTask` 去构造任务对象和回调上下文

一句话背景：

> 在真实 xtimer 链路里，问题通常不是一上来直接 OOM，而是先出现 Full GC 频繁。因为外层每秒都在继续提交新的分钟分片任务，但抢到锁后的扫描任务往往要持续接近一分钟，线程池吞吐跟不上时，分片任务会在线程池和大队列里积压。再叠加 Redis、DB fallback、任务对象和回调上下文，这些对象会逐步晋升到老年代，先表现为 Full GC 频繁、调度 RT 抖动和任务触发延迟升高。

## 二、出现过程

- `SchedulerWorker` 每秒都会继续提交新的 minuteBucket 分片
- 抢到锁后的 `TriggerWorker.work` 不会瞬时结束，而是持续扫描接近一分钟
- `schedulerPool` 和 `triggerPool` 的任务开始堆积
- Redis RT 抖动时，`TriggerTimerTask` 开始更多走 DB fallback
- 系统未必立刻挂，但调度 RT、任务触发延迟、回调耗时开始同步抖动

监控上的第一现场通常是：

- Old 区占用持续偏高
- Full GC 次数明显增加
- Full GC 停顿时间拉长
- Full GC 后回落幅度差

## 三、排查过程

### 第 1 步：先看线程池是不是已经失衡

- `schedulerPool.activeCount`
- `schedulerPool.queueSize`
- `triggerPool.activeCount`
- `triggerPool.queueSize`

如果 `activeCount` 长时间顶满、`queueSize` 还在涨，就说明不是瞬时尖峰，而是 xtimer 这套“每秒提任务、单任务挂很久”的模型已经失衡了。

### 第 2 步：再看 Redis / DB / 回调 RT

- Redis `rangeByScore` RT
- `taskMapper.getTasksByTimeRange` RT
- 回调 HTTP RT

这一步是为了确认：到底是模型本身先有问题，还是 Redis / DB / 回调变慢把问题放大。

### 第 3 步：看 GC 指标

常见命令：

```bash
jstat -gcutil <pid> 1000 20
jcmd <pid> GC.heap_info
jmap -histo:live <pid>
```

观察到：

- Young 区回收后仍有大量对象存活
- Old 区长期高位
- Full GC 频率明显高于正常值

### 第 4 步：回到 xtimer 对象流转链路

这时要把对象为什么“活得久”讲清楚：

1. 分片任务本身在线程池和队列里停留很久
2. `TriggerTimerTask` 每秒都在构造 Redis 结果集、任务对象、回调上下文
3. Redis 异常时 DB fallback 会把对象数量进一步放大
4. 这些对象跨过多轮 Minor GC 后进入老年代

问题在于：

- 单个扫描任务生命周期过长
- 线程池队列过大，把压力藏进堆里
- Redis/DB fallback 和回调对象叠加，放大对象体积和存活时间

## 四、根因

真正根因不是“JVM 参数太小”，而是这三个条件叠在一起：

- 长生命周期扫描任务
- 深队列、大量积压
- 缺少背压，异常路径下 Redis / DB fallback 又继续放大对象

## 五、解决过程

### 1. 先止血

- 缩小扫描批次
- 限制并发
- 扩容核心节点

### 2. 根因治理

- 把线程池队列改成有界队列
- 拆分调度线程池和触发线程池
- 减少无效扫描
- 严格控制 fallback 结果集大小
- 增加背压和监控告警

## 六、沉淀过程

1. 建了 GC、线程池队列、Redis/DB RT 联动看板
2. 做了分钟分片批次与老年代占用的压测基线
3. 规定长生命周期任务必须配有界队列和背压
4. 对 Full GC 次数、调度 RT、触发延迟补了告警

## 七、1 分钟面试回答版

> 我这个定时调度中心里，实际更先暴露出来的通常不是直接 OOM，而是 Full GC 频繁。因为在真实 xtimer 里，SchedulerWorker 每秒都会把当前分钟和上一分钟补偿的 5 个 bucket 全部提交给 schedulerPool，但抢到锁后的 TriggerWorker.work 会持续扫完整个分钟窗口，单个分片生命周期接近一分钟。如果线程池吞吐跟不上，分片任务就会在线程池和大队列里积压，再叠加 Redis 查询、DB fallback、任务对象构造和回调上下文，这些对象会逐步晋升到老年代，先表现成 Full GC 频繁、调度 RT 抖动和任务触发延迟升高。排查时我会先看 GC 指标、线程池和队列，再看 Redis/DB RT，确认是不是外部依赖变慢把上游拖住了。定位下来，这类问题通常不是单点故障，而是长生命周期扫描任务、大队列和缺少背压共同导致的。处理上先缩批次、限并发、扩容止血，长期再把线程池队列改成有界、拆分调度和触发线程池、减少无效扫描、控制 fallback 结果集大小，并增加背压和监控告警。
