-- 说明：
-- 1. 这份脚本演示如何在你本机已有的 MySQL 上开启慢查询日志。
-- 2. 需要较高权限；如果你的账号没有权限，可以让 DBA 帮你设置。
-- 3. SET GLOBAL 重启后可能失效；SET PERSIST 在支持的版本中可持久化。

SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
SHOW VARIABLES LIKE 'slow_query_log_file';
SHOW VARIABLES LIKE 'log_output';
SHOW VARIABLES LIKE 'min_examined_row_limit';

SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0.05;
SET GLOBAL log_output = 'FILE';
SET GLOBAL min_examined_row_limit = 100;

-- 如果你的 MySQL 版本支持，也可以尝试持久化：
-- SET PERSIST slow_query_log = 'ON';
-- SET PERSIST long_query_time = 0.05;
-- SET PERSIST log_output = 'FILE';
-- SET PERSIST min_examined_row_limit = 100;

SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
SHOW VARIABLES LIKE 'slow_query_log_file';
SHOW VARIABLES LIKE 'log_output';
SHOW VARIABLES LIKE 'min_examined_row_limit';

-- 用这个 SQL 验证 slow log 是否真的开始记录。
SELECT SLEEP(0.3);
