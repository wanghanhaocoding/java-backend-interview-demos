USE xtimer_slow_demo;

SELECT 'bad_sql: function on indexed run_timer' AS step;

EXPLAIN ANALYZE
SELECT task_id, timer_id, run_timer, status
FROM timer_task
WHERE FROM_UNIXTIME(run_timer / 1000, '%Y-%m-%d %H:%i') = '2025-03-05 10:15'
ORDER BY run_timer DESC
LIMIT 50;

SELECT 'better_sql: rewrite to pure range condition' AS step;

EXPLAIN ANALYZE
SELECT task_id, timer_id, run_timer, status
FROM timer_task
WHERE run_timer >= UNIX_TIMESTAMP('2025-03-05 10:15:00') * 1000
  AND run_timer < UNIX_TIMESTAMP('2025-03-05 10:16:00') * 1000
ORDER BY run_timer DESC
LIMIT 50;
