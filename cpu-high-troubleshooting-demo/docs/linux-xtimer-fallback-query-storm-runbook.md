# Linux 服务器手工 Runbook：xtimer DB fallback 查询风暴 CPU 高

这份 runbook 只做一件事：

在 Linux 机器上，把 `xtimer` 的 DB fallback 查询风暴 CPU 高场景从启动、观察、留证、止血到修复后验证完整走一遍。

## 0. 适用前提

- 服务器系统：`Linux`
- JDK：`8`
- Maven：可用
- 当前目录：`cpu-high-troubleshooting-demo`
- 演练入口：`com.example.cpuhightroubleshootingdemo.cpu.XtimerFallbackStormCpuDemo`
- 模拟热点线程：`xtimer-fallback-query-storm`
- 对应第一项目真实链路：`TriggerTimerTask.getTasksByTime -> TaskCache.getTasksFromCache -> taskMapper.getTasksByTimeRange`

这个 runbook 对齐的是 `ScheduleCenter 定时调度中心` 里 xtimer 的 fallback 链路，重点不是“DB 慢”这四个字，而是 `minuteBucketKey` 在异常路径上连续重试。

## 1. 登录机器后先确认环境

先执行：

```bash
java -version
mvn -version
pwd
```

你至少要确认：

- `java` 是 `1.8`
- 当前目录就是 `cpu-high-troubleshooting-demo`
- `mvn` 可以正常编译当前模块

## 2. 手工启动 fallback storm 场景

先准备输出目录并编译：

```bash
mkdir -p lab-output/xtimer-fallback-storm
mvn -q -DskipTests compile
```

再启动演练进程：

```bash
nohup java -cp target/classes com.example.cpuhightroubleshootingdemo.cpu.XtimerFallbackStormCpuDemo --run 180 \
  > lab-output/xtimer-fallback-storm/app.log 2>&1 &
```

确认进程已经起来：

```bash
tail -n 20 lab-output/xtimer-fallback-storm/app.log
```

正常你会看到类似输出：

- `pid=<pid>, hotThread=xtimer-fallback-query-storm`
- `second=<n> attempts=<count> hottestBucket=<minuteBucketKey>:<count>`

先把 `pid` 记下来，后面的 `top / jstack / profiler` 都用它。

## 3. 第二个终端跑 top -> jstack -> profiler 链

如果你想从日志里直接提取 `pid`：

```bash
PID=$(grep -o 'pid=[0-9]*' lab-output/xtimer-fallback-storm/app.log | head -n 1 | cut -d= -f2)
echo "$PID"
```

### 3.1 先看热点线程

```bash
top -Hp "$PID"
```

重点看：

- 最热线程是不是 `xtimer-fallback-query-storm`
- 是不是单个线程在反复拉高 CPU
- callback 线程是不是也被上下文构造连带打热

把最热的 `tid` 记下来。

### 3.2 把 tid 映射成 Java 栈

```bash
printf '%x\n' <tid>
jstack "$PID" | grep -A 25 <nid>
```

为了更快确认 fallback 链路，再补一条：

```bash
jstack "$PID" | grep -n "getTasksByTimeRange\|notify_http_param" -A 10
```

真实 xtimer 线上排查时，你要把热点对回这条链路：

- `TriggerTimerTask.getTasksByTime`
- `TaskCache.getTasksFromCache`
- `taskMapper.getTasksByTimeRange`
- callback body / `notify_http_param` 重建

### 3.3 再补一轮 CPU profiler

如果机器里已经装了 `async-profiler`：

```bash
/opt/async-profiler/profiler.sh -e cpu -d 30 \
  -f lab-output/xtimer-fallback-storm/cpu.html "$PID"
```

如果安装路径不在 `/opt/async-profiler`，把命令里的路径替换成你的实际位置。

火焰图重点看：

- `taskMapper.getTasksByTimeRange` 相关调用链是不是最宽
- callback 上下文重建是不是明显放大了 CPU
- 热点是否集中在少量 `minuteBucketKey`

## 4. 现场证据至少留这 4 份

先把线程和 top 输出落盘：

```bash
top -Hp "$PID" -b -n 3 > lab-output/xtimer-fallback-storm/top.txt
jcmd "$PID" Thread.print -l > lab-output/xtimer-fallback-storm/thread-print.txt
jstack "$PID" > lab-output/xtimer-fallback-storm/jstack.txt
grep 'hottestBucket=' lab-output/xtimer-fallback-storm/app.log | tail -n 20 \
  > lab-output/xtimer-fallback-storm/hottest-bucket-trend.txt
```

在真实 xtimer 机器上，证据还要补齐这几类：

- 同一 `minuteBucketKey` 的连续 fallback 次数
- `taskMapper.getTasksByTimeRange` RT 和调用量
- Redis RT 和 Redis miss 比例，确认是不是先从 Redis 抖动切到 fallback
- `triggerPool` 的 `queueSize`、callback RT、日志量，确认是不是 CPU / DB / 日志一起放大

## 5. 线上先止血怎么做

止血的目标是先把 `minuteBucketKey` 的异常重试频率打下来。

优先顺序建议这样做：

1. 对连续 fallback 的 `minuteBucketKey` 做限频或熔断，先阻止秒级重试。
2. 给 `taskMapper.getTasksByTimeRange` 临时加结果集上限，避免异常路径把对象构造做大。
3. 如果 `triggerPool` 已经被拖高，先把 fallback 查询和真实 callback 执行错峰，避免继续抢线程和 CPU。
4. 必要时扩容 trigger 节点，先把同一批问题 key 的热点分摊开。

## 6. 根因修复要改什么

长期修复要回到 xtimer 的 fallback 策略本身：

1. 对同一 `minuteBucketKey` 的 fallback 路径加退避和限频，不允许连续秒级打 DB。
2. 把 `taskMapper.getTasksByTimeRange` 的结果集和查询窗口做硬上限，避免异常路径一次拉太多任务。
3. 让 `triggerPool` 水位反向约束 fallback 调度，不能在 callback 已经堆积时继续放大查询。
4. 给同 key fallback 次数、DB RT、Redis miss 比例、callback RT 补监控和告警。

## 7. 修复后怎么验收

修复后，用同样的命令再跑一轮：

```bash
java -cp target/classes com.example.cpuhightroubleshootingdemo.cpu.XtimerFallbackStormCpuDemo --run 60
```

至少确认这几件事：

- `top -Hp` 里不再是单个 `xtimer-fallback-query-storm` 线程长期接近满核
- 同样时长下，`attempts` 的增长幅度明显下降
- `hottestBucket` 不再持续被同一个 `minuteBucketKey` 长时间占满
- `jstack` 和火焰图里 `taskMapper.getTasksByTimeRange` 不再是绝对主热点
- 真实 xtimer 实例上的 DB RT、`triggerPool.queueSize` 和 callback RT 回到可接受范围

## 8. 演练结束后收尾

如果只是演练，结束后把进程停掉：

```bash
kill "$PID"
```

再把证据目录打包留档：

```bash
tar -czf lab-output/xtimer-fallback-storm.tar.gz lab-output/xtimer-fallback-storm
```
