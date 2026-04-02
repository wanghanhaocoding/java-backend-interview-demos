# Linux 服务器实操指南：xtimer Full GC 手工 Runbook

这份 runbook 只做一件事：

在 Linux 节点上把 `ScheduleCenter / xtimer` 的 `Full GC` 现场按“先对齐口径，再手工复现，再采证诊断，再止血与根因修复，最后验证回归”完整走一遍。

这个模块没有像 `OOM` 那样附一键脚本，而是按手工 run 的方式给出命令。原因很简单：`Full GC` 更依赖你把 `SchedulerWorker -> SchedulerTask.asyncHandleSlice -> TriggerWorker.work -> TriggerTimerTask -> TriggerPoolTask` 这条真实 xtimer 链路讲清楚，而不是只盯一个异常栈。

## 1. 适用前提与环境确认

- 服务器系统：`Linux`
- JDK：`8`
- Maven：可用即可
- 当前目录：`schedule-center-fullgc-demo`
- 这份文档同时覆盖两件事：
  1. 用当前 demo 对齐 xtimer 的真实参数和代码 anchor
  2. 在真实 `bitstorm-svr-xtimer` 节点上按 Linux 命令收集 `Full GC` 证据

先确认环境：

```bash
java -version
mvn -version
mkdir -p lab-output/full-gc
```

先把教学模块跑一遍，确认你说的口径和代码一致：

```bash
mvn -q -DskipTests spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Xms256m -Xmx256m -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:lab-output/full-gc/gc.log" \
  | tee lab-output/full-gc/demo-preview.log
```

这一步主要是对齐 `ScheduleCenterFullGcTeachingApplication`、`DemoRunner` 和 `ScheduleCenterFullGcDemoService#frequentFullGcCase` 的输出，不是为了立刻把 demo 打到 `OOM`。你至少要确认这些 xtimer 常量和结论已经跑出来：

- `SchedulerWorker.fixedRate = 1000ms`
- `5` 个 bucket
- “上一分钟补偿 + 当前分钟实时”两个窗口
- `TriggerWorker.work` 单分片生命周期接近 `60s`
- `schedulerPool.maxPoolSize = 100`
- `schedulerPool.queueCapacity = 99999`
- 稳态并发需求约 `600`
- 理论排队分片约 `500`

如果你想快速核对输出里的关键字段：

```bash
grep -E "XtimerRuntimeProfile|PressureMetrics|queuedSlices|estimatedRetainedMb|TriggerWorker.work" \
  lab-output/full-gc/demo-preview.log
```

## 2. 手工复现场景与启动命令

`Full GC` 的复现重点不是先追 `OOM`，而是先把“长生命周期分片扫描 + 深队列 + Redis/DB fallback 放大对象数量”这个组合跑出来。

如果你在教学模块里手工预演，直接保留上一步命令即可；如果你在真实 `bitstorm-svr-xtimer` 压测节点上手工复现，做法是把 GC 参数加到你现有启动命令里，不额外发明一套脚本：

```bash
export JAVA_TOOL_OPTIONS="-Xms512m -Xmx512m -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:/tmp/xtimer-full-gc.log"
# 然后按你现有的 bitstorm-svr-xtimer 启动命令重启实例
```

复现窗口里不要一次改太多变量，优先保留这几个 xtimer 条件：

- `SchedulerWorker` 继续按 `@Scheduled(fixedRate = 1000)` 每秒提交分片
- 每轮仍然覆盖 `5` 个 bucket 和 `2` 个分钟窗口
- `SchedulerTask.asyncHandleSlice` 抢锁后仍同步进入 `TriggerWorker.work`
- `TriggerWorker.work` 仍以接近 `60s` 的周期扫描同一个 `minuteBucketKey`
- Redis 抖动或 `taskMapper.getTasksByTimeRange` fallback 放大仍然存在

你真正要复现的不是“堆一下打满”，而是下面这条演化链：

1. `schedulerPool.queueSize` 先涨
2. `triggerPool.queueSize` 跟着涨
3. `Old` 区抬高
4. `FGC` 变频繁
5. 调度 lag 和回调 RT 开始抖

## 3. 现场诊断命令与证据收集

先拿到 Java 进程：

```bash
jps -l
```

拿到 xtimer 或教学模块的 `pid` 后，先看 GC：

