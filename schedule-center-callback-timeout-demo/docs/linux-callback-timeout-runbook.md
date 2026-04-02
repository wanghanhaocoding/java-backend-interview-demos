# Linux 服务器实操 runbook：xtimer callback timeout / thread-pool saturation

## 1. 适用前提与范围确认

- 服务器系统：`Linux`
- JDK：`8`
- 当前案例目录：`schedule-center-callback-timeout-demo`
- 真实代码锚点：`AsyncPool`、`TriggerTimerTask`、`TriggerPoolTask`、`ExecutorWorker`
- 当前 runbook 只聚焦 `callback timeout -> triggerPool backlog -> schedulerPool lag -> CallerRunsPolicy`

如果现场主现象已经变成 `Full GC`、`OOM` 或 CPU 热点，优先切到对应 demo 的 runbook，不要把问题混讲。

## 2. 复现入口与基础启动

先保证教学模块能启动：

```bash
mvn spring-boot:run
```

当前模块启动后会打印：

1. callback timeout 的事故链路
2. 当前案例的关键口径
3. 真实 xtimer 代码锚点
4. delay propagation stages
5. 现场证据
6. 边界与处理

如果你是在真实 xtimer 服务上排查，先记住本次案例最关键的两个事实：

- `ExecutorWorker.executeTimerCallBack` 是同步 HTTP callback
- `AsyncPool.schedulerPoolExecutor` / `AsyncPool.triggerPoolExecutor` 都是 `CallerRunsPolicy`

## 3. 线上排查链路

### 第一步：先确认是不是 callback 慢 RT

先拿到 Java 进程：

```bash
jps -l
```

然后确认最近是否存在 callback 超时、回调 RT 飙高或下游不可达：

- 业务 callback 监控里是否大量超过 `2000ms` 教学预算
- 应用日志里是否出现回调失败、回调异常、下游超时

如果 callback RT 还很低，就别急着套这个 runbook。

### 第二步：确认 triggerPool 是否已经进入 backlog

关注这几个指标：

- `triggerPool.activeCount`
- `triggerPool.queueSize`
- callback timeout 次数
- callback RT 分位数

教学口径下，如果 callback RT 稳定在 `3000ms`，而每秒 callback 到达速率约 `120/s`，那 `triggerPool` 理论只能消费约 `33.33/s`，也就是每秒会净积压约 `86.67` 个 callback。

这时线程池现象通常是：

- `activeCount` 接近 `100`
- `queueSize` 持续上涨
- 业务 callback 失败率和超时率抬高

### 第三步：用线程栈确认阻塞真的在 callback 链路

```bash
jstack <pid> | grep -n "ExecutorWorker.executeTimerCallBack"
jstack <pid> | grep -n "TriggerTimerTask.handleBatch"
jstack <pid> | grep -n "TriggerPoolTask.runExecutor"
```

重点确认两件事：

1. 是否有大量线程停在 `ExecutorWorker.executeTimerCallBack`
2. 扫描 / 投递线程栈里是否开始夹带 callback 执行痕迹

第二种现象一旦出现，基本就说明 `CallerRunsPolicy` 已经把阻塞反压回调用线程。

### 第四步：确认 lag 是否已经传播到调度层

继续结合业务监控看：

- minute bucket 扫描延迟
- `SchedulerWorker` 的 fixedRate 节奏是否已经拖后
- 任务触发时间与实际 callback 时间差是否持续扩大

如果扫描 lag 和 callback RT 同时抬高，就说明这已经不是单纯的 callback 失败，而是调度系统级联延迟。

## 4. 先止血动作

### 止血 1：限最热 callback

- 先限制最热 callback 路由或 app 的并发
- 优先把 `TriggerPoolTask.runExecutor` 的入流压下来

目标不是立刻“修复一切”，而是先阻止 `triggerPool.queueSize` 继续滚雪球。

### 止血 2：缩小单轮投递批次

- 临时减小 `TriggerTimerTask.handleBatch` 单轮投递量
- 必要时暂停最慢的一类 callback 任务

如果 callback RT 还没恢复，继续维持原始投递节奏只会把问题扩散到 `schedulerPool`。

### 止血 3：按 triggerPool 水位加背压

- 当 `triggerPool` 接近满载时，限制新的 minute slice 提交
- 避免 `SchedulerWorker` 继续按 `1000ms` 固定节奏无脑推动 backlog

这一步的目标是保护调度层，不要让 lag 再扩大。

## 5. 根因治理方向

1. 给 `ExecutorWorker.executeTimerCallBack` 补齐明确的 connect/read timeout 预算
2. 治理最慢的 callback 下游接口，降低 `triggerPool` 单任务占用时间
3. 重审 `AsyncPool` 的深队列策略，让压力更早暴露
4. 按 app / callback 类型做线程池隔离，避免最慢的路由拖垮整个 triggerPool
5. 对重 callback 场景做异步削峰，例如 MQ 解耦

## 6. 修复后验证

至少验证这些信号已经回落：

- callback RT 回到目标预算以内
- `triggerPool.queueSize` 不再持续单向增长
- `triggerPool.activeCount` 不再长期钉死在上限
- minute bucket 扫描 lag 明显回落
- 线程栈里不再长期看到调用线程被迫执行 `ExecutorWorker.executeTimerCallBack`

如果这些信号仍未恢复，就说明“慢 callback 反压调度链路”这个根因还没有真正收住。
