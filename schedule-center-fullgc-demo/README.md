# schedule-center-fullgc-demo

一个专门讲 `ScheduleCenter` 频繁 `Full GC` 的教学项目。

这个 demo 不把问题讲成抽象 JVM 八股，而是直接对齐你真实项目里的调度中心链路：

1. 任务先由迁移模块写入 Redis `ZSet`
2. 调度层按分钟和 `bucket` 分片
3. 多台节点每秒竞争分布式锁
4. 抢到锁的节点持续扫描这一分钟窗口
5. 到期任务再异步投递到执行模块

---

## 这个项目讲什么

对应代码：

- `fullgc/ScheduleCenterFullGcDemoService.java`

会直接演示：

- 为什么 `5` 个 bucket 再加“当前分钟 + 上一分钟补偿”会形成每秒 `10` 个分片扫描任务
- 为什么单个分片扫描如果持续接近 `60s`，就会把并发需求抬到 `600`
- 为什么 `schedulerPool.max=100`、`queueCapacity=99999` 这种配置更容易先打出 `Full GC`
- 排查时应该先看线程池堆积、Redis/DB RT、GC 指标，再回到代码链路

---

## 这个项目怎么学

建议按这个顺序看：

1. `ScheduleCenterFullGcDemoService`
2. `docs/full-gc-case.md`
3. `demo/DemoRunner.java`
4. `ScheduleCenterFullGcDemoTest`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. 故障是怎么出现的
2. 为什么先表现成 `Full GC`
3. 排查时看哪些指标
4. 怎么止血和怎么长期治理

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `ScheduleCenterFullGcDemoTest`

---

## 面试里怎么说最稳

> 我这个定时调度中心更容易先出现的不是直接 OOM，而是 Full GC 频繁。因为它的任务先迁移到 Redis ZSet，调度层按分钟和 bucket 分片，多台节点每秒都会去竞争当前分钟和上一分钟的分片锁。问题在于，抢到锁后不是很快结束，而是会持续扫描接近一分钟。如果线程池吞吐跟不上，分片扫描任务会在线程池和队列里积压，再叠加 Redis 查询结果、DB fallback 结果、任务对象和回调上下文，很多对象会跨过多轮 Minor GC 晋升到老年代，先表现成 Full GC 频繁、调度 RT 抖动和触发延迟升高。排查时我会先看线程池堆积、Redis/DB RT 和 GC 指标，再定位到是分片扫描生命周期过长、队列过大、缺少背压。处理上先限流、降批次、扩容止血，后面再改线程池和扫描模型。 
