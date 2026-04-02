# xtimer MySQL 慢查询排查实验室

这个目录现在按 **ScheduleCenter / bitstorm-svr-xtimer** 的真实查询模式来设计。

也就是说：

- 不再用泛化的 `orders / users_demo`
- 直接用 `xtimer`、`timer_task` 两张表来练
- SQL 模式贴近你第一个项目里的扫描、fallback 和任务去重查询

## 你最先看这 3 个文件就够了

### 1. 开慢日志示例

- `sql/00_enable_slow_log_example.sql`

作用：

- 演示怎么开启 slow query log
- 演示怎么验证 slow log 真的生效

### 2. 初始化 xtimer 数据

- `sql/01_init_data.sql`

作用：

- 创建 `xtimer_slow_demo` 数据库
- 初始化 `xtimer` 表
- 初始化 `timer_task` 表（约 20 万行）

### 3. xtimer 教学案例合集

- `sql/02_teaching_cases.sql`

作用：

- 一次性演示 4 类更贴 xtimer 的慢查询
- 带你观察执行计划和优化动作

## 推荐使用顺序

### 第一步：先开 slow log

先执行：

```sql
SOURCE mysql-slow-query-lab/sql/00_enable_slow_log_example.sql;
```

### 第二步：初始化 xtimer 数据

执行：

```sql
SOURCE mysql-slow-query-lab/sql/01_init_data.sql;
```

### 第三步：跑 xtimer 教学案例

执行：

```sql
SOURCE mysql-slow-query-lab/sql/02_teaching_cases.sql;
```

## 这套案例包含什么

### 案例 1：对 `run_timer` 做函数计算

示例：

```sql
WHERE FROM_UNIXTIME(run_timer / 1000, '%Y-%m-%d %H:%i') = '2025-03-01 10:15'
```

教学目标：

- 理解为什么在索引列上做函数计算会让优化器更难走范围索引
- 学会把毫秒时间戳查询改成纯范围条件

### 案例 2：xtimer fallback 查询缺联合索引

示例：

```sql
WHERE status = 0
  AND run_timer >= ?
  AND run_timer <= ?
ORDER BY run_timer
LIMIT 500
```

教学目标：

- 对齐真实 `taskMapper.getTasksByTimeRange`
- 理解为什么 `status + run_timer` 更适合做联合索引

### 案例 3：执行去重查询缺联合索引

示例：

```sql
WHERE timer_id = ?
  AND run_timer = ?
```

教学目标：

- 对齐真实 `getTasksByTimerIdUnix`
- 理解为什么只给 `timer_id` 单列索引不够

### 案例 4：启用定时器扫描缺联合索引

示例：

```sql
WHERE status = 2
  AND app = 'treasury-center'
ORDER BY modify_time DESC
LIMIT 100
```

教学目标：

- 对齐 `getTimersByStatus` 和管理侧检索场景
- 理解为什么 `status + app + modify_time` 这类组合在平台场景里更有意义

## Java 调用链怎么落到这些 SQL

### 1. TriggerTimerTask 的 Redis miss / Redis 异常 fallback

真实代码入口：

- `TriggerWorker.work(minuteBucketKey)`
- `TriggerTimerTask.getTasksByTime(start, end)`
- `TaskCache.getTasksFromCache(minuteBucketKey, start, end)`

关键点：

- `TaskCache` 先走 Redis ZSET 的 `rangeByScore`
- 只有 Redis 抛异常时，`TriggerTimerTask` 才会 fallback 到 `taskMapper.getTasksByTimeRange(start, end - 1, TaskStatus.NotRun)`
- 实验室案例 2 用同一组 `status + run_timer` 条件，再额外补 `ORDER BY run_timer LIMIT 500`，模拟线上手工止血时常用的“有序、限量回扫”

### 2. ExecutorWorker 的执行去重链路

真实代码入口：

- `TriggerPoolTask.runExecutor(task)`
- `ExecutorWorker.work(timerIDUnixKey)`
- `taskMapper.getTasksByTimerIdUnix(timerId, runTimer)`

关键点：

- 这就是案例 3 对应的 SQL 原型
- 如果这条查询慢，重复执行校验会拖住 `triggerPool`，callback 的成功失败回写也会往后堆

### 3. 其他两个案例分别在讲什么

- 案例 1 是排查 `TriggerTimerTask` 时间窗时最常写错的 `run_timer` 手工定位 SQL
- 案例 4 对应 `TimerMapper.getTimersByStatus` 一类启用定时器扫描，再扩展成平台侧常见的 `status + app + modify_time` 检索

## 如何配合讲义一起学

再看这份：

- `TEACHING_GUIDE.md`

它讲的是：

- 怎么带着 xtimer 的业务背景去看 SQL
- 怎么从 slow log 走到执行计划
- 每一步该看什么证据

## 线上止血先做什么

1. 先限流 callback 链路
2. `TriggerPoolTask` 会把任务异步扔进 `triggerPool`，`ExecutorWorker.executeTimerCallBack(...)` 会直接打业务 HTTP。线上先把最热 app 或 callback 路由限流，避免 DB fallback 和下游超时互相放大。
3. 缩小回扫时间窗。`TriggerTimerTask` 本来就是按 `start ~ end` 小窗口扫，手工补查 `taskMapper.getTasksByTimeRange` 时，优先按单个 minuteBucketKey、单个 gap 秒窗口查，不要一把扫整分钟甚至整小时。
4. 限制 fallback 结果集。手工 SQL 一定保留 `ORDER BY run_timer LIMIT N`，先用 100 或 500 这种小批次把 backlog 切开，再根据 slow log 和 callback 成功率继续推进。

## 没本地 MySQL 就直接起容器

如果你本机没有可用 MySQL，可以直接在这个目录执行：

```bash
docker compose up -d
```

`docker-compose.yml` 已对齐 `MYSQL_DATABASE: xtimer_slow_demo`，并会自动挂载 `./sql` 初始化脚本。

## 如果你的 MySQL 不支持 `EXPLAIN ANALYZE`

那就把 SQL 里的：

```sql
EXPLAIN ANALYZE
```

改成：

```sql
EXPLAIN
```

功能会弱一点，但仍然能练基本排查思路。
