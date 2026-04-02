# java-backend-interview-demos

一个面向 Java 后端面试准备的教学型 Demo 合集。

这个仓库不是一个单体项目，而是一组可以拆开学习、单独运行的最小化示例。每个 demo 都尽量围绕一条清晰主线展开：先把核心概念讲明白，再落到可运行代码和测试，最后回到“面试里怎么说”。

## 第一次打开先看这里

如果你第一次看这个仓库，最先要知道的不是“从哪一题开始背”，而是**每个文件夹到底负责讲什么**。

可以先按你的目标选目录：

- 想按简历主线看：先看 `schedule-center-*`、`async-job-center-*`、`treasury-*`
- 想补 Java 后端基础题：先看 `spring-*`、`mysql-lock-mvcc-demo`、`redis-lock-demo`、`mq-cache-idempotency-demo`、`distributed-tx-demo`
- 想补稳定性和排障表达：先看 `schedule-center-fullgc-demo`、`schedule-center-callback-timeout-demo`、`jvm-stability-interview-demo`、`cpu-high-troubleshooting-demo`
- 想补服务治理和认证授权：看 `resilience-auth-demo`
- 想补“可观测性 + 设计模式 + 业务表达”：看 `order-observability-pattern-demo`

### 顶层目录一览

| 目录 | 是干什么的 | 适合什么时候看 |
| --- | --- | --- |
| `spring-core-demo` | Spring IoC、Bean 生命周期、AOP 代理、循环依赖 | 面试里被问 Spring 原理题时 |
| `spring-tx-demo` | Spring 事务、传播机制、回滚规则、代理边界 | 面试里被问 `@Transactional` 时 |
| `mysql-lock-mvcc-demo` | MySQL 隔离级别、MVCC、锁 | 补数据库基本盘时 |
| `redis-lock-demo` | Redis 分布式锁、Redisson、JVM 并发、线程池、慢 SQL 实验室 | 补锁、并发、线程池时 |
| `mq-cache-idempotency-demo` | MQ 可靠性、缓存一致性、接口幂等 | 补消息、缓存、重复请求时 |
| `distributed-tx-demo` | 本地事务、Outbox、状态机、补偿、TCC、Saga | 面试里被问分布式事务时 |
| `resilience-auth-demo` | 限流、熔断、降级、隔离、JWT、RBAC | 补服务治理和认证授权时 |
| `schedule-center-fullgc-demo` | 贴 `xtimer` 的 Full GC runbook、老年代压力、调度延迟与止血 | 补 ScheduleCenter Full GC 故障案例时 |
| `schedule-center-callback-timeout-demo` | 贴 `xtimer` 的 callback timeout、triggerPool backlog、schedulerPool 反压链路 | 补 ScheduleCenter 回调超时和线程池饱和案例时 |
| `jvm-stability-interview-demo` | 贴 `xtimer` 的 OOM、Full GC、死锁、故障排查表达 | 补 ScheduleCenter 稳定性案例时 |
| `cpu-high-troubleshooting-demo` | 贴 `xtimer` 的 CPU 标高排查、热点线程定位、止血和长期治理 | 补 ScheduleCenter CPU 故障排查时 |
| `schedule-center-trigger-demo` | ScheduleCenter 的时间索引、分钟分片、滑动时间窗扫描 | 讲调度中心“怎么触发任务”时 |
| `schedule-center-scaling-demo` | ScheduleCenter 的多机去重、预取、本地队列、双线程池、背压 | 讲调度中心“怎么扩容和抗压”时 |
| `async-job-center-core-demo` | AsyncJobCenter 的 `server + worker`、三张核心表、任务主链路 | 讲异步任务平台骨架时 |
| `async-job-center-retry-sharding-demo` | AsyncJobCenter 的失败重试、`order_time`、锁演进、滚动分表 | 讲异步任务平台稳定性治理时 |
| `treasury-receipt-state-machine-demo` | 司库回执幂等、状态机、终态保护、超时补偿、对账 | 讲回执处理和状态推进时 |
| `treasury-plan-collection-demo` | 司库日计划、预算校验、资金归集、固定窗口、优先级公平 | 讲司库计划与归集链路时 |
| `order-observability-pattern-demo` | 司库风格业务流、可观测性、设计模式表达 | 讲业务建模和工程表达时 |

### 根目录文档

| 文件 | 是干什么的 |
| --- | --- |
| `README.md` | 整个仓库的总导航，先看这个 |
| `JAVA_BACKEND_INTERVIEW_DEMO_ROADMAP.md` | 按主题整理的学习路线图 |
| `RESUME_ALIGNED_EXPANSION_ANALYSIS.md` | 解释这些 demo 为什么和你的简历主线匹配 |

## 如果你是按“我的简历”来准备

这套仓库现在更适合按下面这三条主线来学，而不是继续泛化扩到所有 Java 生态题库：

