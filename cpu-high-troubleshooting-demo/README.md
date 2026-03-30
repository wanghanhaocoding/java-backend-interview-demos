# cpu-high-troubleshooting-demo

一个专门讲**线上 CPU 标高排查**的教学项目。

这个 demo 不做泛泛的 JVM 八股，而是直接贴你简历里的两条主线来讲：

1. `ScheduleCenter` 的空转扫描为什么会把 CPU 打高
2. `AsyncJobCenter` 的失败重试风暴为什么会制造热点线程
3. 线上 CPU 高时，怎么从 `top -Hp` 一路定位到 Java 代码
4. 排查后先怎么止血，再怎么做长期治理

---

## 这个项目讲什么

对应代码：

- `cpu/CpuHighTroubleshootingDemoService.java`
- `cpu/BusySpinScheduleScannerDemo.java`
- `cpu/AsyncRetryStormCpuDemo.java`

会直接演示：

- `ScheduleCenter` 的 scanner 线程空扫 bucket，形成 busy spin
- `AsyncJobCenter` 失败后立即重试，少量 jobId 高频空转
- `top -Hp -> printf '%x' -> jstack -> async-profiler` 的排查路径
- 为什么 CPU 高不一定等于流量真高，也可能是循环模型出了问题

---

## 你最先看这 4 个文件就够了

1. `docs/schedule-center-busy-spin-case.md`
2. `docs/async-job-retry-storm-case.md`
3. `docs/diagnosis-playbook.md`
4. `docs/interview-cheatsheet.md`

如果你想再结合代码理解，可以继续看：

- `cpu/CpuHighTroubleshootingDemoService.java`
- `cpu/BusySpinScheduleScannerDemo.java`
- `cpu/AsyncRetryStormCpuDemo.java`

---

## 这个项目怎么学

建议按这个顺序看：

1. `CpuHighTroubleshootingDemoService`
2. `demo/DemoRunner.java`
3. `BusySpinScheduleScannerDemo`
4. `AsyncRetryStormCpuDemo`
5. 两个测试类

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. `ScheduleCenter` 空转扫描案例
2. `AsyncJobCenter` 重试风暴案例
3. 一套通用 CPU 标高排查 SOP

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `CpuHighTroubleshootingDemoTest`
- `CpuHotSpotPreviewTest`

---

## 如何本机真实复现热点线程

### 1. 先编译

```bash
mvn -q -DskipTests compile
```

### 2. 跑 ScheduleCenter 空转扫描热点

```bash
java -cp target/classes com.example.cpuhightroubleshootingdemo.cpu.BusySpinScheduleScannerDemo --run 20
```

### 3. 跑 AsyncJobCenter 重试风暴热点

```bash
java -cp target/classes com.example.cpuhightroubleshootingdemo.cpu.AsyncRetryStormCpuDemo --run 20
```

上面两个命令都会打印当前 `pid`，你可以在 Linux 机器上另开终端继续执行：

```bash
top -Hp <pid>
printf '%x\n' <tid>
jstack <pid> | grep -A 20 <nid>
```

如果环境里有 `async-profiler`，再补一轮：

```bash
./profiler.sh -e cpu -d 15 -f cpu.html <pid>
```

---

## 面试里怎么说最稳

### 1. 线上 CPU 高先看什么？

> 我不会先猜 GC 或机器太小，而是先确认是不是 Java 进程本身在高，再找出最热线程，最后把线程栈和业务日志对应起来。

### 2. 什么样的代码最容易把 CPU 打高？

> 最常见的不是“业务量真的大”，而是空转循环、无退避重试、过度序列化、锁竞争和线程池模型不合理。

### 3. 怎么把这个问题讲得像真实项目？

> 最稳的讲法是挂在具体业务线程上，比如 ScheduleCenter 的 scanner 空扫 bucket，或者 AsyncJobCenter 的 retry dispatcher 立刻重试，把 CPU 打高。这样排查链路和修复动作都会更像真的生产问题。
