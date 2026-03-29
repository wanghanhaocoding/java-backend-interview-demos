USE slow_demo;

-- =========================
-- 0. 先验证 slow log 是否真的生效
-- =========================

SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
SHOW VARIABLES LIKE 'slow_query_log_file';
SHOW VARIABLES LIKE 'log_output';

SELECT SLEEP(0.3);

-- =========================
-- 1. 案例一：索引列上套函数
-- =========================

SELECT 'case-1 bad-sql: function on indexed column' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, status, created_at
FROM orders
WHERE DATE(created_at) = '2025-03-01'
ORDER BY created_at DESC
LIMIT 20;

SELECT 'case-1 better-sql: rewrite to range condition' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, status, created_at
FROM orders
WHERE created_at >= '2025-03-01 00:00:00'
  AND created_at < '2025-03-02 00:00:00'
ORDER BY created_at DESC
LIMIT 20;

-- =========================
-- 2. 案例二：WHERE + ORDER BY 缺联合索引
-- =========================

SELECT 'case-2 before-fix: missing composite index for status + amount' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, amount, created_at
FROM orders
WHERE status = 'PAID'
ORDER BY amount DESC
LIMIT 50;

SET @drop_idx_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'orders'
              AND index_name = 'idx_status_amount'
        ),
        'ALTER TABLE orders DROP INDEX idx_status_amount',
        'SELECT ''idx_status_amount not exists, skip drop'''
    )
);
PREPARE drop_stmt FROM @drop_idx_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

ALTER TABLE orders ADD INDEX idx_status_amount (status, amount);
ANALYZE TABLE orders;

SELECT 'case-2 after-fix: same sql after adding idx_status_amount(status, amount)' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, amount, created_at
FROM orders
WHERE status = 'PAID'
ORDER BY amount DESC
LIMIT 50;

-- =========================
-- 3. 案例三：LIKE 前导百分号
-- =========================

SELECT 'case-3 bad-sql: leading wildcard like' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, remark, created_at
FROM orders
WHERE remark LIKE '%VIP%'
ORDER BY created_at DESC
LIMIT 30;

SELECT 'case-3 compare-sql: prefix like' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, remark, created_at
FROM orders
WHERE remark LIKE 'VIP%'
ORDER BY created_at DESC
LIMIT 30;

-- =========================
-- 4. 案例四：隐式类型转换
-- =========================

SELECT 'case-4 good-sql: column type and parameter type consistent' AS step;

EXPLAIN
SELECT *
FROM users_demo
WHERE phone = '13800000003';

SELECT 'case-4 risky-sql: compare varchar column with numeric literal' AS step;

EXPLAIN
SELECT *
FROM users_demo
WHERE phone = 13800000003;

-- =========================
-- 5. 观察热点 SQL 摘要
-- =========================

SELECT
    DIGEST_TEXT,
    COUNT_STAR,
    ROUND(SUM_TIMER_WAIT / 1000000000000, 2) AS total_sec,
    ROUND(AVG_TIMER_WAIT / 1000000000, 2) AS avg_ms,
    SUM_ROWS_EXAMINED,
    SUM_ROWS_SENT
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'slow_demo'
ORDER BY AVG_TIMER_WAIT DESC
LIMIT 10;
