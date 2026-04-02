# xtimer CPU 标高问题面试速查

## 一、如果面试官问：你线上遇到过 CPU 高吗？

你可以先这样总答：

> 遇到过，而且我不会直接说“流量大”或者“机器不够”。我会先看最热线程，再把线程栈和 xtimer 的真实调度链路对应起来。结合我做过的 ScheduleCenter，我准备了两个更稳的案例，一个是 TriggerWorker 对空 minuteBucketKey 持续扫描把 CPU 打高，一个是 Redis 抖动后 TriggerTimerTask 持续走 taskMapper.getTasksByTimeRange 做 DB fallback，把 CPU 和 DB 一起放大。 

## 二、两个案例的定位关键词

### 1. xtimer 空扫热点

- `top -Hp`
- `TriggerWorker`
- `TriggerTimerTask`
- empty scan

### 2. xtimer fallback 风暴

- `taskMapper.getTasksByTimeRange`
- `minuteBucketKey`
- Redis miss
- callback 上下文重建

## 三、一个万能收尾句

> 我看 CPU 高这类问题，会先区分“现象”和“根因”。现象是 CPU 高，根因可能是空 minuteBucketKey 扫描、DB fallback 查询风暴、线程池模型失衡或者过度构造回调上下文。排查时我会先拿到热点线程和 Java 栈，再回到 xtimer 的业务链路确认，最后把止血动作和长期治理动作拆开说。 
