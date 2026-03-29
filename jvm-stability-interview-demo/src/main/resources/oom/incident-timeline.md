# OOM 现场时间线（更贴近真实项目）

## 事故背景

- 系统：AsyncJobCenter worker
- 场景：银行回调处理 / 回单制作 / 预算分析异步任务
- 时间：工作日上午高峰

## 时间线

### 10:02
- 银行回调超时开始增多
- `receipt_make` 任务失败率上升

### 10:05
- worker 实例的 old 区占用快速抬升
- fallback snapshot 日志明显增多

### 10:08
- Full GC 次数开始增加
- 任务拉取 RT 抖动

### 10:11
- 个别 worker 出现 `java.lang.OutOfMemoryError: Java heap space`
- 实例被重启

### 10:15
- 临时扩容 + 降低单实例任务拉取量
- 摘掉异常任务类型止血

### 10:40
- 导出 heap dump
- MAT 分析定位到 `JobCallbackSnapshot` 和 `LeakyLocalRetrySnapshotBuffer`

### 当天下午
- 修复本地 fallback buffer 设计
- 失败上下文改轻量落库
- 补充监控和告警
