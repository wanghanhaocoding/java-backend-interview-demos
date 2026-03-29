# order-observability-pattern-demo

一个专门讲 **司库回执 / 资金归集 / 日计划链路 + 日志 / 指标 / Trace + 设计模式落地** 的教学项目。

这个项目的目标不是把司库系统做大，而是用一条最小化的“预算校验 -> 指令下发 -> 失败补偿”链路，回答三类高频面试题：

1. 司库指令、回执、额度补偿的状态流转怎么设计
2. 一次归集下发失败或变慢时，日志、指标、trace 怎么帮助排查
3. 策略模式、模板方法、责任链在后端业务里到底怎么落地

---

## 这个项目讲什么

### 1. 司库链路建模 demo

对应代码：

- `treasury/TreasuryFlowDemoService.java`

会直接演示：

- 预算与额度校验
- 预占归集额度
- 按银行接入渠道策略执行下发
- 成功进入已下发状态
- 渠道失败后释放额度并补偿

### 2. 可观测性 demo

对应代码：

- `observability/ObservabilityDemoService.java`

会直接演示：

- 生成 traceId
- 结构化日志串起一次请求
- 记录成功数、补偿数等计数指标
- 记录一次 treasury flow 延迟指标

### 3. 设计模式 demo

对应代码：

- `pattern/DesignPatternDemoService.java`

会直接演示：

- 策略模式：按银行接入方式选择下发策略
- 责任链：按顺序执行额度和预算校验器
- 模板方法：统一司库指令主流程，子步骤由具体实现补充

---

## 这个项目怎么学

建议按这个顺序看：

1. `TreasuryFlowDemoService`
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

1. 银行直连成功的指令流转
2. 渠道失败后的额度补偿
3. 本次流程里用了哪些设计模式

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `TreasuryFlowDemoTest`
- `ObservabilityDemoTest`
- `DesignPatternDemoTest`

---

## 面试里怎么说最稳

### 1. 司库指令状态流转最容易出什么问题？

> 最容易出的问题是“额度状态和指令状态不一致”，比如额度已经预占了但银行下发失败，或者指令已成功下发但补偿又误释放了额度。所以建模时要把预占、确认、补偿这些状态拆清楚。

### 2. 为什么后端也要讲 trace？

> 因为一次请求往往会跨 controller、service、DB、MQ、三方接口。如果没有 traceId，把日志放在一起看几乎不可能快速定位问题。

### 3. 设计模式怎么说才不像背书？

> 最稳的说法不是背定义，而是直接结合代码：比如银行渠道切换我用策略模式，预算和额度校验我用责任链，整个司库指令主流程我用模板方法统一骨架。
