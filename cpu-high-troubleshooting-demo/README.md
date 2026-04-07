# cpu-high-troubleshooting-demo

一个专门讲 **xtimer / ScheduleCenter 线上 CPU 标高排查** 的教学项目。

这版不再混用 `AsyncJobCenter` 案例，而是全部对齐你简历中的第一个项目：

- 项目：`ScheduleCenter 定时调度中心`
- 真实代码：`bitstorm-svr-xtimer`
- 真实角色：`SchedulerWorker / SchedulerTask / TriggerWorker / TriggerTimerTask / TaskCache / TaskMapper`

## 这个项目讲什么

对应代码：

- `cpu/CpuHighTroubleshootingDemoService.java`
- `cpu/XtimerEmptyScanCpuDemo.java`
- `cpu/XtimerFallbackStormCpuDemo.java`
- `cpu/CpuScenarioRuntimeService.java`
- `web/CpuScenarioController.java`

会直接演示两类更贴真实项目的 CPU 高场景：

1. `TriggerWorker / TriggerTimerTask` 对空 `minuteBucketKey` 持续做 `rangeByScore`，空扫把 CPU 打高
2. Redis 取数异常后持续走 `taskMapper.getTasksByTimeRange` 做 DB fallback，查询风暴把 CPU 打高

## 你最先看这 4 个文件就够了

1. `docs/xtimer-empty-scan-case.md`
2. `docs/xtimer-fallback-query-storm-case.md`
3. `docs/diagnosis-playbook.md`
4. `docs/interview-cheatsheet.md`

如果你想再结合代码理解，可以继续看：

- `cpu/CpuHighTroubleshootingDemoService.java`
- `cpu/XtimerEmptyScanCpuDemo.java`
- `cpu/XtimerFallbackStormCpuDemo.java`

如果你要在 Linux 机器上手工演练，直接看这 2 个 runbook：

- `docs/linux-xtimer-empty-scan-runbook.md`
- `docs/linux-xtimer-fallback-query-storm-runbook.md`

如果你想把它当成一个一直挂在服务器上的服务来跑，直接看：

- `web/CpuScenarioController.java`
- `cpu/CpuScenarioRuntimeService.java`
- `application.yml`

## 这个项目怎么学

建议按这个顺序看：

1. `CpuHighTroubleshootingDemoService`
2. `demo/DemoRunner.java`
3. `XtimerEmptyScanCpuDemo`
4. `XtimerFallbackStormCpuDemo`
5. 两个测试类

## 如何运行

### 1. 常驻服务模式

```bash
mvn spring-boot:run
```

现在这个模块会以 Spring Boot Web 服务的方式常驻，不会再像以前一样打印完就退出。

启动后可以直接用这些接口：

```bash
curl -s http://127.0.0.1:8080/api/cpu/status
curl -s http://127.0.0.1:8080/api/cpu/cases
curl -s -X POST "http://127.0.0.1:8080/api/cpu/scenarios/empty-scan/start?durationSeconds=0"
curl -s -X POST "http://127.0.0.1:8080/api/cpu/scenarios/fallback-storm/start?durationSeconds=0"
curl -s -X POST http://127.0.0.1:8080/api/cpu/scenarios/stop
curl -s http://127.0.0.1:8080/actuator/health
```

其中：

1. `durationSeconds=0` 表示一直跑，直到你手动 stop
2. `/api/cpu/status` 会返回当前热点线程、运行时长和计数器
3. `/actuator/health` 会暴露 `cpuScenario` 的当前状态明细

### 2. 直接打 jar 常驻

```bash
mvn -q -DskipTests package
java -jar target/cpu-high-troubleshooting-demo-0.0.1-SNAPSHOT.jar
```

如果你想一启动就自动打热点：

```bash
java -jar target/cpu-high-troubleshooting-demo-0.0.1-SNAPSHOT.jar \
  --demo.scenario.auto-start=empty-scan \
  --demo.scenario.duration-seconds=0
```

或者：

```bash
java -jar target/cpu-high-troubleshooting-demo-0.0.1-SNAPSHOT.jar \
  --demo.scenario.auto-start=fallback-storm \
  --demo.scenario.duration-seconds=0
```

### 3. 旧的命令行演练模式

老的类入口仍然保留，适合你在本机或 Linux 上单独演练某个热点线程。

启动后仍会打印：

1. xtimer 空 minuteBucketKey 扫描热点
2. xtimer DB fallback 查询风暴热点
3. 一套贴 xtimer 的 CPU 标高排查 SOP

## 如何运行测试

```bash
mvn test
```

重点看：

- `CpuHighTroubleshootingDemoTest`
- `CpuHotSpotPreviewTest`

## 如何本机真实复现热点线程

### 1. 先编译

```bash
mvn -q -DskipTests compile
```

### 2. 跑 xtimer 空扫热点

```bash
java -cp target/classes com.example.cpuhightroubleshootingdemo.cpu.XtimerEmptyScanCpuDemo --run 20
```

### 3. 跑 xtimer DB fallback 查询风暴热点

```bash
java -cp target/classes com.example.cpuhightroubleshootingdemo.cpu.XtimerFallbackStormCpuDemo --run 20
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

## 如何在 Linux 服务器上长期挂着

如果你的目标是把它当成一台服务器上的常驻演练项目，推荐直接用 `nohup + java -jar`：

```bash
mkdir -p lab-output/cpu-high-service
nohup java -jar target/cpu-high-troubleshooting-demo-0.0.1-SNAPSHOT.jar \
  --server.port=8080 \
  --demo.node-id=cpu-node-a \
  > lab-output/cpu-high-service/app.log 2>&1 &
```

然后按需启动某个热点场景：

```bash
curl -s -X POST "http://127.0.0.1:8080/api/cpu/scenarios/empty-scan/start?durationSeconds=0"
```

或者：

```bash
curl -s -X POST "http://127.0.0.1:8080/api/cpu/scenarios/fallback-storm/start?durationSeconds=0"
```

你也可以在启动时自动拉起：

```bash
nohup java -jar target/cpu-high-troubleshooting-demo-0.0.1-SNAPSHOT.jar \
  --server.port=8080 \
  --demo.node-id=cpu-node-a \
  --demo.scenario.auto-start=empty-scan \
  --demo.scenario.duration-seconds=0 \
  > lab-output/cpu-high-service/app.log 2>&1 &
```

## 面试里怎么说最稳

> 我会把 CPU 高问题挂在 xtimer 这条真实链路上讲，而不是泛泛说“线程空转”。因为在这个项目里，SchedulerWorker 每秒都在提交 minute bucket 分片，TriggerWorker 内部又会启动 Timer 持续扫描一分钟。如果空 minuteBucketKey 没有及时退避，或者 Redis 抖动后 TriggerTimerTask 一直走 taskMapper.getTasksByTimeRange 做 DB fallback，CPU 很容易先被 Timer 线程和查询构造打高。排查时我先用 `top -Hp` 找热点线程，再用 `jstack` 和火焰图确认是 empty scan 还是 fallback query，最后再结合 queueSize、Redis RT、DB RT 和 callback RT 收敛根因。
