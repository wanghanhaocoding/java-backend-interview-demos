# 当前仓库的简历导向扩展分析

这份文档已经按当前仓库的真实落地结果更新，不再停留在“准备新增什么”的阶段。

判断标准仍然只有两个：

1. 是否真的贴你的简历主线
2. 是否能把简历里的真实项目拆成可运行、可讲述的 demo

对应材料主要来自：

- `C:\Users\wanghanhao\Desktop\面经解析\resume_extracted.txt`
- 当前仓库各 demo 的 README 和代码实现

---

## 一、先说当前结论

现在这个仓库已经形成了两层结构：

### 1. 通用技术基本盘

这些项目负责兜住 Java 后端常见基础题：

- `spring-core-demo`
- `spring-tx-demo`
- `mysql-lock-mvcc-demo`
- `redis-lock-demo`
- `mq-cache-idempotency-demo`
- `distributed-tx-demo`
- `jvm-stability-interview-demo`
- `resilience-auth-demo`

它们覆盖的是：

- Spring IoC / 事务 / 代理
- MySQL 事务 / MVCC / 锁 / 慢 SQL
- Redis 分布式锁 / 缓存一致性
- MQ 可靠性 / 幂等 / Outbox
- 分布式事务 / 状态机 / 补偿
- OOM / Full GC / 死锁
- 服务治理 / JWT / RBAC

### 2. 简历主线表达层

这一层才是你现在仓库真正的差异化价值，已经补成了更贴简历的独立 demo：

- `schedule-center-trigger-demo`
- `schedule-center-scaling-demo`
- `async-job-center-core-demo`
- `async-job-center-retry-sharding-demo`
- `treasury-receipt-state-machine-demo`
- `treasury-plan-collection-demo`
- `order-observability-pattern-demo`（已改成司库题材）

这 7 个项目直接对应你简历里最有辨识度的三条线：

- `ScheduleCenter`
- `AsyncJobCenter`
- `司库信息系统`

---

## 二、已经落地的简历主线 demo 分别解决什么问题

### 1. `schedule-center-trigger-demo`

作用：

- 讲清 `ScheduleCenter` 最核心的触发模型

当前覆盖：

- Redis ZSet 风格时间索引
- 分钟分片
- 滑动时间窗扫描

适合回答：

- 为什么不是直接扫数据库
- 时间索引为什么能降低扫描成本
- 分钟桶和 bucket 是怎么配合使用的

### 2. `schedule-center-scaling-demo`

作用：

- 讲清 `ScheduleCenter` 的扩展性和高并发治理

当前覆盖：

- 多机去重
- 预取到本地队列
- `schedulerPool` / `workerPool` 双线程池
- 背压

适合回答：

- 多节点一起扫怎么不重复
- 为什么双线程池更稳
- 同秒洪峰怎么削峰

### 3. `async-job-center-core-demo`

作用：

- 把 `AsyncJobCenter` 先讲成一个平台，而不是散点能力

当前覆盖：

- `server + worker`
- 三张核心表
- `create -> hold -> set -> 下一阶段 -> 终态`

适合回答：

- 为什么拆 server 和 worker
- `hold` 这一步在解决什么问题
- 任务生命周期是怎么推进的

### 4. `async-job-center-retry-sharding-demo`

作用：

- 讲 `AsyncJobCenter` 的稳定性治理和数据扩展

当前覆盖：

- 失败重试
- `order_time`
- 行锁到 Redis 锁演进
- 滚动分表 / 冷热拆分

适合回答：

- 为什么失败后不直接立刻重试
- 为什么 MySQL 行锁会放大冲突
- 为什么异步任务平台适合滚动分表

### 5. `treasury-receipt-state-machine-demo`

作用：

- 直接讲司库回执链路最有价值的一段

当前覆盖：

- 网银指令
- 回执延迟 / 重复 / 乱序
- 幂等键
- 状态机
- 终态保护
- 超时补偿 / 对账

