# schedule-center-oom-demo

一个专门讲 `ScheduleCenter / xtimer` 积压型 `OOM` 的教学项目。

这版不是讲“写了个静态集合没释放”，而是直接对齐你简历第一个项目的真实链路：

- 简历项目：`ScheduleCenter 定时调度中心`
- 真实代码：`bitstorm-svr-xtimer`
- 真实问题：`每秒持续提交分片 + 近一分钟长扫描 + 99999 深队列 + Redis 异常走 DB fallback`

## 这个 demo 讲什么

对应代码：

- `oom/ScheduleCenterOomDemoService.java`

这版会直接演示：

1. 为什么 `SchedulerWorker` 每秒提交 10 个分片，本身就会把 schedulerPool 压满
2. 为什么 `TriggerWorker.work` 挂住近一分钟后，问题会先表现成 Full GC，再继续恶化成 OOM
3. 为什么 `triggerPool` 慢回调和 `taskMapper.getTasksByTimeRange` 的 fallback 放大，会把堆对象继续推高
4. 为什么这类问题更像“积压型 OOM”，而不是典型内存泄漏

## 为什么这版更像你的真实项目

它对齐了 xtimer 的真实配置和实现：

- `scheduler.bucketsNum = 5`
- `SchedulerWorker.fixedRate = 1s`
- `scheduler.pool.maxPoolSize = 100`
- `scheduler.pool.queueCapacity = 99999`
- `trigger.pool.maxPoolSize = 100`
- `trigger.pool.queueCapacity = 99999`
- `TriggerTimerTask` 每秒做一次 `rangeByScore`
- Redis 异常时走 `TaskMapper.getTasksByTimeRange`

也就是说，这版 demo 讲的不是抽象“调度系统”，而是你代码里真实存在的 `SchedulerWorker / SchedulerTask / TriggerWorker / TriggerTimerTask / TaskCache / TaskMapper` 这条链路。

## 这个项目怎么学

建议按这个顺序看：

1. `oom/ScheduleCenterOomDemoService.java`
2. `docs/oom-case.md`
3. `demo/DemoRunner.java`
4. `ScheduleCenterOomDemoTest`

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. xtimer 里的 OOM 是怎么一步步恶化出来的
2. schedulerPool 和 triggerPool 的关键压力口径
3. 排查时如何区分积压和泄漏
4. 先止血与长期治理怎么拆开说

## 如何运行测试

```bash
mvn test
```

重点看：

- `ScheduleCenterOomDemoTest`

## 面试里怎么说最稳

> 如果继续恶化，这类问题是可能进一步打到 OOM 的，但它更像积压型 OOM，不是典型代码泄漏。因为在 xtimer 里，SchedulerWorker 每秒都会提交分钟分片任务，而抢到锁后的 TriggerWorker.work 会持续扫描近一分钟，导致 schedulerPool 和 triggerPool 的大队列长期积压。再叠加 Redis 查询、DB 查询、任务对象构造和回调上下文，这些对象会在堆里不断堆积；如果 Redis 异常又触发 TaskMapper.getTasksByTimeRange 的 DB fallback，结果集还会被进一步放大。通常它不会一上来就 OOM，而是先出现 Full GC 越来越频繁、回收效果越来越差，最后老年代被打满。处理重点不是单纯加堆，而是收住任务生产速度、缩短单任务生命周期、限制队列长度，并把异常路径下的对象放大效应压住。 