- `ScheduleCenter`：秒级调度、Redis ZSet、时间分片、双线程池、预取与本地队列、多机去重
- `AsyncJobCenter`：server + worker、三张核心表、重试补偿、冷热分表、`order_time`、MySQL 行锁到 Redis 锁演进
- `司库信息系统`：网银指令、回执处理、状态机、幂等、预算校验、资金归集、固定窗口热点

当前仓库里，和这三条主线最贴近的项目是：

- `schedule-center-trigger-demo`
- `schedule-center-scaling-demo`
- `schedule-center-fullgc-demo`
- `schedule-center-callback-timeout-demo`
- `async-job-center-core-demo`
- `async-job-center-retry-sharding-demo`
- `treasury-receipt-state-machine-demo`
- `treasury-plan-collection-demo`
- `order-observability-pattern-demo`
- `jvm-stability-interview-demo`
- `cpu-high-troubleshooting-demo`
- `redis-lock-demo`
- `mq-cache-idempotency-demo`
- `distributed-tx-demo`
- `mysql-lock-mvcc-demo`
- `spring-core-demo`
- `spring-tx-demo`

详细分析、补充优先级、后续建议新增 demo，见：

- [RESUME_ALIGNED_EXPANSION_ANALYSIS.md](./RESUME_ALIGNED_EXPANSION_ANALYSIS.md)

## 新增：简历主线 Demo

这一轮已经补齐 6 个更贴你简历主线的独立 demo：

- `schedule-center-trigger-demo`：ScheduleCenter 的时间索引、分钟分片、滑动时间窗扫描
- `schedule-center-scaling-demo`：ScheduleCenter 的多机去重、预取、本地队列、双线程池、背压
- `async-job-center-core-demo`：AsyncJobCenter 的 `server + worker`、三张核心表、`create/hold/set` 链路
- `async-job-center-retry-sharding-demo`：AsyncJobCenter 的失败重试、`order_time`、锁演进、滚动分表
- `treasury-receipt-state-machine-demo`：司库回执的幂等、状态机、终态保护、超时补偿、对账
- `treasury-plan-collection-demo`：司库日计划、预算校验、固定窗口触发、归集分片、优先级公平

## 这个仓库适合什么场景

- 想系统复习 Java 后端高频面试题
- 想把 Spring、MySQL、Redis、MQ、分布式事务这些知识点串起来
- 想把“概念答案”变成“有代码、有案例、有排查过程”的项目表达
- 想把 GitHub 仓库整理成一套能展示后端基础和工程理解的作品集

## 仓库包含哪些 Demo

### 1. [spring-core-demo](./spring-core-demo)

**主题**

- Spring IoC
- Bean 生命周期
- AOP 代理
- BeanDefinition 注册
- 循环依赖

**能回答的面试题**

- `@Service` 为什么能进 Spring 容器？
- Bean 是什么时候创建的？
- `@PostConstruct`、`afterPropertiesSet`、`@PreDestroy` 的顺序是什么？
- 为什么 AOP / `@Transactional` 依赖代理？
- 为什么有些循环依赖能解决，有些不能解决？

### 2. [spring-tx-demo](./spring-tx-demo)

**主题**

- Spring 事务基础行为
- 回滚规则
- 7 种事务传播机制
- 代理边界
- self-invocation 事务失效

**能回答的面试题**

- `@Transactional` 在什么情况下会回滚，什么情况下不会？
- checked exception 为什么默认不回滚？
- `rollbackFor` 是做什么的？
- `REQUIRED`、`REQUIRES_NEW`、`NESTED`、`NOT_SUPPORTED` 的区别是什么？
- 为什么同类内部调用会导致事务失效？

### 3. [mysql-lock-mvcc-demo](./mysql-lock-mvcc-demo)

**主题**

- MySQL 隔离级别
- MVCC
- 快照读与当前读
- gap lock / next-key lock
- InnoDB 死锁

**能回答的面试题**

- `READ COMMITTED` 和 `REPEATABLE READ` 的区别是什么？
- MVCC 是怎么实现“读不阻塞写”的？
- read view 是什么？
- gap lock 为什么会拦插入？
- MySQL 死锁一般是怎么形成的，怎么分析？

### 4. [redis-lock-demo](./redis-lock-demo)

**主题**

- Redis 原生分布式锁
- Redisson
- watchdog 自动续期
- JVM 并发计数
- 本地缓存并发初始化
- 线程池建模与执行流转
- MySQL 慢查询实验室

**能回答的面试题**

- Redis 分布式锁怎么实现？
- 为什么锁必须设置超时时间？
- 为什么解锁必须校验持有者？
- Redisson 和手写 Redis 锁的区别是什么？
- `count++` 为什么线程不安全？
- `AtomicInteger` 和 `LongAdder` 的适用场景怎么选？
- `putIfAbsent` 和 `computeIfAbsent` 有什么区别？
- 线程池里的任务是怎么从 core 到 queue 再到 max 的？
- 慢 SQL 怎么复现、定位和优化？

