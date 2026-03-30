# AsyncJobCenter CPU 案例：失败后立刻重试，少量任务反复空转打满 CPU

## 一、这个案例怎么和你的简历对上

这个案例最适合挂在你的：

- `AsyncJobCenter 异步任务中心`
- 银行回调
- 失败重试
- 断点续做

一句话背景：

> 我们在 AsyncJobCenter 里承接回单制作、预算分析等异步回调任务。某次银行接口抖动后，worker 对失败任务没有回推 `order_time`，而是立刻重试，导致少量 jobId 在 retry dispatcher 里不断空转，CPU、日志和下游调用一起被放大。

## 二、出现过程

- 整体流量没有明显暴涨
- 但 worker 实例 CPU 一直居高不下
- 日志里同一个 `jobId` 的失败和 retry 反复出现
- 下游银行接口 QPS 被异常放大

## 三、排查过程

### 第 1 步：先抓热点线程

```bash
top -Hp <pid>
```

经常能看到：

- retry dispatcher 线程很热
- callback worker 或序列化线程也可能跟着热

### 第 2 步：结合线程栈看是不是“重试空转”

```bash
printf '%x\n' <tid>
jstack <pid> | grep -A 20 <nid>
```

通常会看到线程在：

- `retryImmediately`
- `rebuildCallbackContext`
- `serializeRetryPayload`

这一类方法里反复打转。

### 第 3 步：把线程热点和业务证据对上

这一步不要只看线程栈，还要看：

- 同一 `jobId` 的重复失败次数
- 单位时间 retry 次数
- 下游超时率
- 线程池队列长度

## 四、根因

真正根因一般是：

- 失败后立刻重试，没有退避
- 重试调度线程和执行线程混在一起
- 同一任务上下文被不断重建和序列化
- 没有限制单任务的单位时间重试次数

## 五、解决过程

### 1. 先止血

- 对异常任务类型限流
- 降低单实例拉取量
- 暂时拉长重试间隔

### 2. 根因治理

- 失败后回推 `order_time`
- 用指数退避替代立刻重试
- 拆分 retry dispatcher 和 callback worker
- 对同一 jobId 的连续失败做告警和熔断

## 六、沉淀过程

1. 给 retry 次数、同 jobId 命中次数、order_time 延迟补指标
2. 把“失败后必须退避”写成平台规范
3. 压测补上失败风暴场景

## 七、1 分钟面试回答版

> 我在 AsyncJobCenter 里准备过一个比较典型的 CPU 高案例。场景是银行回调抖动后，失败任务如果没有回推 `order_time`，而是立刻重试，就会导致 retry dispatcher 线程持续空转，少量 jobId 几秒内被重复处理很多次。排查时我先用 `top -Hp` 找热点线程，再用 `jstack` 对应到 Java 栈，通常能落在 retry dispatcher 和上下文重建上；然后再结合日志看同一个 jobId 的重复失败证据。处理上先做限流和拉长重试间隔止血，后面把失败重试改成指数退避、拆分线程池，并对同一任务的异常重试次数补监控和告警。
