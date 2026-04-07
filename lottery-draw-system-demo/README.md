# lottery-draw-system-demo

一个专门讲“高并发抽奖系统怎么设计、面试里怎么回答”的教学项目。

这个 demo 既给你一版能直接说出口的面试答案，也给你一套可运行的 Spring Boot 示例代码。

---

## 先说题目里的冲突

题目写的是：

- 抽奖次数达到 `20w` 次，或者 `200w` 次就停止
- 一等奖 `5w`
- 二等奖 `10w`
- 三等奖 `20w`
- 奖品必须抽完

如果总抽奖次数真的是 `20w`，那光这三档奖品就已经 `35w` 了，题目本身不成立。

所以面试里最稳的第一句话是：

> 这个题要先澄清约束。因为 20w 次抽奖不可能发完 35w 个奖品，所以我下面按 200w 总抽奖次数来设计；如果面试官坚持 20w，就必须同步缩减奖品库存。

---

## 面试里怎么回答最稳

### 1. 总体方案

我会采用：

- Redis 预生成奖池
- Lua 原子出队
- MQ 异步落库 / 发奖 / 通知
- MySQL 持久化抽奖记录

一句话解释就是：

> 把所有中奖结果和未中奖结果提前按库存生成成一个大奖池，随机打散后放到 Redis。每次用户抽奖时，只做资格校验、幂等校验和奖池原子弹出，结果立即返回；数据库落库和后续发奖动作异步走 MQ。

### 2. 为什么这样能满足题目要求

#### a. 抽奖次数达到 200w 就停止

奖池长度就是 `200w`，原子弹出到末尾后自动停止。

#### b. 奖品数量固定

一等奖 `5w`、二等奖 `10w`、三等奖 `20w` 都提前写死成库存 token，不会超发。

#### c. 奖品必须抽完

因为奖品 token 预先放进总奖池里，只要奖池被抽完，所有奖品一定会被抽完。

#### d. 用户必须在 2s 内获取到抽奖结果

主链路只保留：

1. 用户资格校验
2. 请求幂等校验
3. Redis Lua 原子出队
4. 结果返回

这些操作一般都是毫秒级，完全可以满足 `2s` SLA。真正慢的动作，比如写库、发券、发短信，都走 MQ 异步处理。

### 3. 生产版关键点

- 幂等：`requestId` 做幂等键，避免重复点击重复扣奖池
- 高并发：Redis 单线程 + Lua 原子脚本，避免并发超发
- 一致性：Redis 决定实时结果，MySQL 负责持久化，依赖 MQ 和补偿任务做最终一致
- 监控：QPS、RT、奖池剩余量、MQ 堆积、发奖失败率

---

## 这个 demo 实现了什么

为了保持 demo 简洁、可运行，这里没有真的接 Redis / MQ / MySQL，而是做了一个等价的本地版模型：

- 用 `byte[]` 预生成并打散奖池，等价于 Redis 奖池
- 用 `AtomicInteger` 原子推进游标，等价于 Lua 原子出队
- 用 `requestId -> result` 的并发 map 做幂等
- 用 REST API 暴露抽奖接口和库存统计接口

这样依然能证明最关键的四件事：

- 总抽奖次数严格受控
- 奖品不会超发
- 奖品会被抽完
- 单次抽奖是一个非常轻的原子操作

---

## 项目结构

- `lottery/LotteryCampaign.java`
  核心抽奖模型，负责奖池初始化、原子抽奖、库存统计、幂等
- `web/LotteryDrawController.java`
  提供 `/api/lottery/draw` 和 `/api/lottery/stats`
- `demo/DemoRunner.java`
  启动时打印一版“面试该怎么说”
- `LotteryCampaignTest`
  验证库存扣减、并发安全、幂等、2 秒响应约束
- `LotteryDrawControllerTest`
  验证接口幂等和统计输出

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后你会看到：

1. 题目约束冲突说明
2. 几次抽奖样例输出
3. 一段适合面试直接口述的总结

---

## 如何调用接口

### 1. 抽奖

```bash
curl -X POST http://localhost:8080/api/lottery/draw \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"u-1001\",\"requestId\":\"req-1001\"}"
```

### 2. 查看库存与统计

```bash
curl http://localhost:8080/api/lottery/stats
```

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `LotteryCampaignTest.shouldExhaustAllPrizesAndStopAfterConfiguredDrawCount`
- `LotteryCampaignTest.shouldStayThreadSafeUnderConcurrentDrawRequests`
- `LotteryDrawControllerTest.shouldReturnSameResultForDuplicateRequestIdAndExposeStats`

---

## 一段 3 分钟口述答案

> 这道题我会先澄清一个前提：如果总抽奖次数是 20 万，但奖品数量加起来有 35 万，那题目本身不成立，所以我会按 200 万次来设计。方案上我会用 Redis 预生成奖池，把一等奖、二等奖、三等奖以及未中奖结果都生成成 token，按库存数量随机打散后放进去。用户抽奖时，主链路只做资格校验、请求幂等和 Redis Lua 原子出队，然后立刻返回结果；写 MySQL 记录、发券、通知等操作走 MQ 异步处理。这样可以保证总抽奖次数不会超过 200 万，各奖品库存绝对准确，奖品会随着奖池抽空而全部发完，同时接口 RT 也能稳定控制在 2 秒内。这个 demo 则用内存里的 byte 数组和 AtomicInteger 把这个思路做成了一个可运行版本。
