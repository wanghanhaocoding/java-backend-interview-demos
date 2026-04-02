# OOM 案例：ScheduleCenter 任务触发失败风暴下，本地补偿快照设计不当导致堆内存打满

## 一、这个案例怎么和你的简历对上

这个案例最适合挂在你的：

- `ScheduleCenter 定时调度中心`
- 分钟分片 / 滑动时间窗 / 本地预取队列 / 失败补偿
- 高并发触发 / 多机调度 / 下游执行链路

一句话背景：

> 我们在 ScheduleCenter 里负责分钟级任务触发和下游执行分发。某次下游执行链路连续超时后，触发失败任务短时间暴涨。为了保证补偿可恢复，trigger 节点本地会暂存失败任务快照，但这个兜底快照设计成了无上限内存集合，结果高峰期持续堆积，最终把堆打满。

这里你最好主动补一句：

> 这类问题在真实项目里通常不会一上来就直接 OOM，而是会先表现成 Full GC 频繁、回收效果越来越差、调度 RT 抖动。只有在 schedulerPool / triggerPool 长时间积压、Redis 异常触发 DB fallback、失败快照又没有上限时，才会继续恶化成积压型 OOM。

## 二、出现过程

### 1. 业务现象

- 某天上午下游执行链路波动，`receipt_timeout_scan` 和 `budget_window_trigger` 两类任务失败率明显升高
- ScheduleCenter trigger 节点的 CPU 不是最高，但内存曲线持续上升
- 监控先告警：Old 区使用率从 60% 一路升到 90%+，Full GC 开始出现，任务预取 RT 抖动
- 之后实例开始重启，日志里出现：`java.lang.OutOfMemoryError: Java heap space`

### 2. 故障影响

- 单个 trigger 节点因为 OOM 被容器拉起重启
- 失败任务越积越多，补偿队列拉长
- 上游业务看到“待触发 / 处理中”状态长时间不结束，部分回单生成延迟

### 3. 更真实的项目过程是怎样的

这个问题不是“代码一上线立刻炸”，而是一个比较真实的演化过程，而且它前面通常会先经历一段 Full GC 抖动期：

1. `SchedulerWorker` 每秒都在继续提交 minuteBucket 分片任务
2. 抢到锁后的 `TriggerWorker` 会持续扫描接近一分钟，线程池开始先出现积压
3. Redis 或下游执行接口波动后，`TriggerTimerTask` 更频繁走 DB fallback，Full GC 开始增加
4. 我们原本以为 trigger 节点本地保留失败快照只是兜底，不会很多
5. 某次高峰期连续触发失败，失败任务开始批量进入本地缓冲
6. 本地缓冲里存的不是轻量引用，而是：
   - 任务 payload
   - 触发响应体
   - 错误堆栈摘要
   - 诊断 headers
7. 而且为了方便按任务查询，又同时维护了：
   - 顺序列表
   - 按 jobId 的 Map
   - 按业务类型分组的 Map
8. 这些对象都被强引用住，最后越来越多地晋升进老年代，最终把“先 Full GC，后 OOM”的链路跑完整

## 三、排查过程

### 第 1 步：先看监控，不要一上来猜是代码 bug 还是机器太小

看到的是：

- 堆使用率持续上升，且不是锯齿型正常波动，而是明显“越垒越高”
- Full GC 后 old 区只回落一点点
- 实例重启后短时间恢复，但同一波业务高峰里又重复上涨

这个时候我会先形成一个初步判断：

> 不是简单瞬时流量抖动，而是对象活得太久，先把老年代顶高；如果再继续堆，就会进一步打到 OOM。

### 第 2 步：看应用日志和失败特征，把内存上涨和业务现象挂起来

发现异常时间段内：

- 下游执行超时日志明显增多
- 同一批 bucket 里的任务反复进入 fallback 流程
- 日志里能看到类似 `ScheduleTriggerWorker fallback snapshot stored` 这种兜底日志在短时间内大量出现

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

- 大量 `ScheduleTaskSnapshot`、`byte[]`、`String` 被 `LeakyScheduleSnapshotBuffer` 持有
- 引用链不是只有一个 List，而是：
  - `orderedSnapshots`
  - `latestByJobId`
  - `snapshotsByBizType`
