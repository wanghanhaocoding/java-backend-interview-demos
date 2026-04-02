DROP DATABASE IF EXISTS xtimer_slow_demo;
CREATE DATABASE xtimer_slow_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE xtimer_slow_demo;

DROP TABLE IF EXISTS timer_task;
DROP TABLE IF EXISTS xtimer;

CREATE TABLE xtimer (
    timer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status INT NOT NULL,
    cron VARCHAR(64) NOT NULL,
    notify_http_param VARCHAR(512) NOT NULL,
    create_time BIGINT NOT NULL,
    modify_time BIGINT NOT NULL,
    KEY idx_status (status),
    KEY idx_app (app)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE timer_task (
    task_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app VARCHAR(64) NOT NULL,
    timer_id BIGINT NOT NULL,
    output VARCHAR(512) NOT NULL,
    run_timer BIGINT NOT NULL,
    cost_time INT NOT NULL,
    status INT NOT NULL,
    create_time BIGINT NOT NULL,
    modify_time BIGINT NOT NULL,
    KEY idx_run_timer (run_timer),
    KEY idx_timer_id (timer_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO xtimer (app, name, status, cron, notify_http_param, create_time, modify_time)
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
    SELECT d3.d * 1000 + d2.d * 100 + d1.d * 10 + d0.d + 1 AS n
    FROM digits d0
    CROSS JOIN digits d1
    CROSS JOIN digits d2
    CROSS JOIN digits d3
)
SELECT
    ELT(1 + MOD(n, 4), 'treasury-center', 'budget-center', 'receipt-center', 'collect-center') AS app,
    CONCAT('timer-', LPAD(n, 5, '0')) AS name,
    CASE
        WHEN MOD(n, 10) < 7 THEN 2
        WHEN MOD(n, 10) = 7 THEN 1
        ELSE 3
    END AS status,
    '0/5 * * * * ?' AS cron,
    CONCAT('{\"url\":\"http://callback/xtimer/', n, '\",\"method\":\"POST\"}') AS notify_http_param,
    1740787200000 + n * 1000 AS create_time,
    1740787200000 + n * 2000 AS modify_time
FROM seq
WHERE n <= 5000;

INSERT INTO timer_task (app, timer_id, output, run_timer, cost_time, status, create_time, modify_time)
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
    ELT(1 + MOD(n, 4), 'treasury-center', 'budget-center', 'receipt-center', 'collect-center') AS app,
    1 + MOD(n, 5000) AS timer_id,
    CASE
        WHEN MOD(n, 15) = 0 THEN CONCAT('callback-timeout-', LPAD(n, 6, '0'))
        WHEN MOD(n, 11) = 0 THEN CONCAT('redis-fallback-', LPAD(n, 6, '0'))
        ELSE CONCAT('ok-', LPAD(n, 6, '0'))
    END AS output,
    1740787200000 + MOD(n, 60 * 60 * 24 * 30) * 1000 AS run_timer,
    10 + MOD(n, 900) AS cost_time,
    CASE
        WHEN MOD(n, 10) < 5 THEN 0
        WHEN MOD(n, 10) < 7 THEN 1
        WHEN MOD(n, 10) < 9 THEN 2
        ELSE 3
    END AS status,
    1740787200000 + n * 500 AS create_time,
    1740787200000 + n * 700 AS modify_time
FROM seq
WHERE n <= 200000;

ANALYZE TABLE xtimer;
ANALYZE TABLE timer_task;

SELECT COUNT(*) AS total_timers FROM xtimer;
SELECT COUNT(*) AS total_tasks FROM timer_task;
