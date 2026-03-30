# schedule-center-oom-demo

一个专门讲 `ScheduleCenter` 积压型 `OOM` 的教学项目。

这个 demo 是 `Full GC` 版本的恶化版，核心不是“对象永远不释放”，而是：

1. 调度层高频提交分片扫描任务
2. 单个扫描任务生命周期很长
3. `triggerPool` 下游回调又慢
4. 线程池队列过大，导致对象越积越多

---

## 这个项目讲什么

对应代码：

- `oom/ScheduleCenterOomDemoService.java`

会直接演示：

- 为什么这是积压型 `OOM`，不是典型内存泄漏
- `schedulerPool` 和 `triggerPool` 双重积压后，堆对象是怎么被放大的
- Redis 异常触发 DB fallback 时，为什么结果集会把堆压力进一步拉高
- 这类问题排查时为什么要同时看线程池、队列、GC 和下游 RT

---

## 这个项目怎么学

建议按这个顺序看：

1. `ScheduleCenterOomDemoService`
2. `docs/oom-case.md`
3. `demo/DemoRunner.java`
4. `ScheduleCenterOomDemoTest`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. OOM 是怎么一步步恶化出来的
2. 哪几层对象在堆里累积
3. 排查时怎么证明确实是积压而不是泄漏
4. 怎么止血和怎么改模型

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `ScheduleCenterOomDemoTest`

---

## 面试里怎么说最稳

> 如果这个调度中心继续恶化，后面是可能打到 OOM 的，但它更像积压型 OOM，不是典型内存泄漏。因为调度层每秒都在提交分钟分片扫描任务，单个分片任务又会持续接近一分钟；同时 triggerPool 还要负责执行回调，如果回调慢或者 Redis 失败触发 DB fallback，待执行任务、Future、查询结果、任务对象和回调上下文会一起堆在线程池和堆内存里。这个过程通常先表现成 Full GC 越来越频繁，后面如果积压持续扩大，老年代回不下来，就会进一步 OOM。处理上不能只靠调大堆，而是要把队列、并发、批次和 fallback 放大量控制住。 
