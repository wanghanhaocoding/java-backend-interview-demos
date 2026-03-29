# Java Backend Interview Demo Roadmap

这个目录现在已经整理成一套可拆分的 Java 后端面试 demo 集合。

你可以按“专题项目”来准备，也可以按“面试模块”来准备。

---

## 一、现有项目

### 1. `spring-tx-demo`

覆盖模块：

- Spring 事务
- 回滚规则
- 7 种传播机制
- 代理边界
- self-invocation
- BeanDefinition 基础链路

### 2. `distributed-tx-demo`

覆盖模块：

- 本地事务
- 状态机 + 幂等 + 补偿
- Outbox
- TCC
- Saga
- 最终一致性

### 3. `redis-lock-demo`

覆盖模块：

- Redis 分布式锁
- Redisson
- 并发计数
- 本地缓存并发
- 线程池
- 慢查询实验室入口

### 4. `jvm-stability-interview-demo`

覆盖模块：

- OOM
- Full GC
- 死锁
- JVM 现场排查与面试表达

---

## 二、新增项目

### 5. `mq-cache-idempotency-demo`

新增覆盖：

- MQ 消息可靠性
- 消息重复消费
- Outbox 重试
- 缓存一致性
- 延时双删
- 缓存击穿互斥保护
- 接口幂等
- 重复回调终态保护

### 6. `mysql-lock-mvcc-demo`

新增覆盖：

- `READ COMMITTED`
- `REPEATABLE READ`
- MVCC
- read view
- gap lock
- 幻读
- InnoDB 死锁

### 7. `spring-core-demo`

新增覆盖：

- IoC
- Bean 生命周期
- AOP 代理
- BeanDefinition 注册
- 循环依赖

### 8. `resilience-auth-demo`

新增覆盖：

- 限流
- 熔断
- 降级
- bulkhead / 隔离
- session
- JWT
- token 黑名单
- RBAC

### 9. `order-observability-pattern-demo`

新增覆盖：

- 订单 / 库存 / 支付建模
- 状态流转
- 补偿
- traceId
- 结构化日志
- 指标埋点
- 策略模式
- 责任链
- 模板方法

---

## 三、按面试模块反查项目

### Spring

- `spring-tx-demo`
- `spring-core-demo`

### MySQL

- `redis-lock-demo/mysql-slow-query-lab`
- `mysql-lock-mvcc-demo`

### Redis / 缓存

- `redis-lock-demo`
- `mq-cache-idempotency-demo`

### 分布式事务 / 幂等 / 补偿

- `distributed-tx-demo`
- `mq-cache-idempotency-demo`
- `order-observability-pattern-demo`

### MQ

- `mq-cache-idempotency-demo`
- `distributed-tx-demo`

### JVM / 并发 / 线程池

- `jvm-stability-interview-demo`
- `redis-lock-demo`

### 服务治理

- `resilience-auth-demo`

### 认证授权

- `resilience-auth-demo`

### 业务建模 / 可观测性 / 设计模式

- `order-observability-pattern-demo`

---

## 四、如果你按“简历主线优先”来学

第一梯队：

1. `spring-tx-demo`
2. `mysql-lock-mvcc-demo`
3. `mq-cache-idempotency-demo`
4. `distributed-tx-demo`
5. `redis-lock-demo`
6. `jvm-stability-interview-demo`

第二梯队：

1. `spring-core-demo`
2. `resilience-auth-demo`

第三梯队：

1. `order-observability-pattern-demo`

---

## 五、当前最值得继续补的方向

如果按你的简历和真实面试追问来补，优先应该是这些：

1. `schedule-center-deep-dive-demo`
   - Redis ZSet 扫描
   - 时间分片
   - 双线程池
   - 预取 + 本地队列
   - 多机去重
2. `async-job-center-deep-dive-demo`
   - server + worker
   - 三张核心表
   - `order_time`
   - 行锁到 Redis 锁演进
   - 滚动分表 / 冷热拆分
3. `treasury-receipt-state-machine-demo`
   - 网银指令
   - 回执延迟 / 重复 / 乱序
   - 幂等键 + 状态机 + 终态保护
   - 补偿 / 对账
4. `multi-datasource-transaction-demo`
   - 多数据源事务管理器
   - 跨库写入边界
   - 为什么尽量避免一个事务同时写多个库
5. `distributed-id-demo`
   - 雪花算法
   - 趋势有序 ID
   - 数据库插入局部性
6. `business-sql-case-demo`
   - 复杂业务 SQL 建模
   - 首笔 / 状态 / 逾期类筛选

---

## 六、当前不建议优先重投入的方向

这些方向不是永远不补，而是当前阶段 ROI 不高：

- `Netty / NIO / BIO`
- `Elasticsearch`
- AI 项目能力扩展

原因：

- 简历里没有明确写
- 当前真实短板不在这里
- 面试收益不如继续深挖 `ScheduleCenter / AsyncJobCenter / 司库信息系统`

详细判断见：`RESUME_ALIGNED_EXPANSION_ANALYSIS.md`