```bash
jstat -gcutil <pid> 1000 20
jcmd <pid> GC.heap_info
jmap -histo:live <pid> | head -n 40
```

重点盯这几个信号：

- `FGC` 是否持续增长
- `Old` 是否长时间高位不回落
- Young 回收后是否还有大量对象存活
- 直方图里是否出现大量集合、任务对象、`FutureTask`、回调上下文

再看线程和进程侧，确认是不是 `TriggerWorker` 扫描太长把吞吐拖垮：

```bash
top -Hp <pid>
jcmd <pid> Thread.print | grep -n "TriggerWorker\|TriggerTimerTask\|SchedulerTask"
```

如果你的节点已经把业务指标打到日志或监控，继续补这组 xtimer 指标：

- `schedulerPool.activeCount`
- `schedulerPool.queueSize`
- `triggerPool.activeCount`
- `triggerPool.queueSize`
- 任务触发 lag
- Redis `rangeByScore` RT
- `taskMapper.getTasksByTimeRange` RT
- callback HTTP RT

如果这些字段已经进日志，可以直接 grep：

```bash
grep -E "schedulerPool|triggerPool|queueSize|lag|rangeByScore|getTasksByTimeRange|callback.*RT" \
  /path/to/xtimer.log | tail -n 50
```

判断是否命中本 runbook 的标准很简单：

- `queueSize` 单向上涨
- `lag` 和 `RT` 同时变差
- `FGC` 增长和 `Old` 高位同步出现

这时基本就不是“单纯 JVM 参数不够”，而是 xtimer 的处理模型开始失衡。

## 4. 止血动作与根因修复

先止血，目标是立刻把对象留存时间和新增对象量压下来：

1. 先缩小 `TriggerTimerTask` 每轮 `handleBatch` 的任务量，避免 Redis 和 DB 一次拉回过多对象。
2. 临时降低同时参与扫描的 `minuteBucketKey` 数量，不要让 `SchedulerWorker` 一次把过多分片持续挂在 `schedulerPool` 上。
3. 限制异常路径下 `taskMapper.getTasksByTimeRange` 的结果集规模，先把 fallback 放大量压住。
4. 必要时扩容 trigger/scheduler 节点，把已堆积的分片和 callback 压力摊薄。

再做根因修复，目标是让 xtimer 不再依赖 `queueCapacity = 99999` 藏压力：

1. 把 `schedulerPool` 和 `triggerPool` 的深队列改成有界队列，让背压提前暴露。
2. 抢锁前增加本机水位判断，不让已经饱和的节点继续接 `minuteBucketKey`。
3. 把 `TriggerWorker.work` 这种接近一分钟的长生命周期扫描拆成更短、更容易回收的子任务。
4. 降低空轮询和重复补偿，减少无效 `rangeByScore` 和无效扫描。
5. 补齐 `FGC / Old / queueSize / lag / RT` 的联动监控，否则下次还是只能靠人肉排查。

这里要特别强调：只调大堆通常只是延后告警，不是修复。只要 `SchedulerWorker` 仍然每秒持续提交、`TriggerWorker.work` 仍然长时间不退出、fallback 结果集仍然无限放大，`Full GC` 迟早还会回来。

## 5. 修复后的验证信号

修完后不要只看“服务暂时没挂”，要连续验证下面这组信号：

- `FGC` 不再持续单向增长
- `Old` 区在 Young/Full GC 后能明显回落，不再长时间钉在高位
- `schedulerPool.queueSize`、`triggerPool.queueSize` 从单向上涨变成持平或下降
- 任务触发 lag 回到单个扫描周期内或业务可接受阈值
- Redis `rangeByScore` RT、`taskMapper.getTasksByTimeRange` RT、callback RT 回到基线

建议修复后立刻再跑一轮：

```bash
jstat -gcutil <pid> 1000 20
jcmd <pid> GC.heap_info
grep -E "schedulerPool|triggerPool|queueSize|lag|rangeByScore|getTasksByTimeRange|callback.*RT" \
  /path/to/xtimer.log | tail -n 50
```

你最终要看到的是这条闭环：

1. `FGC` 下来了
2. `Old` 回落了
3. `queueSize` 不再单向涨
4. `lag` 和 `RT` 恢复了

如果只看到 `FGC` 暂时下降，但 `queueSize` 还在慢慢涨，说明你只是把故障往后拖，还没有真正修好 xtimer 的 Full GC 根因。
