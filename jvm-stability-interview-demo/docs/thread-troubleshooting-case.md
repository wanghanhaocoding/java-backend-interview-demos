# 线程定位案例：线程卡住后，怎么从线程 dump 反查到代码

## 一、这个案例怎么和你的简历对上

这个案例最适合挂在你的：

- `AsyncJobCenter`
- `ScheduleCenter`
- 回执处理、调度扫描、补偿任务、线程池隔离

一句话背景：

> 线上偶发“任务不报错，但就是卡住”的问题时，先别急着猜数据库还是网络。更稳的做法是先拿线程 dump，看线程名、线程状态和方法栈，再回到代码定位具体卡在哪条链路。

## 二、出现过程

- 某批任务处理变慢
- 日志里没有明显业务异常
- 线程池活跃线程不算低，但吞吐掉下来了
- 部分任务一直停留在“处理中”

## 三、排查过程

### 第 1 步：先拿到 Java 进程

```bash
jps -l
```

### 第 2 步：导线程栈

```bash
jcmd <pid> Thread.print
```

或者：

```bash
jstack <pid>
```

### 第 3 步：先看线程名，再看线程状态

这个 demo 里故意构造了 5 条典型线程：

- `receipt-callback-busy-thread`：持续 RUNNABLE，模拟回调重试线程占 CPU
- `receipt-lock-holder-thread`：持有锁并 sleep，模拟长事务/大锁范围
- `receipt-lock-blocked-thread`：BLOCKED，等待上面的锁
- `callback-queue-waiting-thread`：WAITING，阻塞在队列取任务
- `schedule-poller-sleeping-thread`：TIMED_WAITING，模拟调度线程周期休眠

### 第 4 步：顺着方法栈回到代码

这个 demo 的设计重点不是“把线程搞坏”，而是“让你能从线程 dump 明确回到方法入口”。

你在线程栈里重点找这些方法：

- `callbackRetryBusyLoop`
- `scanRetryWindow`
- `decodeReceiptPayload`
- `holdReceiptStateMonitor`
- `rebuildReceiptProjectionAfterLock`
- `waitForNextCallbackBatch`
- `sleepBetweenScheduleRounds`

也就是说，排查顺序应该是：

1. 先看线程名，判断它属于哪条业务链路
2. 再看线程状态，判断它是 CPU 忙、锁等待、队列等待还是正常 sleep
3. 再看方法栈顶部和调用链
4. 最后回到源码确认为什么卡在这里

## 四、如何在本地运行

### 1. 编译

```bash
mvn -q -DskipTests compile
```

### 2. 启动 demo

```bash
java -cp target/classes com.example.jvmstabilitydemo.thread.ThreadTroubleshootingDemo --run
```

### 3. 另开一个终端排查

```bash
jps -l
jcmd <pid> Thread.print
jstack <pid>
```

如果你在 Windows PowerShell 上跑，也是同样这几个命令。

## 五、你在这个 demo 里应该看到什么

### 1. `receipt-lock-blocked-thread`

你会看到它是 `BLOCKED`，而且会等待 `receipt-lock-holder-thread` 释放锁。

这个时候你就能顺着线程栈回到：

- `rebuildReceiptProjectionAfterLock`

### 2. `callback-queue-waiting-thread`

你会看到它在队列等待，线程状态通常是 `WAITING`。

这就能帮助你区分：

- 它不是死锁
- 也不是 CPU 忙
- 而是在等新任务

### 3. `receipt-callback-busy-thread`

你会看到它持续 `RUNNABLE`，方法栈会落在：

- `callbackRetryBusyLoop`
- `scanRetryWindow`
- `decodeReceiptPayload`

这个场景最适合练“CPU 高线程怎么回到代码”。

## 六、沉淀过程

1. 线程池线程必须有清晰命名
2. 线程 dump 排查先看线程名，再看状态，再看方法栈
3. `BLOCKED`、`WAITING`、`TIMED_WAITING`、`RUNNABLE` 的排查含义要区分开
4. 线程问题不要只停留在“线程多了”这种表面结论

## 七、1 分钟面试回答版

> 如果线程运行出了问题，我一般不会直接猜代码，而是先拿线程 dump。第一步先看线程名，判断它属于调度、回调、补偿还是业务 worker；第二步看线程状态，是 RUNNABLE、BLOCKED、WAITING 还是 TIMED_WAITING；第三步看线程栈顶部和调用链，把线程现场和具体方法名对上。比如我专门做过一个 demo，里面会构造回调忙线程、锁阻塞线程、队列等待线程和调度休眠线程，用 `jcmd Thread.print` 或 `jstack` 就能直接把线程名和方法栈对上，快速回到代码入口。这套方法在线上排查线程池卡顿、锁竞争和 CPU 飙高时都很实用。
