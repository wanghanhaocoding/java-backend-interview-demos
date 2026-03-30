# ScheduleCenter CPU 案例：scanner 线程空扫未来 bucket，空转把 CPU 打高

## 一、这个案例怎么和你的简历对上

这个案例最适合挂在你的：

- `ScheduleCenter 定时调度中心`
- 秒级触发
- bucket 分片
- 本地预取队列

一句话背景：

> 我们为了保证秒级精度，在调度中心里维护了 scanner 线程持续扫描未来时间窗。某个早期版本为了追求“别漏扫”，队列空时也不阻塞等待，而是不断空扫 bucket 和附带解析 payload，结果业务峰值不算特别大，CPU 却先被 scanner 线程打满了。

## 二、出现过程

- 调度实例 CPU 持续偏高
- workerPool 的真实执行量没有明显同步上涨
- QPS 没到极限，但实例已经开始抖
- 日志里能看到大量空扫、空命中、预取检查

## 三、排查过程

### 第 1 步：先确认是不是 Java 进程在高

常见命令：

```bash
top -Hp <pid>
```

看到的是：

- 某一个调度线程接近满核
- 热点线程名字通常就已经能暴露方向，比如 `schedule-scanner-*`

### 第 2 步：把热点线程映射到 Java 栈

```bash
printf '%x\n' <tid>
jstack <pid> | grep -A 20 <nid>
```

这一步通常会看到线程卡在：

- `scanLoop`
- `pollBucket`
- `decodePayload`

而不是卡在真正的业务回调线程里。

### 第 3 步：再看火焰图

如果有 `async-profiler`：

```bash
./profiler.sh -e cpu -d 15 -f cpu.html <pid>
```

通常会看到最宽的方法栈集中在：

- bucket 遍历
- 本地空轮询
- 无意义的解析或日志拼装

## 四、根因

根因不是“任务真的太多”，而是：

- 本地队列空时没有退避
- scanner 线程 busy spin
- 空扫时还带了额外 payload 检查
- 线程模型把“调度精度”错误地理解成“永远不能停一下”

## 五、解决过程

### 1. 先止血

- 降低扫描频率
- 下调预取批次
- 扩实例摊薄当前热点

### 2. 根因治理

- 空队列时 `sleep / parkNanos / condition wait`
- 按下一次最早触发时间阻塞等待
- 扫描和执行双线程池隔离
- 给 empty scan 次数补监控和告警

## 六、沉淀过程

1. 把 `top -Hp -> jstack -> 火焰图` 固化成排查 SOP
2. 把 empty scan 次数列入调度看板
3. 评审时明确禁止无退避的空转循环

## 七、1 分钟面试回答版

> 我在 ScheduleCenter 里遇到过一次比较典型的 CPU 高问题。场景是为了保秒级调度精度，我们有 scanner 线程持续扫未来 bucket。某个版本为了避免漏扫，队列空时也不阻塞，结果 scanner 线程一直空转，还顺带做了一些 payload 检查，最终一个线程就能把单核打得很高。排查时我先用 `top -Hp` 找到热点线程，再把 tid 转成 nid，用 `jstack` 看线程栈，确认热点都落在 scanLoop 和 pollBucket 上；后面如果环境允许，再用 `async-profiler` 抓 CPU 火焰图验证。处理上我们先降批次和扩容止血，后面把空队列等待、下一触发点阻塞、双线程池隔离和 empty scan 指标都补上了。
