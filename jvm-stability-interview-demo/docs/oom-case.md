# OOM 案例：AsyncJobCenter 银行回调失败风暴下，本地兜底快照设计不当导致堆内存打满

## 一、这个案例怎么和你的简历对上

这个案例最适合挂在你的：

- `AsyncJobCenter 异步任务中心`
- 任务创建 / 投递 / 调度执行 / 重试补偿
- 高并发、失败重试、断点续做

一句话背景：

> 我们在 AsyncJobCenter 里承接票据回单制作、预算分析这类异步回调任务。某次银行侧回调接口波动后，失败任务短时间暴涨。为了保证补偿可恢复，worker 本地会暂存失败任务快照，但这个兜底快照设计成了无上限内存集合，结果高峰期持续堆积，最终把堆打满。

## 二、出现过程

### 1. 业务现象

- 某天上午银行回调链路波动，`receipt_make` 和 `budget_analysis` 两类任务失败率明显升高
- AsyncJobCenter worker 进程的 CPU 不是最高，但内存曲线持续上升
- 监控先告警：Old 区使用率从 60% 一路升到 90%+，Full GC 开始出现，任务拉取 RT 抖动
- 之后实例开始重启，日志里出现：`java.lang.OutOfMemoryError: Java heap space`

### 2. 故障影响

- 单个 worker 因为 OOM 被容器拉起重启
- 失败任务越积越多，重试队列拉长
- 上游业务看到“处理中”状态长时间不结束，部分回单生成延迟

### 3. 更真实的项目过程是怎样的

这个问题不是“代码一上线立刻炸”，而是一个比较真实的演化过程：

1. 上游银行接口偶发超时，失败任务略有上升
2. 我们原本认为 worker 本地保留失败快照只是兜底，不会很多
3. 某次高峰期回调失败连续出现，失败任务开始批量进入本地缓冲
4. 本地缓冲里存的不是轻量引用，而是：
   - 任务 payload
   - 回调 response
   - 错误堆栈摘要
   - 诊断 headers
5. 而且为了方便按任务查询，又同时维护了：
   - 顺序列表
   - 按 jobId 的 Map
   - 按业务类型分组的 Map
6. 这些对象都被强引用住，最后越来越多地晋升进老年代

## 三、排查过程

### 第 1 步：先看监控，不要一上来猜是代码 bug 还是机器太小

看到的是：

- 堆使用率持续上升，且不是锯齿型正常波动，而是明显“越垒越高”
- Full GC 后 old 区只回落一点点
- 实例重启后短时间恢复，但同一波业务高峰里又重复上涨

这个时候我会先形成一个初步判断：

> 不是简单瞬时流量抖动，更像有对象被长期持有，或者某个内存缓冲没有释放。

### 第 2 步：看应用日志和失败特征，把内存上涨和业务现象挂起来

发现异常时间段内：

- 银行回调超时日志明显增多
- 同一批任务反复进入 retry 流程
- 日志里能看到类似 `AsyncJobCallbackWorker fallback snapshot stored` 这种兜底日志在短时间内大量出现

这一步的作用是把问题范围从“JVM 有异常”缩到：

> 很可能是失败任务补偿链路把对象留在了内存里。

### 第 3 步：用 JVM 工具看现场，先确认是 GC 回收不掉，不是单次突刺

常见命令：

```bash
jps -l
jstat -gcutil <pid> 1000 10
jmap -dump:live,format=b,file=heap.hprof <pid>
```

面试里你可以这样讲：

> 当时我先用 `jstat` 连续看了几轮 GC 指标，确认 old 区占用一直高，Full GC 后回落不明显；然后导了 live heap dump，准备看哪些对象被强引用住了。

### 第 4 步：用 MAT 看谁把对象留住了

分析 heap dump 后发现：

- 大量 `JobCallbackSnapshot`、`byte[]`、`String` 被 `LeakyLocalRetrySnapshotBuffer` 持有
- 引用链不是只有一个 List，而是：
  - `orderedSnapshots`
  - `latestByJobId`
  - `snapshotsByBizType`
- 每个快照里都带着：
  - 原始任务 payload
  - 回调响应体
  - 错误堆栈摘要
  - traceId / tenantId / bankCode 等诊断信息