- 每个快照里都带着：
  - 原始任务 payload
  - 触发响应体
  - 错误堆栈摘要
  - traceId / tenantId / bucket 等诊断信息

这就很像真实项目里的问题：

> 某个“为了排障和补偿方便”设计的数据结构，在极端流量下反过来成了内存放大器。

最终根因链条是：

1. `SchedulerWorker` 外层每秒持续提交分钟分片
2. `TriggerWorker` 单次扫描生命周期接近一分钟，先形成线程池和队列积压
3. Redis / 下游波动时，DB fallback 和失败任务重试把对象继续放大
4. trigger 节点把失败任务完整快照放进本地兜底缓冲
5. 缓冲既没有上限，也没有 TTL，还维护了多份索引
6. 大对象被长期强引用，先表现为 Full GC 回收效果越来越差
7. 如果不止血，最终就会继续恶化成 OOM

## 四、根因

真正根因不是 JVM 参数太小，而是任务投递模型失控了：

- 长生命周期扫描任务 + 大队列，先把对象越积越多地推向老年代
- 为了“保险”又做了**无上限本地失败快照缓冲**
- 快照对象太重，既存 payload，又存 response 和错误信息
- 为了查找方便还维护了多份索引结构，进一步放大内存占用
- 高峰期失败率升高时，这个设计从“偶发兜底”变成了“持续堆积”

## 五、解决过程

### 1. 先止血

- 临时扩容实例
- 调小单机预取量
- 暂时降低失败任务补偿频率
- 摘流异常 bucket

这里你可以强调：

> 线上先做的是止血，不是等根因彻底改完。因为 OOM 已经影响处理链路了，第一优先级是先让实例别继续反复重启。

### 2. 再修根因

- 去掉无上限本地快照常驻方案
- 失败上下文改成轻量化落库，只保留必要索引字段
- 大 payload 不再进本地长期缓冲，只保留可回溯引用
- 给补偿链路加长度阈值、退避和熔断
- 本地结构只保留短周期窗口，不再维护多份冗余索引

## 六、沉淀过程

1. 补了 JVM 监控看板
2. 建了失败任务和异常 bucket 看板
3. 加了“本地缓存必须有上限和淘汰策略”的编码规范
4. 固化了 `jstat + jmap + MAT` 的排查 SOP
5. 对 trigger 节点的 fallback snapshot 量和 retained payload 体积补了指标

## 七、1 分钟面试回答版

> 如果继续恶化，这类问题是可能进一步打到 OOM 的，但它更像积压型 OOM，不是典型代码泄漏。因为在 xtimer 这条链路里，SchedulerWorker 外层每秒都在继续提交 minuteBucket 分片，抢到锁后的 TriggerWorker 又会持续扫接近一分钟，线程池和大队列先把对象越积越多地推向老年代；如果 Redis 异常又触发 DB fallback，下游回调也变慢，结果集、任务对象、Future 和回调上下文会继续放大。通常它不会一上来就 OOM，而是先出现 Full GC 越来越频繁、回收效果越来越差，最后堆被打满。处理重点不是单纯加堆，而是收住任务生产速度、缩短单任务生命周期、限制队列长度，并把异常路径下的对象放大效应压住。

## 八、你在项目代码里可以怎么给面试官看“这事像真的”

你现在项目里对应的代码入口在：

- `src/main/java/com/example/jvmstabilitydemo/oom/OomLeakDemo.java`
- `src/main/java/com/example/jvmstabilitydemo/oom/ScheduleCenterTaskStormSimulator.java`
- `src/main/java/com/example/jvmstabilitydemo/oom/LeakyScheduleSnapshotBuffer.java`
- `src/main/java/com/example/jvmstabilitydemo/oom/ScheduleTaskSnapshot.java`

这一版比之前更像真实项目，原因是：

1. 不是简单 `List<byte[]>`，而是失败任务触发快照
2. 快照里有业务字段、诊断 headers、payload、response、stack trace
3. 有本地缓冲、多索引结构、无上限设计这些典型工程问题
4. 日志输出也带了调度中心语境，比如 `ScheduleTriggerWorker`

如果面试官继续追问，你就可以说：

> 我后来还专门把这个问题抽成了一个 demo，里面我把真实出问题的点都保留了，比如失败任务快照、本地 fallback buffer、多份索引、payload 和 response 一起进内存，这样排查链路和真实项目就比较接近。
