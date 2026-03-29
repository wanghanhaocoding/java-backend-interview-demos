# treasury-plan-collection-demo

一个专门讲司库日计划、预算校验和资金归集调度的教学项目。

这个 demo 直接贴你的业务主线，重点回答：

1. 日计划怎么从业务申请生成待执行任务
2. 预算校验怎么拦住超额度计划
3. 固定时间窗口怎么组织归集触发
4. 为什么要做分片并行和优先级公平调度

---

## 这个项目讲什么

对应代码：

- `plan/TreasuryPlanCollectionDemoService.java`

会直接演示：

- 生成多笔日计划申请
- 按主体预算做校验和拦截
- 把通过校验的计划按固定窗口归档
- 用 shard 分散归集任务
- 在同一窗口内按优先级优先、同优先级按 `order_time` 保证公平

---

## 这个项目怎么学

建议按这个顺序看：

1. `TreasuryPlanCollectionDemoService`
2. `demo/DemoRunner.java`
3. `TreasuryPlanCollectionDemoTest`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. 计划生成
2. 预算校验
3. 固定窗口编排
4. shard 并行分发
5. 执行顺序输出

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `TreasuryPlanCollectionDemoTest`

---

## 面试里怎么说最稳

### 1. 为什么预算校验不能放到最后做？

> 因为归集、指令下发、回执链路都比较长，预算如果在最后才拦，就会把大量无效任务送进调度中心和异步链路，增加系统噪音和补偿成本。

### 2. 关键任务优先和公平调度怎么兼顾？

> 常见做法不是只按优先级硬排，而是把优先级和 `order_time` 结合起来。高优先级任务先跑，但同优先级仍然按进入顺序或等待时长保证公平。
