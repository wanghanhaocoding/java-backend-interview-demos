# MySQL 慢查询排查实验室

这个目录现在按**本机已有 MySQL** 来设计。

也就是说：

- 你不用改当前 Spring Boot 项目依赖
- 你不用必须用 Docker
- 你只要有一个本地 MySQL，就能直接拿这里的 SQL 和讲义练习

---

## 你最先看这 3 个文件就够了

### 1. 开慢日志示例

- `sql/00_enable_slow_log_example.sql`

作用：

- 演示怎么开启 slow query log
- 演示怎么验证 slow log 真的生效

### 2. 初始化数据

- `sql/01_init_data.sql`

作用：

- 创建 `slow_demo` 数据库
- 初始化 `orders` 表（约 20 万行）
- 初始化 `users_demo` 表（约 5 万行）

### 3. 教学案例合集

- `sql/02_teaching_cases.sql`

作用：

- 一次性演示 4 类典型慢查询/坏写法
- 带你观察执行计划和优化动作

---

## 推荐使用顺序

### 第一步：先开 slow log

先执行：

```sql
SOURCE mysql-slow-query-lab/sql/00_enable_slow_log_example.sql;
```

### 第二步：初始化数据

执行：

```sql
SOURCE mysql-slow-query-lab/sql/01_init_data.sql;
```

### 第三步：跑教学案例

执行：

```sql
SOURCE mysql-slow-query-lab/sql/02_teaching_cases.sql;
```

---

## 这套案例包含什么

### 案例 1：索引列上套函数

示例：

```sql
WHERE DATE(created_at) = '2025-03-01'
```

教学目标：

- 理解为什么函数会让索引更难按范围查找
- 学会把它改写成范围查询

### 案例 2：`WHERE + ORDER BY` 缺联合索引

示例：

```sql
WHERE status = 'PAID'
ORDER BY amount DESC
LIMIT 50
```

教学目标：

- 理解为什么不是单看 `WHERE`，而要一起看 `WHERE + ORDER BY + LIMIT`
- 学会什么时候该补联合索引

### 案例 3：`LIKE '%xxx'`

示例：

```sql
WHERE remark LIKE '%VIP%'
```

教学目标：

- 理解为什么前导 `%` 对普通索引不友好

### 案例 4：隐式类型转换

示例：

```sql
WHERE phone = 13800000003
```

教学目标：

- 理解为什么列类型和参数类型不一致会埋坑

---

## 如何配合讲义一起学

再看这份：

- `TEACHING_GUIDE.md`

它讲的是：

- 怎么教
- 怎么排查
- 每一步该看什么证据

---

## 如果你的 MySQL 不支持 `EXPLAIN ANALYZE`

那就把 SQL 里的：

```sql
EXPLAIN ANALYZE
```

改成：

```sql
EXPLAIN
```

功能会弱一点，但仍然能练基本排查思路。

---

## 目录里其他文件说明

目录里还保留了之前的：

- `docker-compose.yml`
- `conf/my.cnf`
- `queries/`

这些属于**可选参考材料**。

如果你已经有本机 MySQL，可以先忽略它们，直接使用 `sql/` 下面的新脚本即可。