这就很像真实项目里的问题：

> 某个“为了排障和补偿方便”设计的数据结构，在极端流量下反过来成了内存放大器。

最终根因链条是：

1. 上游波动导致失败任务增多
2. worker 把失败任务完整快照放进本地兜底缓冲
3. 缓冲既没有上限，也没有 TTL，还维护了多份索引
4. 大对象在堆里被长期强引用
5. Minor GC 回收不掉，逐步晋升老年代
6. Full GC 后仍降不下来，最终 OOM

## 四、根因

真正根因不是 JVM 参数太小，而是：

- 为了“保险”做了**无上限本地失败快照缓冲**
- 快照对象太重，既存 payload，又存 response 和错误信息
- 为了查找方便又维护了多份索引结构，进一步放大了内存占用
- 高峰期失败率升高时，这个设计从“偶发兜底”变成了“持续堆积”

## 五、解决过程

### 1. 先止血

- 临时扩容实例
- 调小单机并发拉取量
- 暂时降低失败任务重试频率
- 摘流异常任务类型

这里你可以强调：

> 线上先做的是止血，不是等根因彻底改完。因为 OOM 已经影响处理链路了，第一优先级是先让实例别继续反复重启。

### 2. 再修根因

- 去掉无上限本地快照常驻方案
- 失败上下文改成轻量化落库，只保留必要索引字段
- 大 payload 不再进本地长期缓冲，只保留可回溯引用
- 给重试链路加长度阈值、退避和熔断
- 本地结构只保留短周期窗口，不再维护多份冗余索引

## 六、沉淀过程

1. 补了 JVM 监控看板
2. 建了失败任务看板
3. 加了“本地缓存必须有上限和淘汰策略”的编码规范
4. 固化了 `jstat + jmap + MAT` 的排查 SOP
5. 对 worker 的 fallback snapshot 量和 retained payload 体积补了指标

## 七、1 分钟面试回答版

> 我在 AsyncJobCenter 里遇到过一次比较真实的 OOM，场景是银行回调波动导致失败重试风暴。我们当时为了保证补偿可恢复，在 worker 本地加了一个失败任务快照缓冲，里面会保留任务 payload、回调 response、错误堆栈和一些诊断信息。平时量小没问题，但那次高峰期失败任务持续堆积，而且这个缓冲没有上限、没有淘汰，还维护了按 jobId 和业务类型的多份索引，结果堆内存一直涨。排查时我先从监控上看到 old 区持续升高、Full GC 后回落不明显，再结合日志发现失败任务和 fallback snapshot 日志一起飙升。后面用 `jstat` 看 GC 趋势、用 `jmap` 导 live heap dump，在 MAT 里定位到大量 `JobCallbackSnapshot` 被本地缓冲长期持有。解决上我们先扩容和限流止血，再把失败上下文改成轻量落库，去掉无上限本地缓存，给重试链路加上限和退避策略，最后还补了 JVM 指标和 fallback buffer 指标的监控。

## 八、你在项目代码里可以怎么给面试官看“这事像真的”

你现在项目里对应的代码入口在：

- `src/main/java/com/example/jvmstabilitydemo/oom/OomLeakDemo.java`
- `src/main/java/com/example/jvmstabilitydemo/oom/AsyncJobFailureStormSimulator.java`
- `src/main/java/com/example/jvmstabilitydemo/oom/LeakyLocalRetrySnapshotBuffer.java`
- `src/main/java/com/example/jvmstabilitydemo/oom/JobCallbackSnapshot.java`

这一版比之前更像真实项目，原因是：

1. 不是简单 `List<byte[]>`，而是失败任务回调快照
2. 快照里有业务字段、诊断 headers、payload、response、stack trace
3. 有本地缓冲、多索引结构、无上限设计这些典型工程问题
4. 日志输出也带了 worker 语境，比如 `AsyncJobCallbackWorker`

如果面试官继续追问，你就可以说：

> 我后来还专门把这个问题抽成了一个 demo，里面我把真实出问题的点都保留了，比如失败任务快照、本地 fallback buffer、多份索引、payload 和 response 一起进内存，这样排查链路和真实项目就比较接近。
