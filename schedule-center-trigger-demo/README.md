# schedule-center-trigger-demo

一个专门讲 `ScheduleCenter` 触发层核心链路的教学项目。

这个 demo 不去还原完整生产服务，而是把你简历里最容易被追问的 4 个点拆成最小可运行案例：

1. 任务注册后怎么落到按分钟分片的时间桶
2. Redis ZSet 风格时间索引怎么表达
3. 滑动时间窗怎么逐段扫描到期任务
4. 为什么这种设计能避免全表扫描

---

## 这个项目讲什么

对应代码：

- `trigger/ScheduleCenterTriggerDemoService.java`

会直接演示：

- 用 `yyyy-MM-dd HH:mm_{bucket}` 生成分钟分片 key
- 按任务 ID 取模，把任务打散到不同 bucket
- 把触发时间当作 score，模拟写入 ZSet
- 用 5 秒滑动窗口逐段扫描并触发任务

---

## 这个项目怎么学

建议按这个顺序看：

1. `ScheduleCenterTriggerDemoService`
2. `demo/DemoRunner.java`
3. `ScheduleCenterTriggerDemoTest`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. 任务注册到分钟分片
2. 任务写入时间索引
3. 滑动时间窗扫描并触发任务

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `ScheduleCenterTriggerDemoTest`

---

## 面试里怎么说最稳

### 1. 为什么不是直接扫数据库？

> 因为高频定时场景下，直接扫数据库会把 IO 和排序压力集中到热时段。更常见的做法是先把近期触发任务预热到 Redis 时间索引里，再按时间窗增量扫描。

### 2. 为什么要按分钟再分 bucket？

> 同一分钟里可能有很多任务，按分钟分桶可以缩小单次扫描范围；再按 bucket 打散，能让多机并行时更容易做水平扩展和去重。
