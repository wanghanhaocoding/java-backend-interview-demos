# async-job-center-retry-sharding-demo

一个专门讲 `AsyncJobCenter` 稳定性治理的教学项目。

这个 demo 对齐你简历里最强的一组技术细节，重点回答：

1. 失败重试为什么要回推 `order_time`
2. 为什么 MySQL 行锁方案在多 worker 下容易抖
3. Redis 分布式锁替代行锁的收益是什么
4. 为什么异步任务更适合做滚动分表和冷热拆分

---

## 这个项目讲什么

对应代码：

- `retry/AsyncJobCenterRetryShardingDemoService.java`

会直接演示：

- 按表容量阈值做滚动分表
- 热表写满后，新任务推进到下一张表
- 旧方案把竞争放在数据库行锁上，容易放大锁冲突
- 改造后把批次占有迁到 Redis 锁
- 任务失败后按指数退避回推 `order_time`
- 多次失败后最终成功，完整走完重试链路

---

## 这个项目怎么学

建议按这个顺序看：

1. `AsyncJobCenterRetryShardingDemoService`
2. `demo/DemoRunner.java`
3. `AsyncJobCenterRetryShardingDemoTest`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. 滚动分表路由
2. 行锁方案的问题
3. Redis 锁占有任务
4. 重试退避和最终成功

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `AsyncJobCenterRetryShardingDemoTest`

---

## 面试里怎么说最稳

### 1. 为什么重试不直接立刻再跑？

> 因为失败通常不是纯随机事件，可能是下游抖动、限流或者短时锁冲突。立刻重试只会放大雪崩，所以更稳的做法是回推 `order_time`，让任务在下一轮调度窗口再被捞起来。

### 2. 为什么异步任务平台适合滚动分表？

> 因为任务表会持续增大，而且冷热差异明显。用固定分片很容易在后期遇到热表膨胀；滚动分表更适合按表容量和时间推进，把热写和历史查询拆开。
