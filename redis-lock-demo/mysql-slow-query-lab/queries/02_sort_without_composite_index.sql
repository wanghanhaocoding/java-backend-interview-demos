USE xtimer_slow_demo;

-- 对齐 TriggerTimerTask.getTasksByTime(...) 在 Redis 异常时调用的
-- TaskMapper.getTasksByTimeRange(startTime, endTime - 1, TaskStatus.NotRun)
-- 这里额外补 ORDER BY run_timer LIMIT 500，模拟线上止血时的有序、限量回扫

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

SELECT 'before_fix: TriggerTimerTask fallback query without idx_status_run_timer' AS step;

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

SELECT 'after_fix: TriggerTimerTask fallback query with idx_status_run_timer(status, run_timer)' AS step;

EXPLAIN ANALYZE
SELECT task_id, timer_id, run_timer, status
FROM timer_task
WHERE status = 0
  AND run_timer >= UNIX_TIMESTAMP('2025-03-05 10:15:00') * 1000
  AND run_timer <= UNIX_TIMESTAMP('2025-03-05 10:15:59') * 1000
ORDER BY run_timer
LIMIT 500;
