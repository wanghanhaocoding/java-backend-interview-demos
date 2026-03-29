USE slow_demo;

DROP TABLE IF EXISTS orders;

CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    remark VARCHAR(255) NOT NULL,
    KEY idx_created_at (created_at),
    KEY idx_status_created_at (status, created_at),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO orders (user_id, status, amount, created_at, remark)
WITH digits AS (
    SELECT 0 AS d
    UNION ALL SELECT 1
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
    UNION ALL SELECT 5
    UNION ALL SELECT 6
    UNION ALL SELECT 7
    UNION ALL SELECT 8
    UNION ALL SELECT 9
),
seq AS (
    SELECT d5.d * 100000 + d4.d * 10000 + d3.d * 1000 + d2.d * 100 + d1.d * 10 + d0.d + 1 AS n
    FROM digits d0
    CROSS JOIN digits d1
    CROSS JOIN digits d2
    CROSS JOIN digits d3
    CROSS JOIN digits d4
    CROSS JOIN digits d5
)
SELECT
    1 + MOD(n, 5000) AS user_id,
    ELT(1 + MOD(n, 4), 'CREATED', 'PAID', 'CANCELLED', 'REFUNDED') AS status,
    ROUND((100 + MOD(n, 50000)) / 100, 2) AS amount,
    TIMESTAMP('2025-01-01 00:00:00') + INTERVAL MOD(n, 60 * 60 * 24 * 120) SECOND AS created_at,
    CASE
        WHEN MOD(n, 20) = 0 THEN CONCAT('VIP-order-', LPAD(n, 6, '0'))
        WHEN MOD(n, 33) = 0 THEN CONCAT('PROMO-order-', LPAD(n, 6, '0'))
        ELSE CONCAT('normal-order-', LPAD(n, 6, '0'))
    END AS remark
FROM seq
WHERE n <= 200000;

ANALYZE TABLE orders;
