# Linux 服务器实操路线

这份路线图是给“空 Linux 云服务器从零开始做线上异常实操”的版本。

建议严格按顺序走，不要一上来同时跑多个场景。

## 第 1 步：OOM

- 所在模块：`jvm-stability-interview-demo`
- 入口类：`com.example.jvmstabilitydemo.oom.OomLeakDemo`
- 目标：学会从 `内存上涨 -> Full GC 回落有限 -> OOM -> heap dump` 这一整条证据链定位问题
- 实操文档：`docs/linux-server-oom-lab.md`

## 第 2 步：Full GC

- 所在模块：`jvm-stability-interview-demo`
- 入口类：`com.example.jvmstabilitydemo.fullgc.FullGcPressureDemo`
- 目标：学会从 `老年代高 -> GC 频繁 -> RT 抖动` 反推批量预取和本地缓冲问题
- 实操文档：`schedule-center-fullgc-demo/docs/linux-server-full-gc-lab.md`

## 第 3 步：死锁

- 所在模块：`jvm-stability-interview-demo`
- 入口类：`com.example.jvmstabilitydemo.deadlock.DeadlockDemo`
- 目标：学会用 `jstack` 或 `jcmd Thread.print` 找到互相等待的锁
- 实操文档：`docs/linux-server-deadlock-lab.md`

## 第 4 步：CPU 飙高

- 所在模块：`cpu-high-troubleshooting-demo`
- 入口类：
  - `com.example.cpuhightroubleshootingdemo.cpu.XtimerEmptyScanCpuDemo`
  - `com.example.cpuhightroubleshootingdemo.cpu.XtimerFallbackStormCpuDemo`
- 目标：学会从 `top -Hp -> tid 转 nid -> jstack` 走到热点线程
- 实操文档：
  - `cpu-high-troubleshooting-demo/docs/linux-xtimer-empty-scan-runbook.md`
  - `cpu-high-troubleshooting-demo/docs/linux-xtimer-fallback-query-storm-runbook.md`

## 第 5 步：慢 SQL

- 所在目录：`redis-lock-demo/mysql-slow-query-lab`
- 目标：学会从 `slow log -> explain -> 索引设计` 走完慢查询排查

## 每一步都要交付什么

每个场景都尽量自己整理出这 5 项：

1. 现象
2. 复现命令
3. 排查命令
4. 根因
5. 面试回答

如果你按这个顺序走，现在第 1 步到第 5 步都已经有对应的 runbook 或实验资料，可以按模块逐个演练并整理这 5 项交付物。