适合回答：

- 回执链路为什么一定要有状态机
- 终态保护怎么防止状态回退
- 对账和补偿为什么是必需项

### 6. `treasury-plan-collection-demo`

作用：

- 把司库的计划、预算、归集调度链路讲成一条完整业务线

当前覆盖：

- 日计划生成
- 预算校验
- 固定窗口归集
- shard 分发
- 优先级与公平调度

适合回答：

- 为什么预算校验要前置
- 固定时间窗口怎么编排
- 优先级和公平性怎么兼顾

### 7. `order-observability-pattern-demo`

当前定位已经变化：

- 目录名还是原来的名字
- 但内容已经从电商 checkout 题材改成司库链路题材

当前覆盖：

- 司库指令 / 回执 / 归集链路建模
- traceId / 结构化日志 / 指标
- 策略模式
- 责任链
- 模板方法

它的价值不在于替代前面 6 个主线 demo，而在于补强这件事：

> 如何把“可观测性”和“设计模式”讲得既像真实项目，又不脱离你的简历背景。

---

## 三、当前仓库里，哪些项目还应该保留

### 第一梯队：必须保留

- `schedule-center-trigger-demo`
- `schedule-center-scaling-demo`
- `async-job-center-core-demo`
- `async-job-center-retry-sharding-demo`
- `treasury-receipt-state-machine-demo`
- `treasury-plan-collection-demo`
- `distributed-tx-demo`
- `redis-lock-demo`
- `mq-cache-idempotency-demo`
- `jvm-stability-interview-demo`

原因：

- 这些项目要么直接对应你的简历主线
- 要么直接支撑这些主线背后的关键技术解释

### 第二梯队：高价值补基本功

- `spring-core-demo`
- `spring-tx-demo`
- `mysql-lock-mvcc-demo`
- `resilience-auth-demo`

原因：

- 它们不是你简历最强的业务表达
- 但它们能补足基础原理题和通用工程题

### 第三梯队：用于补表达层

- `order-observability-pattern-demo`

原因：

- 这个项目现在已经适合保留
- 但它更偏“表达增强”和“架构叙述补充”，不是最核心主线

---

## 四、现在还值得继续补的，不是新业务主线，而是技术专题

主线 demo 这轮已经补得差不多了。接下来更值得继续做的是 4 类技术专题：

### 1. `multi-datasource-transaction-demo`

建议覆盖：

- 单数据源事务
- 多事务管理器
- 跨库写边界
- 为什么更推荐补偿 / 最终一致

### 2. `distributed-id-demo`

建议覆盖：

- UUID / 自增 / 号段 / 雪花算法
- 趋势递增 ID
- 插入局部性
- ID 与索引设计

### 3. `thread-pool-isolation-backpressure-demo`

建议覆盖：

- 双线程池
- 队列长度
- 拒绝策略
- 限流
- 背压

### 4. `rolling-sharding-demo`

建议覆盖：

- 滚动分表
- 冷热拆分
- 表路由
- 历史表查询

这 4 个专题更像“第二阶段增强项”，不应该再和主线 demo 抢优先级。

---

## 五、当前不建议优先重投入的方向

这些方向不是永远不做，而是当前阶段 ROI 明显不如继续深挖简历主线：

- `Netty / NIO / BIO`
- `Elasticsearch`
- AI 项目能力扩展

原因很简单：

- 简历里没有明确写
- 当前仓库已经不缺“泛化的面试题”
- 继续往这些方向扩，会削弱仓库围绕你简历主线形成的聚焦感

---

## 六、现在这套仓库的更合理定位

最合理的定位已经不再是：

> 我做了一堆 Java 面试 demo。

而应该是：

> 我把自己的后端项目主线拆成了可运行 demo，再用通用技术 demo 去补足底层解释、排查思路和面试表达。

这个定位比单纯罗列八股题更强，也更贴你当前简历。
