# Full GC 案例：bitstorm-svr-xtimer 的分钟分片长扫描先把老年代顶高

## 1. 先把真实项目链路说清楚

这个案例直接对齐你简历里的第一个项目 `ScheduleCenter 定时调度中心`，对应真实代码是 `bitstorm-svr-xtimer`。

核心链路不是抽象的，而是下面这条：

1. `MigratorWorker` 负责把未来时间窗任务迁移到 Redis `ZSet`
2. `TaskCache.GetTableName` 生成 `yyyy-MM-dd HH:mm_{bucket}` 这种分钟分片 key
3. `SchedulerWorker` 用 `@Scheduled(fixedRate = 1000)` 每秒调度一次
4. 每次会对 `5` 个 bucket 同时提交“上一分钟补偿 + 当前分钟实时”两轮分片
5. `SchedulerTask.asyncHandleSlice` 抢到分布式锁后不会立刻结束，而是直接进入 `TriggerWorker.work`
6. `TriggerWorker.work` 用 `Timer.scheduleAtFixedRate` 每秒驱动一次 `TriggerTimerTask`
7. `TriggerTimerTask` 每秒对 Redis `rangeByScore` 拉取到期任务，异常时走 `taskMapper.getTasksByTimeRange` 做 DB fallback
8. 到期任务再交给 `TriggerPoolTask` 异步投递到执行链路

## 2. 为什么 xtimer 更容易先表现成 Full GC

因为它的第一层矛盾不是“对象永远不释放”，而是“对象活得太久”。

把真实参数代进去：

- `scheduler.bucketsNum = 5`
- `SchedulerWorker.fixedRate = 1s`
- 每秒扫描分钟窗口数 = `2`，即“上一分钟补偿 + 当前分钟实时”
- 所以每秒提交分片数 = `5 * 2 = 10`
- `TriggerWorker.work` 会把一个 scheduler 线程挂住接近 `60s`
- 稳态并发需求 = `10 * 60 = 600`
- 真实 `schedulerPool.maxPoolSize = 100`
- 理论上会有约 `500` 个分片长期滞留在线程池队列里

而真实队列配置又是：

- `scheduler.pool.queueCapacity = 99999`
- `trigger.pool.queueCapacity = 99999`

这意味着系统不会很快暴露拒绝或背压，而是先把压力藏进堆里。

## 3. 这些对象为什么会晋升到老年代

分片排队和扫描期间，堆里会同时堆这些东西：

- 待执行的 `SchedulerTask` 分片上下文
- Redis `rangeByScore` 拉回来的任务 member 集合
- 反序列化后的 `TaskModel`
- `TriggerTimerTask` 每轮构造的列表对象
- 回调请求体、上下文对象、Future
- Redis 抖动时 DB fallback 查出来的结果集

它们不一定是典型泄漏，但在 JVM 里停留得足够久：

1. 一轮 Young GC 回收不掉
2. 又碰上下一轮调度继续提交
3. 对象多次存活后晋升到老年代
4. Old 区占用开始持续抬高
5. Full GC 越来越频繁

所以第一现场通常是：

- `Full GC` 次数变多
- 调度 RT 抖动
- 触发延迟上升
- 实例未必立刻挂，但明显开始卡

## 4. 我会怎么排查

### 第一步：先看线程池是不是已经失衡

重点看：

- `schedulerPool.activeCount`
- `schedulerPool.queueSize`
- `triggerPool.activeCount`
- `triggerPool.queueSize`

如果 `activeCount` 长时间顶满，`queueSize` 又持续上涨，说明不是瞬时尖峰，而是提交速率已经持续大于处理速率。

### 第二步：确认是不是 Redis 或 DB 把扫描拖长了

重点看：

- Redis `rangeByScore` RT
- `taskMapper.getTasksByTimeRange` RT
- 回调 HTTP RT

这一步的目的是判断：到底是模型先有问题，还是外部依赖变慢把问题放大了。

### 第三步：看 GC 现场

常见命令：

```bash
jstat -gcutil <pid> 1000 20
jcmd <pid> GC.heap_info
jmap -histo:live <pid>
```

典型信号：

- Young 区回收后仍有大量对象存活
- Old 区长期高位
- Full GC 频率明显高于正常值
- 对象直方图里出现大量集合、任务对象、Future 和回调上下文

### 第四步：回到 xtimer 代码收敛根因

真正的问题通常是这几件事叠在一起：

1. `SchedulerWorker` 每秒提交分片
2. `TriggerWorker.work` 近一分钟才结束
3. `queueCapacity=99999` 把压力藏起来
4. Redis 异常时 DB fallback 进一步放大结果集

## 5. 我会怎么处理

### 先止血

1. 缩小每轮扫描批次
2. 暂时降低同时参与扫描的分片并发
3. 控制 trigger 投递速率
4. 必要时扩容核心节点

### 长期治理

1. 把深队列改成有界队列
2. 抢锁前增加本机水位检查，不让饱和节点继续接 minuteBucketKey
3. 降低无效扫描和重复补偿
4. Redis 异常时严格限制 DB fallback 结果集
5. 把近一分钟的长扫描任务拆成更短生命周期的子任务

## 6. 面试里怎么说

> 我这个 ScheduleCenter 更先暴露出来的通常不是直接 OOM，而是 Full GC 频繁。因为真实 xtimer 里是每秒都在提交分钟分片任务，但抢到锁后的 TriggerWorker 会把这个分片持续扫描接近一分钟，导致 schedulerPool 很容易出现“外层提交快、内层任务长”的结构性积压。再叠加 Redis 查询、DB fallback、TaskModel 和回调上下文，这些对象会在堆里停留很久并晋升到老年代，先表现成 Full GC、RT 抖动和触发延迟。排查时我会先看 schedulerPool/triggerPool 队列，再看 Redis/DB RT 和 GC 指标，最后回到代码确认是长生命周期扫描、大队列和缺少背压共同造成的。 
