# Linux 服务器实操指南：第 3 步 死锁

这份文档只做一件事：

在一台空 Linux 云服务器上，把 xtimer 死锁场景从启动、导栈、止血到修复后验证完整走一遍。

## 0. 适用前提

- 服务器系统：`Linux`
- JDK：`8`
- Maven：可选
- 当前目录：`jvm-stability-interview-demo`

这个模块已经改成 `JDK8` 可运行。

这次不用脚本，全部按手工命令走，目的是把 `jps -> jstack -> jcmd` 这条现场链路练熟。

### Ubuntu / Debian

```bash
sudo apt update
sudo apt install -y openjdk-8-jdk maven procps
```

### CentOS / RHEL / openEuler / Anolis

```bash
sudo yum install -y java-1.8.0-openjdk-devel maven procps-ng
```

## 1. 上传代码后先确认环境

先执行：

```bash
java -version
mvn -version
jps -l
jcmd -l
which jstack
```

你至少要确认 3 件事：

- `java` 是 `1.8`
- `jps / jstack / jcmd` 都能执行
- 当前目录就在 `jvm-stability-interview-demo`

## 2. 先编译，再准备现场目录

优先走 Maven：

```bash
mvn -q -DskipTests compile
mkdir -p lab-output/deadlock
```

如果服务器上没有 Maven，也可以手工编译：

```bash
mkdir -p target/classes
find src/main/java -name '*.java' | sort > target/java-sources.list
javac -encoding UTF-8 -d target/classes @target/java-sources.list
mkdir -p lab-output/deadlock
```

## 3. 用可附着方式启动死锁现场

执行：

```bash
java -cp target/classes \
  com.example.jvmstabilitydemo.deadlock.DeadlockDemo \
  --hold-seconds=60 \
  > lab-output/deadlock/app.log 2>&1 &
echo $! > lab-output/deadlock/app.pid
```

这里的 `--hold-seconds=60` 是专门给 Linux 手工导栈准备的。

含义是：

- `DeadlockDemo` 先按正常逻辑制造死锁
- 打印内部检测结果
- 主线程额外保活 `60` 秒，给你第二个终端执行 `jstack / jcmd`

先看一眼日志是否已经形成死锁：

```bash
tail -n 20 lab-output/deadlock/app.log
```

正常会看到这些关键词：

- `detected deadlocked thread count=2`
- `deadlock thread=xtimer-executor-worker`
- `deadlock thread=xtimer-disable-timer-worker`

## 4. 用 jps 先确认 pid 和主类

执行：

```bash
cat lab-output/deadlock/app.pid
jps -l | grep com.example.jvmstabilitydemo.deadlock.DeadlockDemo
```

这一步的目的不是只拿 pid，而是确认你导栈的进程就是当前这次死锁演练进程。

## 5. 用 jstack 拿第一份死锁证据

执行：

```bash
pid=$(cat lab-output/deadlock/app.pid)
jstack "$pid" | tee lab-output/deadlock/jstack-1.txt
```

你重点看 4 类信号：

- `Found one Java-level deadlock`
- `xtimer-executor-worker`
- `xtimer-disable-timer-worker`
- `waiting to lock` / `BLOCKED`

如果你想快速筛关键词：

```bash
grep -nE "Found one Java-level deadlock|xtimer-executor-worker|xtimer-disable-timer-worker|waiting to lock|BLOCKED" \
  lab-output/deadlock/jstack-1.txt
```

## 6. 用 jcmd 再补一份线程现场

继续执行：

```bash
jcmd "$pid" Thread.print -l > lab-output/deadlock/jcmd-thread-1.txt
grep -nE "Java-level deadlock|xtimer-executor-worker|xtimer-disable-timer-worker|waiting to lock|BLOCKED" \
  lab-output/deadlock/jcmd-thread-1.txt
```

如果你还想把启动参数也补齐：

```bash
jcmd "$pid" VM.command_line
```

到这里，你手里至少应该有两份独立证据：

1. `jstack-1.txt`
2. `jcmd-thread-1.txt`

## 7. 把现场和 xtimer 代码链路对上

这一步不要停在“看到死锁”。

你要把 dump 里的线程，和 demo 里的两条真实路径一一对上：

- `xtimer-executor-worker` 对应 `DeadlockDemo.lockTaskThenTimer`
- `xtimer-disable-timer-worker` 对应 `DeadlockDemo.lockTimerThenTask`

这两条路径表达的就是 xtimer 里的经典撞锁顺序：

1. 执行回调线程先拿 `timer_task`，再拿 `xtimer`
2. 停用定时器线程先拿 `xtimer`，再拿 `timer_task`

如果两份 dump 都能看到两条线程互相持有对方需要的锁，这条证据链就闭环了：

`业务卡住 -> jps 拿 pid -> jstack / jcmd 发现 Java 级死锁 -> 回到 xtimer 锁顺序确认根因`

## 8. 先止血，再恢复服务

线上止血动作建议按这个顺序做：

1. 先保留 `jstack` 和 `jcmd` 产物，不要一上来就重启
2. 临时摘掉高风险的“停用定时器 / 批量补偿”操作
3. 把故障节点从调度流量里摘出去，避免继续卡住新任务
4. 再做重启或拉起新实例

如果你想在 demo 里多补一份标准线程 dump，再结束进程，可以执行：

```bash
pid=$(cat lab-output/deadlock/app.pid)
kill -3 "$pid"
sleep 2
tail -n 80 lab-output/deadlock/app.log
kill "$pid"
wait "$pid" 2>/dev/null || true
```

`kill -3` 会把一份线程栈打到 `app.log`，适合补最后一次现场证据。

## 9. 修复后怎么验证不是只恢复了这一次

修复后不要只看“服务恢复了”，而要明确验证“锁顺序已经稳定一致”。

### 方案 A：连续导 3 次线程 dump

在预发或灰度环境重放“执行回调 + 停用定时器”并发流量后，连续导 3 次线程栈：

```bash
pid=<pid>
for i in 1 2 3; do
  jcmd "$pid" Thread.print -l > "lab-output/deadlock/post-fix-${i}.txt"
  sleep 5
done
grep -R "Java-level deadlock" lab-output/deadlock/post-fix-*.txt
```

你期待的结果是：

- `grep` 没有任何输出
- 不再出现 `xtimer-executor-worker` 和 `xtimer-disable-timer-worker` 长时间互相等待
- 同一批并发请求最终都能结束，不再无限 `BLOCKED`

### 方案 B：并发回放 xtimer 两条冲突路径

如果你是在真实 xtimer 服务上验证，就把下面两类操作并发回放：

1. 执行回调链路
2. 停用定时器链路

验证时至少做两件事：

- 连续观察 `5` 到 `10` 分钟，不要只看一瞬间
- 期间每隔 `30` 秒补一份 `jcmd Thread.print -l`

只要连续多份 dump 都没有 `Java-level deadlock`，并且管理侧停用和执行侧回调都能正常结束，这次修复才算真的站住。

## 10. 这一轮结束后，你应该能回答什么

至少能稳定回答这 4 个问题：

1. 为什么这不是“线程池打满”那么简单
2. 你是怎么用 `jps -> jstack -> jcmd` 把现场证据串起来的
3. 你在 xtimer 里是怎么把 `timer_task / xtimer` 两条锁路径对上的
4. 你先止血做了什么，修完后又怎么证明没有再次死锁
