# jvm-stability-interview-demo

一个专门为**面试复盘**准备的教学项目，聚焦三类高频线上稳定性问题：

1. `OOM`（OutOfMemoryError）
2. `Full GC` 频繁导致系统抖动
3. `死锁`（Deadlock）

这个项目不是在还原某个真实生产仓库，而是**结合你的简历背景做的“可讲述版案例抽象”**：

- `ScheduleCenter`：高精度定时调度中心
- `AsyncJobCenter`：异步任务中心
- `司库信息系统`：资金计划、预算、网银指令、回执处理等核心链路

目标不是单纯给你一堆概念，而是把这 3 类问题都拆成：

- **出现过程**
- **排查过程**
- **解决过程**
- **沉淀过程**
- **面试时怎么说**

---

## 你最先看这 4 个文件就够了

1. `docs/oom-case.md`
2. `docs/full-gc-case.md`
3. `docs/deadlock-case.md`
4. `docs/interview-cheatsheet.md`

如果你想再结合代码理解，可以继续看：

- `oom/OomLeakDemo.java`
- `oom/ScheduleCenterTaskStormSimulator.java`
- `oom/LeakyScheduleSnapshotBuffer.java`
- `oom/ScheduleTaskSnapshot.java`
- `fullgc/FullGcPressureDemo.java`
- `deadlock/DeadlockDemo.java`

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
└── support/
    └── CaseStoryLibrary.java

src/test/java/com/example/jvmstabilitydemo/
└── CaseStudySanityTest.java

docs/
├── oom-case.md
├── full-gc-case.md
├── deadlock-case.md
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
java -Xms256m -Xmx256m -Xlog:gc* -cp target/classes com.example.jvmstabilitydemo.fullgc.FullGcPressureDemo --run
```

### 5. 运行死锁示例

```bash
mvn -q -DskipTests compile
java -cp target/classes com.example.jvmstabilitydemo.deadlock.DeadlockDemo
```

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
- 对应：`ScheduleCenter`
- 关键词：触发失败、本地补偿快照、fallback snapshot、多索引强引用

### Full GC
- 对应：`ScheduleCenter`
- 关键词：时间窗预取、本地缓冲、秒级调度、批量扫描

### 死锁
- 对应：`司库信息系统 / AsyncJobCenter`
- 关键词：任务状态流转、补偿线程、锁顺序
