# order-observability-pattern-demo

一个专门讲 **订单 / 库存 / 支付建模 + 日志 / 指标 / Trace + 设计模式落地** 的教学项目。

这个项目的目标不是把电商系统做大，而是用一条最小 checkout 流程，回答三类高频面试题：

1. 订单、库存、支付的状态流转怎么设计
2. 一次下单失败或变慢时，日志、指标、trace 怎么帮助排查
3. 策略模式、模板方法、责任链在后端业务里到底怎么落地

---

## 这个项目讲什么

### 1. 订单建模 demo

对应代码：

- `order/OrderFulfillmentDemoService.java`

会直接演示：

- 下单校验
- 预占库存
- 按支付渠道策略执行支付
- 成功完成订单
- 支付失败后释放库存并补偿

### 2. 可观测性 demo

对应代码：

- `observability/ObservabilityDemoService.java`

会直接演示：

- 生成 traceId
- 结构化日志串起一次请求
- 记录成功数、补偿数等计数指标
- 记录一次 checkout 延迟指标

### 3. 设计模式 demo

对应代码：

- `pattern/DesignPatternDemoService.java`

会直接演示：

- 策略模式：按支付方式选择策略
- 责任链：按顺序执行订单校验器
- 模板方法：统一 checkout 主流程，子步骤由具体实现补充

---

## 这个项目怎么学

建议按这个顺序看：

1. `OrderFulfillmentDemoService`
2. `ObservabilityDemoService`
3. `DesignPatternDemoService`
4. `demo/DemoRunner.java`
5. 各模块测试

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. 钱包支付成功的订单流转
2. 支付失败后的库存补偿
3. 本次流程里用了哪些设计模式

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `OrderFulfillmentDemoTest`
- `ObservabilityDemoTest`
- `DesignPatternDemoTest`

---

## 面试里怎么说最稳

### 1. 订单状态流转最容易出什么问题？

> 最容易出的问题是“资源状态和订单状态不一致”，比如库存已经扣了但支付失败，或者支付成功了但订单没推进。所以建模时要把预占、确认、补偿这些状态拆清楚。

### 2. 为什么后端也要讲 trace？

> 因为一次请求往往会跨 controller、service、DB、MQ、三方接口。如果没有 traceId，把日志放在一起看几乎不可能快速定位问题。

### 3. 设计模式怎么说才不像背书？

> 最稳的说法不是背定义，而是直接结合代码：比如支付渠道切换我用策略模式，订单校验我用责任链，整个 checkout 主流程我用模板方法统一骨架。
