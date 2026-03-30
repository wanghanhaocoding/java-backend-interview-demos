# CPU 标高问题面试速查

## 一、如果面试官问：你线上遇到过 CPU 高吗？

你可以先这样总答：

> 遇到过，而且我一般不会直接说“流量大”或者“机器不够”。我会先看最热线程，再把线程栈和业务链路对应起来。结合我做过的 ScheduleCenter 和 AsyncJobCenter，我准备了两个比较稳的案例，一个是 scanner 空转把 CPU 打高，一个是失败后立刻重试导致 retry dispatcher 热点。

## 二、两个案例的定位关键词

### 1. ScheduleCenter

- `top -Hp`
- scanner 线程
- 空扫 bucket
- busy spin

### 2. AsyncJobCenter

- retry dispatcher
- `order_time`
- 指数退避
- 同 jobId 重复失败

## 三、一个万能收尾句

> 我看 CPU 高这类问题，会先区分“现象”和“根因”。现象是 CPU 高，根因可能是空转循环、无退避重试、锁竞争或者序列化热点。排查时我会先拿到热点线程和线程栈，再回到业务链路确认，最后把止血动作和长期治理动作拆开说。