### 5. [resilience-auth-demo](./resilience-auth-demo)

**主题**

- 限流
- 熔断
- 降级
- bulkhead / 隔离
- session
- JWT
- RBAC

**能回答的面试题**

- 限流和熔断的区别是什么？
- 降级一般在什么场景下出现？
- bulkhead / 线程池隔离解决什么问题？
- JWT 为什么适合无状态认证？
- JWT 为什么还需要黑名单机制？
- RBAC 怎么做权限控制？

### 6. [mq-cache-idempotency-demo](./mq-cache-idempotency-demo)

**主题**

- MQ 消息可靠性
- Outbox 本地消息表
- 消费幂等
- 缓存一致性
- 延时双删
- 接口幂等

**能回答的面试题**

- 消息为什么会重复、丢失、积压？
- 为什么不能只靠消费者“不报错”来保证消息可靠？
- Outbox 模式解决的核心问题是什么？
- 缓存和数据库双写为什么容易不一致？
- 延时双删适合解决什么问题？
- 幂等为什么不能只靠前端防抖？
- 支付回调重复通知怎么防重复处理？

### 7. [distributed-tx-demo](./distributed-tx-demo)

**主题**

- 本地事务
- 最终一致性
- 柔性事务
- 状态机 + 幂等 + 补偿
- Outbox
- TCC
- Saga

**能回答的面试题**

- 分布式事务有哪些常见方案？
- 为什么 `@Transactional` 管不了外部系统？
- 为什么真实项目里很多场景不用 XA / 2PC？
- 状态机 + 幂等 + 补偿适合什么业务？
- Outbox 如何保证“业务成功后消息不丢”？
- TCC 和 Saga 的区别是什么？
- 资金类场景为什么更常讲最终一致性？

### 8. [order-observability-pattern-demo](./order-observability-pattern-demo)

**主题**

- 司库回执 / 归集 / 日计划链路建模
- 状态流转
- 可观测性
- 结构化日志
- 指标与 Trace
- 策略模式
- 责任链
- 模板方法

**能回答的面试题**

- 司库指令、回执、归集链路的状态流转怎么设计？
- 指令下发失败后额度或资源怎么补偿？
- traceId 在后端排障里为什么重要？
- 日志、指标、trace 分别解决什么问题？
- 策略模式、责任链、模板方法在业务代码里怎么落地？
- 设计模式怎么结合项目讲，而不是只背定义？

### 9. [jvm-stability-interview-demo](./jvm-stability-interview-demo)

**主题**

- OOM
- Full GC
- 死锁
- 故障排查路径
- 修复动作与复盘表达

**能回答的面试题**

- 线上 OOM 一般怎么排查？
- Full GC 频繁导致系统抖动时怎么看？
- 死锁一般怎么定位？
- 线上稳定性问题怎么组织成一段完整的面试回答？
- 故障复盘时，业务背景、现象、根因、修复、沉淀应该怎么讲？

## 推荐学习顺序

如果你想按“最贴近简历主线 + 最容易应对真实追问”的顺序看，建议这样走：

1. `schedule-center-trigger-demo`
2. `schedule-center-scaling-demo`
3. `schedule-center-fullgc-demo`
4. `schedule-center-callback-timeout-demo`
5. `async-job-center-core-demo`
6. `async-job-center-retry-sharding-demo`
7. `treasury-receipt-state-machine-demo`
8. `treasury-plan-collection-demo`
9. `order-observability-pattern-demo`
10. `spring-core-demo`
11. `spring-tx-demo`
12. `mysql-lock-mvcc-demo`
13. `redis-lock-demo`
14. `mq-cache-idempotency-demo`
15. `distributed-tx-demo`
16. `jvm-stability-interview-demo`
17. `cpu-high-troubleshooting-demo`
18. `resilience-auth-demo`

其中：

- 前 6 个是直接对应简历主线的新主干 demo
- `order-observability-pattern-demo` 现在已经改造成更贴近司库回执 / 归集 / 日计划场景的版本，适合补“可观测性 + 设计模式”表达
- `resilience-auth-demo` 更偏“补短板”，用于补 Spring 从零搭建、登录认证、权限控制

## 使用方式

- 每个 demo 基本都是独立 Maven 项目
- 先进入对应目录，再运行 `mvn test` 或 `mvn spring-boot:run`
- README 会说明该 demo 重点讲什么、先看哪些类、适合怎么表述成面试答案

如果你的目标是做 GitHub 展示，这个仓库最适合强调的是：

- 不是只罗列八股题，而是把知识点做成了可运行 Demo
- 每个主题都尽量对应到真实后端场景
- 不只讲概念，也讲代码、测试、排查和表达方式
