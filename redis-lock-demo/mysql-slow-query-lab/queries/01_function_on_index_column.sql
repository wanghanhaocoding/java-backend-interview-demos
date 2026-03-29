SELECT 'bad_sql: function on indexed column' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, status, created_at
FROM orders
WHERE DATE(created_at) = '2025-03-01'
ORDER BY created_at DESC
LIMIT 20;

SELECT 'better_sql: rewrite to range condition' AS step;

EXPLAIN ANALYZE
SELECT id, user_id, status, created_at
FROM orders
WHERE created_at >= '2025-03-01 00:00:00'
  AND created_at < '2025-03-02 00:00:00'
ORDER BY created_at DESC
LIMIT 20;
