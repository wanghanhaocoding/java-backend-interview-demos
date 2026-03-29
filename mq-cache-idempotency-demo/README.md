# mq-cache-idempotency-demo

一个专门讲 **MQ 消息可靠性 / 缓存一致性 / 接口幂等** 的教学项目。

这个项目不追求接入真实 Kafka、Redis、网关，而是用一条最小化的订单链路，把后端面试里最常被连续追问的三个问题讲清楚：

1. 消息为什么会重复、丢失、积压
2. 缓存和数据库双写为什么容易不一致
3. 重复提交和重复回调到底怎么防

---

## 这个项目讲什么

### 1. MQ 消息可靠性 demo

对应代码：

- `mq/MessageReliabilityDemoService.java`

会直接演示：

- 业务数据和 outbox 事件一起落盘
- 第一次投递失败，事件进入待重试
- 后台重试后投递成功
- 消费端收到重复消息时用幂等表去重

### 2. 缓存一致性 demo

对应代码：

- `cache/CacheConsistencyDemoService.java`

会直接演示：

- 先更新数据库但忘删缓存，出现脏读
- 延时双删为什么能把脏缓存再清一遍
- 热点 key 失效时，为什么加互斥能减少击穿

### 3. 接口幂等 demo

对应代码：

- `idempotency/IdempotencyDemoService.java`

会直接演示：

- 幂等 token 防止表单重复提交
- 支付回调重复通知只处理一次
- 终态一旦写入，迟到的处理中回调不能把状态冲回去

---

## 这个项目怎么学

建议按这个顺序看：

1. `MessageReliabilityDemoService`
2. `CacheConsistencyDemoService`
3. `IdempotencyDemoService`
4. `demo/DemoRunner.java`
5. 各模块测试

---

## 如何运行

直接启动：

```bash
mvn spring-boot:run
```

启动后会按顺序打印这些案例：

1. Outbox 首次投递失败后重试成功
2. 消费端重复消息去重
3. 缓存脏读案例
4. 延时双删修复缓存
5. 热点 key 击穿的互斥保护
6. token 幂等防重复提交
7. 重复回调与终态保护

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `MessageReliabilityDemoTest`
- `CacheConsistencyDemoTest`
- `IdempotencyDemoTest`

---

## 面试里怎么说最稳

### 1. MQ 为什么不能只靠消费者不报错？

> 因为消息链路上存在生产成功但投递失败、消费成功但确认失败、网络重试导致重复投递这些情况，所以要把“至少一次投递”和“消费端幂等”配套考虑。

### 2. 缓存一致性为什么难？

> 因为数据库和缓存是两个系统，更新顺序一旦被并发打断，就会出现旧值回填。真实项目里一般会根据场景选删缓存、延时双删、订阅 binlog 或者异步修复。

### 3. 幂等为什么不能只靠前端防抖？

> 因为重复请求不只来自用户连点，还可能来自重试、超时重发和第三方回调。服务端必须有 token、业务唯一键或者状态机终态保护。
