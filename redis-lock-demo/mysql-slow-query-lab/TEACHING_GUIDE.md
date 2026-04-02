# xtimer 慢查询教学方法

这份文档不是单纯给你看 SQL，而是告诉你：

> **怎么带着 xtimer 的真实链路去复现，怎么带着证据去排查，怎么带着对比去验证。**

配套 SQL：

- `sql/00_enable_slow_log_example.sql`
- `sql/01_init_data.sql`
- `sql/02_teaching_cases.sql`

## 一、推荐教学顺序

### 第 1 步：先确认环境

先看版本：

```sql
SELECT VERSION();
```

要点：

- MySQL 8.0.18+ 才支持 `EXPLAIN ANALYZE`
- 如果版本低，就用 `EXPLAIN`

### 第 2 步：先确认 slow log 生效

先执行：

```sql
SELECT SLEEP(0.3);
```

然后去看 slow log。

这一步你要能讲清楚：

> 如果慢日志都没开，后面“排查慢 SQL”这件事本身就是空的。

### 第 3 步：先做 `run_timer` 函数计算案例

这是最贴 xtimer 的入门案例，因为真实表 `timer_task.run_timer` 就是毫秒时间戳。

你要回答：

1. 为什么 `FROM_UNIXTIME(run_timer / 1000, ...)` 会把索引用坏？
2. 为什么改成 `run_timer between ...` 更合理？
3. 优化证据是什么？

### 第 4 步：再做 xtimer fallback 查询案例

这条 SQL 对应真实项目里的：

`taskMapper.getTasksByTimeRange(startTime, endTime - 1, TaskStatus.NotRun)`

要讲清楚：

> 这不是通用 SQL 优化题，而是调度中心 Redis miss 后的异常路径慢查询。

### 第 5 步：再做执行去重和启用定时器扫描

这两条更像真实生产：

- `timer_id + run_timer` 去重检查
- `status + app + modify_time` 的平台检索

### 第 6 步：把 Java 调用链和 SQL 先对起来

先把这 4 个真实代码点记住：

1. `TriggerWorker.work(minuteBucketKey)` 会创建 `TriggerTimerTask`
2. `TriggerTimerTask.getTasksByTime(start, end)` 先调用 `TaskCache.getTasksFromCache(...)`
3. Redis 异常时才 fallback 到 `taskMapper.getTasksByTimeRange(start, end - 1, TaskStatus.NotRun)`
4. `TriggerPoolTask.runExecutor(task)` 最终调到 `ExecutorWorker.work(timerIDUnixKey)`，再查 `taskMapper.getTasksByTimerIdUnix(timerId, runTimer)`

你要把实验室 SQL 对成下面这样：

- 案例 1：手工定位 `run_timer` 时间窗，帮助你看 `TriggerTimerTask` 正在扫哪一段时间
- 案例 2：对应 `TriggerTimerTask` 的 DB fallback；实验室额外补 `ORDER BY run_timer LIMIT 500`，因为线上止血必须控制回扫体积
- 案例 3：对应 `ExecutorWorker` 的去重检查，这条是最贴源码的一条
- 案例 4：对应 `TimerMapper.getTimersByStatus` 一类启用定时器扫描，再展开成平台检索 SQL

## 二、统一排查框架：5 步法

### 第一步：先定性，是不是 SQL 慢

不要一上来就加索引。

先问：

- 是不是 `taskMapper.getTasksByTimeRange` 慢？
- 是不是 `getTasksByTimerIdUnix` 慢？
- slow log 里有没有对应 SQL？

如果 slow log 根本没有，那问题可能是：

- 线程池排队
- Redis 抖动
- callback 下游慢
- 代码串行处理过多

### 第二步：看 slow log 的核心字段

至少盯住这 4 个：

1. `Query_time`
2. `Rows_examined`
3. `Rows_sent`
4. SQL 原文

你要形成这个直觉：

> `Rows_examined` 很大、`Rows_sent` 很小，通常说明扫描太多、过滤太晚、索引没用好。

### 第三步：看执行计划

优先：

```sql
EXPLAIN ANALYZE <SQL>;
```

不支持的话：

```sql
EXPLAIN <SQL>;
```

重点不是背字段，而是看：

- 扫描行数是否明显偏大
- 是否走了不合适的索引
- 是否存在额外排序
- 实际执行路径是不是和你的预期一致

### 第四步：归类问题

放到 xtimer 里，最常见的慢 SQL 基本都能归到这几类：

1. `run_timer` 上套函数
2. `status + run_timer` 缺联合索引
3. `timer_id + run_timer` 缺联合索引
4. 平台检索缺 `status + app + modify_time` 索引
5. 异常路径 fallback 结果集过大

### 第五步：优化后一定回归验证

至少再做 3 件事：

1. 再跑一次 `EXPLAIN ANALYZE`
2. 再看一次 slow log
3. 再看一次真实耗时

## 三、每个 xtimer 案例应该怎么讲

### 案例 1：`run_timer` 上套函数

核心不是“函数不能用”，而是：

> 对索引列先做函数计算，会让优化器更难按原始范围索引查找。

### 案例 2：fallback 查询缺联合索引

要讲清楚：

> `WHERE status = 0 AND run_timer between ... order by run_timer limit ...` 这种调度 SQL，本质上要同时考虑过滤、排序和限流，不是只给 `status` 或 `run_timer` 单列索引就够了。

补充一句：

> 真实 `TaskMapper.getTasksByTimeRange(...)` 在 XML 里只有 `run_timer + status` 条件；实验室把 `ORDER BY run_timer LIMIT 500` 明确写出来，是为了训练你在线上止血时主动控制回扫节奏。

### 案例 3：执行去重缺联合索引

要讲清楚：

> xtimer 的执行链路里，会按 `timer_id + run_timer` 判断任务是否已经执行过，这类查询天然适合联合索引。

### 案例 4：启用定时器扫描缺联合索引

要讲清楚：

> 平台型系统的管理侧查询，经常不是简单 `where status`，而是 `status + app + 排序字段` 组合使用。

### 补充：慢 SQL 线上止血动作怎么讲

做 runbook 时，不要只讲“最终索引方案”，还要讲临时止血动作：

1. 先限流。`TriggerPoolTask` 异步投递后，`ExecutorWorker.executeTimerCallBack(...)` 会继续放大下游压力，先把最热 callback 路由或最热 app 限流。
2. 缩小时间窗。手工执行 `taskMapper.getTasksByTimeRange` 等价 SQL 时，优先按单个 minuteBucketKey、单个 gap 秒窗口回扫，不要一次扫完整个 backlog。
3. 限制结果集。手工 fallback SQL 保留 `ORDER BY run_timer LIMIT N`，先用 100 或 500 做小批次，观察 slow log 和 callback 成功率后再推进。
4. 动作后复盘。每做完一次止血动作，都重新跑 `queries/03_digest_topn.sql`，确认热点 digest 有没有降下来。

## 四、建议你训练自己回答的 3 个问题

每做完一个案例，都回答：

1. 这条 SQL 为什么慢？
2. 问题属于“SQL 写法”还是“索引设计”？
3. 你拿什么证据证明它优化成功了？

## 五、最核心的一句话

这套练习最想让你形成的不是“背答案”，而是这个习惯：

> **先从 xtimer 的 slow log 发现问题，再用 `EXPLAIN ANALYZE` 找原因，最后用改 SQL / 调整索引的方式验证优化是否真的生效。**
