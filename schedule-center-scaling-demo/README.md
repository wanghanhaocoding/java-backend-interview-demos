# schedule-center-scaling-demo

一个专门讲 `ScheduleCenter` 扩展性治理的教学项目。

这个 demo 对齐你简历里的高并发调度场景，重点回答这几类问题：

1. 多机一起扫 bucket 怎么避免重复触发
2. 为什么要拆 `schedulerPool` 和 `workerPool`
3. 预取到本地队列能解决什么问题
4. 同秒洪峰来时怎么做削峰和背压

---

## 这个项目讲什么

对应代码：

- `scaling/ScheduleCenterScalingDemoService.java`

会直接演示：

- 两个节点竞争同一个 bucket 锁
- 只有抢到锁的节点才能预取任务
- `schedulerPool` 只负责扫描和填充本地队列
- `workerPool` 负责真正执行业务任务
- 本地队列达到上限时，扫描侧暂停预取，等待消费侧回落

---

## 这个项目怎么学

建议按这个顺序看：

1. `ScheduleCenterScalingDemoService`
2. `demo/DemoRunner.java`
3. `ScheduleCenterScalingDemoTest`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. 节点竞争 bucket 锁
2. 预取到本地队列
3. 背压触发
4. `workerPool` 完成执行

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `ScheduleCenterScalingDemoTest`

---

## 面试里怎么说最稳

### 1. 为什么要双线程池？

> 扫描线程池和执行线程池职责不同。前者保证扫描准时，后者承接业务耗时。如果混在一起，业务阻塞会反向拖慢调度精度。

### 2. 多机扫描最核心的控制点是什么？

> 关键不是“每台机器都能扫”，而是“同一个 bucket 在同一时刻只能有一个节点生效”。通常要配合分布式锁或租约机制做去重。
