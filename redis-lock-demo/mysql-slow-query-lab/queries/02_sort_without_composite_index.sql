SELECT 'before_fix: missing composite index for status + amount' AS step;

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

SELECT 'after_fix: same SQL after adding idx_status_amount(status, amount)' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, amount, created_at
FROM orders
WHERE status = 'PAID'
ORDER BY amount DESC
LIMIT 50;
