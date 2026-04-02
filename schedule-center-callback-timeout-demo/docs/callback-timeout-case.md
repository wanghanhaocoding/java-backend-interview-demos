# xtimer callback timeout 案例：slow callback 如何把 triggerPool 拖成调度级联延迟

## 1. 先把真实代码锚点立住

这个模块只讨论 `bitstorm-svr-xtimer` 里和 callback 直接相关的链路：

1. `SchedulerWorker @Scheduled(fixedRate = 1000)` 每秒继续提交 minute slice
2. `TriggerTimerTask.handleBatch` 拉到到期任务
3. `TriggerPoolTask.runExecutor` 使用 `@Async("triggerPool")` 投递 callback
4. `ExecutorWorker.work` 会查 `taskMapper.getTasksByTimerIdUnix`
5. `ExecutorWorker.executeTimerCallBack` 里同步发 HTTP callback
6. `AsyncPool.schedulerPoolExecutor` / `AsyncPool.triggerPoolExecutor` 都挂了 `CallerRunsPolicy`

这几个锚点串起来以后，callback timeout 的事故主线就很清楚了：慢的不是“抽象异步线程”，而是 xtimer 真正在执行业务回调的那批 trigger 线程。

## 2. 这条事故链路是怎么形成的

教学口径固定为：

- `SchedulerWorker.fixedRate = 1000ms`
- `5 个 bucket * 2 个分钟窗口 = 10` 个 minute slice/s
- 每个 slice 平均投递 `12` 个 callback task
- 业务 callback RT 观察值约 `3000ms`
- 教学 timeout budget 先按 `2000ms` 讲
- `triggerPool.maxPoolSize = 100`
- `triggerPool.queueCapacity = 99999`

于是压力很容易算出来：

- callback 到达速率约 = `10 * 12 = 120/s`
- `triggerPool` 理论处理能力约 = `100 / 3 = 33.33/s`
- callback 净积压约 = `(120 - 33.33) = 86.67/s`
- 每分钟新增 backlog 约 = `5200`
- 队列打满时间约 = `99999 / 86.67 ≈ 1153.8s ≈ 19.23 分钟`

也就是说，只要 `ExecutorWorker.executeTimerCallBack` 一直卡在慢 callback 上，哪怕没有 CPU 打满、没有 OOM，系统也已经处于不可持续状态。

## 3. 为什么会从 triggerPool 扩散到 schedulerPool

这一段是 callback timeout 模块最重要的边界，也是它区别于其他 demo 的地方。

### 阶段 1：triggerPool backlog 先被深队列隐藏

- `TriggerTimerTask.handleBatch` 还在持续投递
- `triggerPool.activeCount` 逐步打满到 `100`
- `triggerPool.queueSize` 开始单向增长

这时看起来像“只是线程池有点忙”，但根因已经出现了：`ExecutorWorker.executeTimerCallBack` 是同步 HTTP 调用，慢 RT 会直接吞掉 `triggerPool` 工作线程。

### 阶段 2：delay propagation 开始发生

因为 callback 到达速率仍是 `120/s`，而消费只有 `33.33/s`，所以 backlog 会按 `86.67/s` 累积。深队列只能延后暴露，不能消灭积压。

### 阶段 3：CallerRunsPolicy 把 callback 反压回调用线程

当 `triggerPool` 队列接近上限时，`AsyncPool.triggerPoolExecutor` 的 `CallerRunsPolicy` 会生效：

- 新 callback 不会被直接丢掉
- 调用 `TriggerPoolTask.runExecutor` 的线程会自己执行 callback

这就意味着，原本负责扫描 minute bucket 的线程，也要开始等待业务 callback 完成。

### 阶段 4：schedulerPool lag 被放大

一旦调用线程开始亲自执行 callback：

- `TriggerTimerTask.handleBatch` 完成本轮投递需要更久
- `SchedulerWorker` 下一次 fixedRate tick 会和上一轮扫描重叠
- `schedulerPool` 也因为 `CallerRunsPolicy` 缺少隔离而开始出现 lag

所以这个问题的核心不是“callback 失败了多少次”，而是**慢 callback 正在回传成调度延迟**。

## 4. 现场最该看的证据是什么

### 先看 callback timeout 是否已经稳定出现

如果业务侧已经看到 callback RT 大量超过 `2000ms`，那就说明 `ExecutorWorker.executeTimerCallBack` 很可能正在持续占住 trigger 线程。

### 再看 triggerPool 是否进入 backlog

优先关注：

- `triggerPool.activeCount`
- `triggerPool.queueSize`
- callback timeout 次数 / callback RT 分位数

如果 `queueSize` 只涨不回落，问题就不是瞬时波动，而是 callback 处理能力不足。

### 最后确认 lag 是否已经传播到调度层

继续看：

- minute bucket 扫描 lag
- `SchedulerWorker` 固定节奏是否开始拖后
- 调用线程栈里是否出现 `ExecutorWorker.executeTimerCallBack`

一旦扫描线程也在执行业务 callback，说明 `CallerRunsPolicy` 已经开始把阻塞传播回上游。

## 5. 先止血和长期治理怎么拆

### 先止血

1. 限最热 callback 路由或 app 的并发，把 `TriggerPoolTask.runExecutor` 的入流压下来
2. 缩小 `TriggerTimerTask.handleBatch` 单轮投递批次，避免 backlog 继续按秒放大
3. 按 `triggerPool` 水位对 `SchedulerWorker` 做反压，不要再让 fixedRate 无脑提交

### 再根治

1. 给 `ExecutorWorker.executeTimerCallBack` 补齐明确的 connect/read timeout 预算
2. 治理最慢的 callback 下游接口，避免 trigger 线程被长时间占住
3. 重审 `AsyncPool` 的深队列策略，让压力更早暴露，而不是等 `CallerRunsPolicy` 才发现
4. 把重 callback 场景按 app 隔离，必要时上 MQ 做异步削峰

## 6. 这个模块和其他 xtimer demo 的边界

- 这里不把 `Full GC` 和老年代晋升当主线，那部分由 `schedule-center-fullgc-demo` 负责
- 这里不把 backlog 继续恶化后的堆放大和 `OOM` 当主线，那部分由 `schedule-center-oom-demo` 负责
- 这里不把空扫热点或 DB fallback 查询风暴导致的 CPU 高当主线，那部分由 `cpu-high-troubleshooting-demo` 负责
- 当前模块唯一主线是 `callback timeout -> triggerPool backlog -> schedulerPool lag -> CallerRunsPolicy`

## 7. 面试里可以怎么说

> 这个问题我会按 xtimer 的真实 callback 链路来讲。因为 SchedulerWorker 每秒都在提交 minute slice，TriggerTimerTask.handleBatch 又会不断把 callback 交给 TriggerPoolTask.runExecutor，但 ExecutorWorker.executeTimerCallBack 是同步 HTTP 调用，只要下游 RT 被拉长，triggerPool 的消费能力就会明显落后于投递速度。前期由于 AsyncPool 用的是 99999 深队列，现象只是 triggerPool.activeCount 和 queueSize 持续上涨；真正危险的是队列顶满以后 CallerRunsPolicy 会让调用线程自己执行 callback，最后慢回调会反压回 minute bucket 扫描和 schedulerPool，表现成调度 lag 扩散。处理上先限最热 callback、缩小投递批次和加背压，长期再治理 callback timeout、线程池隔离和异步削峰。 
