# treasury-receipt-state-machine-demo

一个专门讲司库回执状态机的教学项目。

这个 demo 直接对应你简历里的网银指令、回执入库和对账场景，重点回答：

1. 回执重复、延迟、乱序时怎么防止状态漂移
2. 为什么只靠“先查库再更新”不够
3. 终态保护和版本控制分别解决什么问题
4. 超时补偿和对账扫描怎么接入主链路

---

## 这个项目讲什么

对应代码：

- `receipt/TreasuryReceiptStateMachineDemoService.java`

会直接演示：

- 指令发起后进入 `SENT`
- 成功回执推进到 `SUCCESS`
- 重复回执被幂等键拦截
- 迟到的处理中回执被终态保护拒绝
- 超时扫描把异常指令推入 `RECONCILING`
- 对账结果再把状态推进到终态

---

## 这个项目怎么学

建议按这个顺序看：

1. `TreasuryReceiptStateMachineDemoService`
2. `demo/DemoRunner.java`
3. `TreasuryReceiptStateMachineDemoTest`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. 正常成功回执
2. 重复回执拦截
3. 迟到回执终态保护
4. 超时扫描
5. 对账修正终态

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `TreasuryReceiptStateMachineDemoTest`

---

## 面试里怎么说最稳

### 1. 为什么回执处理一定要有状态机？

> 因为银行回执不是按你期望的顺序到达的，可能先成功、后处理中，也可能重复回调。如果没有状态机约束，迟到回执很容易把终态冲回去。

### 2. 超时补偿和对账为什么不能少？

> 因为外部银行链路天然不受本地事务控制，不能指望每笔指令都按时回执。超时扫描保证异常单能被捞出来，对账保证最终一致。
