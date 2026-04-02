# Linux 服务器手工 Runbook：xtimer 空 minuteBucketKey 扫描 CPU 高

这份 runbook 只做一件事：

在 Linux 机器上，把 `xtimer` 的空 `minuteBucketKey` 扫描 CPU 高场景从启动、观察、留证、止血到修复后验证完整走一遍。

## 0. 适用前提

- 服务器系统：`Linux`
- JDK：`8`
- Maven：可用
- 当前目录：`cpu-high-troubleshooting-demo`
- 演练入口：`com.example.cpuhightroubleshootingdemo.cpu.XtimerEmptyScanCpuDemo`
- 模拟热点线程：`xtimer-trigger-empty-scan`
- 对应第一项目真实链路：`SchedulerWorker -> SchedulerTask -> TriggerWorker.work -> TriggerTimerTask.run -> TaskCache.getTasksFromCache`

这个 runbook 对齐的是 `ScheduleCenter 定时调度中心` 里的 xtimer 扫描链路，不混入别的项目术语。

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

## 2. 手工启动 empty-scan 场景

先准备输出目录并编译：

```bash
mkdir -p lab-output/xtimer-empty-scan
mvn -q -DskipTests compile
```

再启动演练进程：

```bash
nohup java -cp target/classes com.example.cpuhightroubleshootingdemo.cpu.XtimerEmptyScanCpuDemo --run 180 \
  > lab-output/xtimer-empty-scan/app.log 2>&1 &
```

确认进程已经起来：

```bash
tail -n 20 lab-output/xtimer-empty-scan/app.log
```

正常你会看到类似输出：

- `pid=<pid>, hotThread=xtimer-trigger-empty-scan`
- `second=<n> emptyScans=<count> timerWakeUps=<count> fallbackChecks=<count>`

先把 `pid` 记下来，后面的 `top / jstack / profiler` 都用它。

## 3. 第二个终端跑 top -> jstack -> profiler 链

如果你想从日志里直接提取 `pid`：

```bash
PID=$(grep -o 'pid=[0-9]*' lab-output/xtimer-empty-scan/app.log | head -n 1 | cut -d= -f2)
echo "$PID"
```

### 3.1 先看热点线程

```bash
top -Hp "$PID"
```

重点看：

- 是否是单个线程接近满核
- 热线程名是不是贴近 `xtimer-trigger-empty-scan`
- 真实 callback 线程有没有同步打满

把最热的 `tid` 记下来。

### 3.2 把 tid 映射成 Java 栈

```bash
printf '%x\n' <tid>
jstack "$PID" | grep -A 25 <nid>
```

真实 xtimer 线上排查时，你要把热点对回这条链路：

- `TriggerWorker.work`
- `TriggerTimerTask.run`
- `TaskCache.getTasksFromCache`
- 空 `minuteBucketKey` 的 `rangeByScore` 和 fallback guard

### 3.3 再补一轮 CPU profiler

如果机器里已经装了 `async-profiler`：

```bash
/opt/async-profiler/profiler.sh -e cpu -d 30 \
  -f lab-output/xtimer-empty-scan/cpu.html "$PID"
```

如果安装路径不在 `/opt/async-profiler`，把命令里的路径替换成你的实际位置。

火焰图重点看：

- empty scan 相关循环是不是最宽
- key / window 字符串构造是不是放大了热点
- fallback guard 有没有跟着一起热

## 4. 现场证据至少留这 4 份

先把线程和 top 输出落盘：

```bash
top -Hp "$PID" -b -n 3 > lab-output/xtimer-empty-scan/top.txt
jcmd "$PID" Thread.print -l > lab-output/xtimer-empty-scan/thread-print.txt
jstack "$PID" > lab-output/xtimer-empty-scan/jstack.txt
grep 'emptyScans=' lab-output/xtimer-empty-scan/app.log | tail -n 20 \
  > lab-output/xtimer-empty-scan/empty-scan-trend.txt
```

在真实 xtimer 机器上，证据还要补齐这几类：

- `schedulerPool` / `triggerPool` 的 `activeCount`、`queueSize`、`reject`
- 同一 `minuteBucketKey` 的连续空命中次数
- `rangeByScore` miss 次数和 RT
- callback 真实吞吐，确认是不是“CPU 高但执行量没涨”

## 5. 线上先止血怎么做

止血只解决当前实例别继续烧 CPU，不等于根因修复。

优先顺序建议这样做：

1. 在 `SchedulerWorker` 侧临时下调每秒提交的 bucket 分片量，先把 `5 个 bucket * 2 个分钟窗口` 的压力降下来。
2. 对持续空命中的 `minuteBucketKey` 做临时摘除、降频或延后扫描，避免 `TriggerTimerTask` 每秒空扫同一批 key。
3. 如果热点已经影响 callback，先扩容 trigger 节点，把 `TriggerWorker` 的 CPU 压力摊薄。
4. 如果 `triggerPool` 也开始堆积，先让扫描路径降速，不要继续跟 callback 抢 CPU。

## 6. 根因修复要改什么

长期修复要回到 xtimer 的扫描模型本身：

1. 对空 `minuteBucketKey` 做退避，不要每秒固定频率空扫到分钟结束。
2. 缩短 `TriggerWorker` 单次持有扫描线程的生命周期，避免一个 Timer 线程长期黏在核上。
3. 让 `schedulerPool` / `triggerPool` 的水位反向约束抢锁和扫描，不能在下游已经饱和时继续扩扫描。
4. 给 `rangeByScore` miss、empty scan、fallback guard 次数补监控和告警。

## 7. 修复后怎么验收

修复后，用同样的命令再跑一轮：

```bash
java -cp target/classes com.example.cpuhightroubleshootingdemo.cpu.XtimerEmptyScanCpuDemo --run 60
```

至少确认这几件事：

- `top -Hp` 里不再是单个 `xtimer-trigger-empty-scan` 线程长期接近满核
- 同样时长下，`emptyScans` 的增长幅度明显下降，或者已经和真实到期任务量挂钩
- `jstack` 不再长期卡在空 `minuteBucketKey` 的循环链路上
- 火焰图里 empty scan 的最宽栈明显变窄
- 真实 xtimer 实例上的 `triggerPool.queueSize` 和 callback RT 恢复到可接受范围

## 8. 演练结束后收尾

如果只是演练，结束后把进程停掉：

```bash
kill "$PID"
```

再把证据目录打包留档：

```bash
tar -czf lab-output/xtimer-empty-scan.tar.gz lab-output/xtimer-empty-scan
```
