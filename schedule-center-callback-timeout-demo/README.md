# schedule-center-callback-timeout-demo

一个专门讲 `ScheduleCenter / xtimer` 回调超时与线程池饱和的教学项目。

这版不再把“慢 callback”混进 `Full GC / OOM / CPU 高` 的故事里，而是单独对齐你简历第一个项目和真实代码：

- 简历项目：`ScheduleCenter 定时调度中心`
- 真实代码：`bitstorm-svr-xtimer`
- 真实代码锚点：`AsyncPool.schedulerPoolExecutor`、`AsyncPool.triggerPoolExecutor`、`TriggerTimerTask.handleBatch`、`TriggerPoolTask.runExecutor`、`ExecutorWorker.work`、`taskMapper.getTasksByTimerIdUnix`、`ExecutorWorker.executeTimerCallBack`

## 这个 demo 讲什么

对应代码：

- `callback/ScheduleCenterCallbackTimeoutDemoService.java`

这版会直接把 callback timeout 的主线讲清楚：

1. `SchedulerWorker` 仍按 `@Scheduled(fixedRate = 1000)` 每秒提交 `5 个 bucket * 2 个分钟窗口 = 10` 个 minute slice
2. `TriggerTimerTask.handleBatch` 拉到到期任务后，经 `TriggerPoolTask.runExecutor` 把 callback 投递到 `triggerPool`
3. `ExecutorWorker.work` 会查 `taskMapper.getTasksByTimerIdUnix`，随后在 `ExecutorWorker.executeTimerCallBack` 里同步发 HTTP callback
4. 当 callback RT 稳定在 `3000ms`，而教学预算只接受 `2000ms` 时，`triggerPool` 处理能力约 `33.33/s`，却要接住约 `120/s` 的 callback 到达速率
5. `AsyncPool` 对 `schedulerPool` 和 `triggerPool` 都配置了 `CallerRunsPolicy`，所以队列顶满后，callback 会反压回调用线程，最终表现成 `triggerPool backlog -> schedulerPool lag`

## 为什么这和真实 xtimer 对得上

这版不是泛泛讲“线程池满了”，而是直接对着真实实现说：

- `AsyncPool.schedulerPoolExecutor` / `AsyncPool.triggerPoolExecutor` 都是 `ThreadPoolTaskExecutor + CallerRunsPolicy`
- `TriggerPoolTask.runExecutor` 是 callback 投递入口
- `ExecutorWorker.executeTimerCallBack` 是同步 HTTP 调用，下游慢 RT 会直接占住 `triggerPool` 工作线程
- `SchedulerWorker` 固定速率继续提交 minute slice，所以如果没有背压，慢 callback 最终一定会扩散到调度层

## 和已有 demo 的边界

- `schedule-center-fullgc-demo` 重点讲老年代晋升和 `Full GC` 频繁
- `schedule-center-oom-demo` 重点讲 backlog 继续恶化后的堆内存放大和 `OOM`
- `cpu-high-troubleshooting-demo` 重点讲空扫热点和 DB fallback 查询风暴把 CPU 打高
- 当前模块只聚焦 `callback timeout -> triggerPool backlog -> schedulerPool delay propagation -> CallerRunsPolicy`

## 你最先看这 5 个文件就够了

1. `callback/ScheduleCenterCallbackTimeoutDemoService.java`
2. `docs/callback-timeout-case.md`
3. `docs/linux-callback-timeout-runbook.md`
4. `demo/DemoRunner.java`
5. `ScheduleCenterCallbackTimeoutDemoTest`

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. callback timeout 的事故链路
2. 当前案例的关键口径
3. 真实 xtimer 代码锚点
4. delay propagation stages
5. 现场证据
6. 边界与处理

## Linux 服务器手工 runbook

- `docs/linux-callback-timeout-runbook.md`

## 如何运行测试

```bash
mvn test
```

重点看：

- `ScheduleCenterCallbackTimeoutDemoTest`

## 面试里怎么说最稳

> 我会把这个问题讲成 xtimer 里的 callback timeout 反压事故，而不是泛泛说“线程池满了”。因为真实链路里，SchedulerWorker 每秒都会继续提交 minute slice，TriggerTimerTask.handleBatch 又会不断把 callback 交给 TriggerPoolTask.runExecutor；但 ExecutorWorker.executeTimerCallBack 是同步 HTTP 调用，只要下游 callback RT 拉到 3 秒左右，triggerPool 的消费能力就会明显落后于投递速度。前期因为 queueCapacity 很深，现象只是 triggerPool.activeCount 和 queueSize 持续上升；等到 AsyncPool 的 CallerRunsPolicy 开始生效，调用线程会自己执行 callback，minute bucket 扫描和 schedulerPool 提交也会被拖慢，最后表现成调度 lag 扩散。处理上我会先限最热 callback、缩小单轮投递批次、按 triggerPool 水位加背压，长期再治理 callback timeout、线程池隔离和异步削峰。 
