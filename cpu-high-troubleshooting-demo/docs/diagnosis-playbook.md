# 线上 CPU 标高排查 SOP

## 一、排查目标

CPU 高这类问题，最容易犯的错是：

- 一上来猜 GC
- 一上来猜机器太小
- 一上来就改线程池参数

更稳的路径是：

> 先找到最热线程，再把线程热点映射回 Java 栈和业务链路。

## 二、标准步骤

### 1. 看机器或容器

先确认：

- 是不是 Java 进程本身在高
- 是单核高，还是多核同时高

### 2. 找热点线程

```bash
top -Hp <pid>
```

要记住：

- `pid` 是 Java 进程
- `tid` 是 Linux 线程 ID

### 3. 把 tid 转成 nid

```bash
printf '%x\n' <tid>
```

### 4. 导线程栈

```bash
jstack <pid> | grep -A 20 <nid>
```

或者：

```bash
jcmd <pid> Thread.print -l
```

### 5. 抓 CPU 火焰图

```bash
./profiler.sh -e cpu -d 15 -f cpu.html <pid>
```

火焰图的作用不是替代线程栈，而是：

- 确认哪条调用链最热
- 看清热点到底是循环、锁竞争、序列化还是框架代码

### 6. 结合业务证据收敛根因

至少把这些证据放在一起看：

- 热点线程名
- 线程栈
- 火焰图
- 线程池 active / queue / reject
- QPS / RT / error rate
- 同 jobId 或同 bucket 的重复命中情况

## 三、常见根因分类

### 1. 空转循环

典型关键词：

- `while (true)`
- 空队列轮询
- busy spin

### 2. 无退避重试

典型关键词：

- retry immediately
- 同一 jobId 高频失败
- 日志暴涨

### 3. 锁竞争

典型关键词：

- 自旋 CAS
- 热锁
- synchronized 热点

### 4. 过度序列化 / 反序列化

典型关键词：

- JSON parse
- payload rebuild
- log string build

## 四、止血和治理要分开

### 止血

- 限流
- 降批次
- 摘流量
- 扩容

### 治理

- 改循环模型
- 改重试退避
- 改线程池隔离
- 改监控和告警

## 五、最核心的一句话

CPU 高不是一个根因，它只是一个现象。

最稳的表达是：

> 我会先把热点线程和 Java 栈拿出来，再结合日志和线程池指标，看它到底是空转、重试、锁竞争还是序列化问题，然后再决定止血和治理动作。
