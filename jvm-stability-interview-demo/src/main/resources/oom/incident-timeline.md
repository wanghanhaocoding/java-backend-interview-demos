# OOM 现场时间线（更贴近调度中心项目）

## 事故背景

- 系统：ScheduleCenter trigger node
- 场景：分钟分片扫描 / 本地预取 / 失败任务补偿
- 时间：工作日上午高峰

## 时间线

### 10:02
- 下游执行链路超时开始增多
- `receipt_timeout_scan` 任务失败率上升

### 10:05
- trigger 节点的 old 区占用快速抬升
- fallback snapshot 日志明显增多

### 10:08
- Full GC 次数开始增加
- 任务拉取 RT 抖动

### 10:11
- 个别 worker 出现 `java.lang.OutOfMemoryError: Java heap space`
- 实例被重启

### 10:15
- 临时扩容 + 降低单实例预取量
- 摘掉异常 bucket 止血

### 10:40
- 导出 heap dump
- MAT 分析定位到 `ScheduleTaskSnapshot` 和 `LeakyScheduleSnapshotBuffer`

### 当天下午
- 修复本地 fallback buffer 设计
- 失败上下文改轻量落库
- 补充监控和告警
