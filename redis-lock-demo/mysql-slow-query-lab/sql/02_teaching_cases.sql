USE xtimer_slow_demo;

-- =========================
-- 0. 先验证 slow log 是否真的生效
-- =========================

SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
SHOW VARIABLES LIKE 'slow_query_log_file';
SHOW VARIABLES LIKE 'log_output';

SELECT SLEEP(0.3);

-- =========================
-- 1. 案例一：对 run_timer 做函数计算
-- =========================

SELECT 'case-1 bad-sql: function on indexed run_timer' AS step;

EXPLAIN ANALYZE
SELECT task_id, timer_id, run_timer, status
FROM timer_task
WHERE FROM_UNIXTIME(run_timer / 1000, '%Y-%m-%d %H:%i') = '2025-03-05 10:15'
ORDER BY run_timer DESC
LIMIT 50;

SELECT 'case-1 better-sql: rewrite to pure range condition' AS step;

EXPLAIN ANALYZE
SELECT task_id, timer_id, run_timer, status
FROM timer_task
WHERE run_timer >= UNIX_TIMESTAMP('2025-03-05 10:15:00') * 1000
  AND run_timer < UNIX_TIMESTAMP('2025-03-05 10:16:00') * 1000
ORDER BY run_timer DESC
LIMIT 50;

-- =========================
-- 2. 案例二：xtimer fallback 查询缺联合索引
-- 对齐 TriggerTimerTask.getTasksByTime(...) 在 Redis 异常时调用的
-- TaskMapper.getTasksByTimeRange(startTime, endTime - 1, TaskStatus.NotRun)
-- 这里额外补 ORDER BY run_timer LIMIT 500，模拟线上止血时的有序、限量回扫
-- =========================

SET @drop_idx_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'timer_task'
              AND index_name = 'idx_status_run_timer'
        ),
        'ALTER TABLE timer_task DROP INDEX idx_status_run_timer',
        'SELECT ''idx_status_run_timer not exists, skip drop'''
    )
);
PREPARE drop_stmt FROM @drop_idx_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

SELECT 'case-2 before-fix: TriggerTimerTask fallback query without idx_status_run_timer' AS step;

EXPLAIN ANALYZE
SELECT task_id, timer_id, run_timer, status
FROM timer_task
WHERE status = 0
  AND run_timer >= UNIX_TIMESTAMP('2025-03-05 10:15:00') * 1000
  AND run_timer <= UNIX_TIMESTAMP('2025-03-05 10:15:59') * 1000
ORDER BY run_timer
LIMIT 500;

ALTER TABLE timer_task ADD INDEX idx_status_run_timer (status, run_timer);
ANALYZE TABLE timer_task;

SELECT 'case-2 after-fix: TriggerTimerTask fallback query with idx_status_run_timer(status, run_timer)' AS step;

EXPLAIN ANALYZE
SELECT task_id, timer_id, run_timer, status
FROM timer_task
WHERE status = 0
  AND run_timer >= UNIX_TIMESTAMP('2025-03-05 10:15:00') * 1000
  AND run_timer <= UNIX_TIMESTAMP('2025-03-05 10:15:59') * 1000
ORDER BY run_timer
LIMIT 500;

-- =========================
-- 3. 案例三：执行去重查询缺联合索引
-- 对齐 ExecutorWorker.work(timerIDUnixKey) 中的
-- taskMapper.getTasksByTimerIdUnix(timerId, runTimer)
-- =========================

SET @drop_dup_idx_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'timer_task'
              AND index_name = 'idx_timer_id_run_timer'
        ),
        'ALTER TABLE timer_task DROP INDEX idx_timer_id_run_timer',
        'SELECT ''idx_timer_id_run_timer not exists, skip drop'''
    )
);
PREPARE drop_dup_stmt FROM @drop_dup_idx_sql;
EXECUTE drop_dup_stmt;
DEALLOCATE PREPARE drop_dup_stmt;

SET @sample_run_timer = (
    SELECT run_timer
    FROM timer_task
    WHERE timer_id = 1024
    ORDER BY run_timer
    LIMIT 1
);

SELECT 'case-3 before-fix: ExecutorWorker duplicate check without idx_timer_id_run_timer' AS step;

EXPLAIN ANALYZE
SELECT *
FROM timer_task
WHERE timer_id = 1024
  AND run_timer = @sample_run_timer;

ALTER TABLE timer_task ADD INDEX idx_timer_id_run_timer (timer_id, run_timer);
ANALYZE TABLE timer_task;

SELECT 'case-3 after-fix: ExecutorWorker duplicate check with idx_timer_id_run_timer(timer_id, run_timer)' AS step;

EXPLAIN ANALYZE
SELECT *
FROM timer_task
WHERE timer_id = 1024
  AND run_timer = @sample_run_timer;

-- =========================
-- 4. 案例四：启用定时器扫描缺联合索引
-- 对齐 TimerMapper.getTimersByStatus(status) 的平台检索扩展版
-- =========================

SET @drop_timer_idx_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'xtimer'
              AND index_name = 'idx_status_app_modify_time'
        ),
        'ALTER TABLE xtimer DROP INDEX idx_status_app_modify_time',
        'SELECT ''idx_status_app_modify_time not exists, skip drop'''
    )
);
PREPARE drop_timer_stmt FROM @drop_timer_idx_sql;
EXECUTE drop_timer_stmt;
DEALLOCATE PREPARE drop_timer_stmt;

SELECT 'case-4 before-fix: getTimersByStatus-style scan without idx_status_app_modify_time' AS step;

EXPLAIN ANALYZE
SELECT timer_id, app, status, modify_time
FROM xtimer
WHERE status = 2
  AND app = 'treasury-center'
ORDER BY modify_time DESC
LIMIT 100;

ALTER TABLE xtimer ADD INDEX idx_status_app_modify_time (status, app, modify_time);
ANALYZE TABLE xtimer;

SELECT 'case-4 after-fix: getTimersByStatus-style scan with idx_status_app_modify_time(status, app, modify_time)' AS step;

EXPLAIN ANALYZE
SELECT timer_id, app, status, modify_time
FROM xtimer
WHERE status = 2
  AND app = 'treasury-center'
ORDER BY modify_time DESC
LIMIT 100;
