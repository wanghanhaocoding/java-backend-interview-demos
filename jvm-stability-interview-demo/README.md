# jvm-stability-interview-demo

一个专门为**面试复盘**准备的教学项目，聚焦四类高频线上稳定性问题：

当前这版已经调整为 **JDK 8 可编译、可运行**，适合放到公司仍在使用 `JDK8` 的环境里演练。

1. `OOM`（OutOfMemoryError）
2. `Full GC` 频繁导致系统抖动
3. `死锁`（Deadlock）
4. `线程定位`（Thread dump / jstack / jcmd）

这个项目现在优先对齐你简历中的第一个项目，也就是 **ScheduleCenter / bitstorm-svr-xtimer** 这条真实调度链路：

- `MigratorWorker`：先把未来时间窗任务迁移到 Redis `ZSet`
- `SchedulerWorker`：`@Scheduled(fixedRate = 1000)` 每秒提交分钟分片
- `SchedulerTask.asyncHandleSlice`：抢到分布式锁后进入分片处理
- `TriggerWorker.work -> TriggerTimerTask -> TriggerPoolTask`：按秒扫描 `minuteBucketKey` 并触发任务
- `TaskCache / TaskMapper / ExecutorWorker`：Redis `rangeByScore`、DB fallback、执行回调

这一版的重点不是泛泛讲 JVM，而是把真实项目里最容易被追问的稳定性链路讲清楚：

- 在 `xtimer` 这类调度中心里，通常**先暴露出来的不是直接 OOM，而是 Full GC 频繁**
- 原因是外层每秒持续提交分片，但单个分片扫描任务生命周期接近 `60s`
- 一旦 `schedulerPool` / `triggerPool` 吞吐跟不上，再叠加 Redis 抖动、DB fallback、任务对象和回调上下文，老年代就会先被顶高
- 所以真实现场更像：`Old 区升高 -> Full GC 频繁 -> 调度 RT 抖动 -> 任务触发延迟 -> 继续恶化后才到积压型 OOM`

目标不是单纯给你一堆概念，而是把这 4 类问题都拆成：

- **出现过程**
- **排查过程**
- **解决过程**
- **沉淀过程**
- **面试时怎么说**

---

## 你最先看这 5 个文件就够了

1. `docs/full-gc-case.md`
2. `docs/oom-case.md`
3. `docs/deadlock-case.md`
4. `docs/thread-troubleshooting-case.md`
5. `docs/interview-cheatsheet.md`

如果你要把这个 demo 真正部署到 Linux 服务器上，一步一步演练线上故障，再先看：

- `docs/linux-server-lab-roadmap.md`
- `docs/linux-server-oom-lab.md`
- `docs/linux-server-deadlock-lab.md`
- `scripts/run-oom-lab.sh`

如果你想再结合代码理解，可以继续看：

- `oom/OomLeakDemo.java`
- `oom/ScheduleCenterTaskStormSimulator.java`
- `oom/LeakyScheduleSnapshotBuffer.java`
- `oom/ScheduleTaskSnapshot.java`
- `fullgc/FullGcPressureDemo.java`
- `deadlock/DeadlockDemo.java`
- `thread/ThreadTroubleshootingDemo.java`

---

## 项目结构

```text
src/main/java/com/example/jvmstabilitydemo/
├── JvmStabilityInterviewApplication.java
├── oom/
│   └── OomLeakDemo.java
├── fullgc/
│   └── FullGcPressureDemo.java
├── deadlock/
│   └── DeadlockDemo.java
├── thread/
│   └── ThreadTroubleshootingDemo.java
└── support/
    └── CaseStoryLibrary.java

src/test/java/com/example/jvmstabilitydemo/
├── CaseStudySanityTest.java
└── thread/
    └── ThreadTroubleshootingDemoTest.java

docs/
├── oom-case.md
├── full-gc-case.md
├── deadlock-case.md
├── thread-troubleshooting-case.md
└── interview-cheatsheet.md
```

---

## 如何运行

### 1. 运行测试

```bash
mvn test
```

### 2. 运行总入口

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.example.jvmstabilitydemo.JvmStabilityInterviewApplication
```

### 3. 运行 OOM 示例

```bash
mvn -q -DskipTests compile
java -Xms128m -Xmx128m -cp target/classes com.example.jvmstabilitydemo.oom.OomLeakDemo --run
```

### 4. 运行 Full GC 示例

```bash
mvn -q -DskipTests compile
java -Xms256m -Xmx256m -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log -cp target/classes com.example.jvmstabilitydemo.fullgc.FullGcPressureDemo --run
```

### 5. 运行死锁示例

```bash
mvn -q -DskipTests compile
java -cp target/classes com.example.jvmstabilitydemo.deadlock.DeadlockDemo
```

### 6. 运行线程定位示例

```bash
mvn -q -DskipTests compile
java -cp target/classes com.example.jvmstabilitydemo.thread.ThreadTroubleshootingDemo --run
```

### 7. 在 Linux 服务器上按步骤演练 OOM

```bash
chmod +x scripts/run-oom-lab.sh
./scripts/run-oom-lab.sh preview
./scripts/run-oom-lab.sh run
```

更完整的步骤见：

- `docs/linux-server-oom-lab.md`
- `docs/linux-server-lab-roadmap.md`

### 8. 在 Linux 服务器上手工演练死锁

```bash
mvn -q -DskipTests compile
java -cp target/classes com.example.jvmstabilitydemo.deadlock.DeadlockDemo --hold-seconds=60
```

更完整的步骤见：

- `docs/linux-server-deadlock-lab.md`
- `docs/linux-server-lab-roadmap.md`
---

## 推荐面试表达顺序

1. **业务背景**
2. **故障现象**
3. **排查路径**
4. **根因**
5. **修复动作**
6. **沉淀**

---

## 这套案例和你的简历怎么对应

### OOM
- 对应：`ScheduleCenter / bitstorm-svr-xtimer`
- 关键词：先 Full GC 再恶化、触发失败、本地补偿快照、fallback snapshot、多索引强引用、积压型 OOM

### Full GC
- 对应：`ScheduleCenter / bitstorm-svr-xtimer`
- 关键词：`fixedRate=1s`、`5 bucket * 2 window`、分片长扫描、`schedulerPool/triggerPool` 深队列、`rangeByScore`、`taskMapper.getTasksByTimeRange`

### 死锁
- 对应：`ScheduleCenter / xtimer`，也可以延展到 `司库信息系统 / AsyncJobCenter`
- 关键词：`timer_task`、`xtimer`、任务状态流转、补偿线程、停用定时器、锁顺序

### 线程定位
- 对应：`AsyncJobCenter / ScheduleCenter`
- 关键词：线程 dump、线程名、线程状态、方法栈、`jstack / jcmd`
