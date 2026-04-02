# schedule-center-fullgc-demo

一个专门讲 `ScheduleCenter / xtimer` 频繁 `Full GC` 的教学项目。

这次不是泛泛讲“调度系统容易 Full GC”，而是直接对齐你简历第一个项目和真实代码：

- 简历项目：`ScheduleCenter 定时调度中心`
- 真实代码：`bitstorm-svr-xtimer`
- 真实链路：`MigratorWorker -> SchedulerWorker -> SchedulerTask -> TriggerWorker -> TriggerTimerTask -> TriggerPoolTask`

## 这个 demo 讲什么

对应代码：

- `fullgc/ScheduleCenterFullGcDemoService.java`

这版会直接把真实项目里的关键事实摊开：

1. `SchedulerWorker` 使用 `@Scheduled(fixedRate = 1000)` 每秒调度一次
2. 每秒会同时提交 `5 个 bucket * 2 个分钟窗口 = 10` 个分片任务
3. `SchedulerTask.asyncHandleSlice` 抢到锁后，会同步进入 `TriggerWorker.work`
4. `TriggerWorker.work` 用 `Timer + CountDownLatch` 扫完整个分钟窗口，线程会挂住接近 `60s`
5. `schedulerPool.maxPoolSize=100`、`queueCapacity=99999`
6. `TriggerTimerTask` 每秒做一次 Redis `rangeByScore`，异常时走 `taskMapper.getTasksByTimeRange` 做 DB fallback

## 为什么这更像你的真实项目

因为你简历里的说法和 xtimer 代码是对得上的：

- `Redis 分片 + 分布式锁`
- `MySQL + Redis 二级存储`
- `按触发时间分片`
- `滑动时间窗增量扫描`
- `提前预取与缓存即将触发任务`
- `线程池、慢 SQL、缓存命中、稳定性治理`

这版 demo 不再只说抽象的“预取过大”，而是把 `fixedRate=1s`、`5 bucket`、`上一分钟补偿`、`queueCapacity=99999` 和 `TriggerWorker` 挂住近一分钟这些真实细节都写进去。

## 这个项目怎么学

建议按这个顺序看：

1. `fullgc/ScheduleCenterFullGcDemoService.java`
2. `docs/full-gc-case.md`
3. `docs/linux-server-full-gc-lab.md`
4. `demo/DemoRunner.java`
5. `ScheduleCenterFullGcDemoTest`

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. xtimer 真实链路里 Full GC 是怎么出现的
2. 关键配置和压力口径
3. 排查时应该看哪些命令和指标
4. 先止血和长期治理怎么拆开说

## Linux 服务器手工 runbook

- `docs/linux-server-full-gc-lab.md`

## 如何运行测试

```bash
mvn test
```

重点看：

- `ScheduleCenterFullGcDemoTest`

## 面试里怎么说最稳

> 我这个定时调度中心里，实际更先暴露出来的通常不是直接 OOM，而是 Full GC 频繁。因为在真实 xtimer 里，SchedulerWorker 每秒都会把当前分钟和上一分钟补偿的 5 个 bucket 全部提交给 schedulerPool，但抢到锁后的 TriggerWorker.work 会持续扫完整个分钟窗口，单个分片生命周期接近一分钟。如果线程池吞吐跟不上，分片任务就会在线程池和超大队列里积压，再叠加 Redis 查询、DB fallback、任务对象构造和回调上下文，这些对象很容易晋升到老年代，先表现成 Full GC 频繁、调度 RT 抖动和任务触发延迟升高。排查时我一般先看 GC 指标、线程池队列和 Redis/DB RT，再确认是长生命周期扫描任务、大队列和缺少背压共同导致的。处理上先缩批次、限并发、扩容止血，长期再把队列改成有界、控制 fallback 结果集并增强背压和监控。 
