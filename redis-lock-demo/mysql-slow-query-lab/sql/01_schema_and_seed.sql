-- 兼容旧文件名，内容与 01_init_data.sql 对齐。
-- 如果你是第一次执行，优先直接使用：
-- SOURCE mysql-slow-query-lab/sql/01_init_data.sql;

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
