# mysql-lock-mvcc-demo

一个专门讲 **MySQL 隔离级别 / MVCC / 行锁与间隙锁 / InnoDB 死锁** 的教学项目。

这个项目不是去模拟完整数据库，而是把 InnoDB 面试里最难讲清的那一段，拆成几个可以稳定跑测试的最小案例。

重点说明：

> 这套代码是 **Java 版教学抽象**，用于帮助你讲清核心机制，不是要替代真实 MySQL 行为的每个细节。

---

## 这个项目讲什么

### 1. 隔离级别 demo

对应代码：

- `isolation/IsolationLevelDemoService.java`

会直接演示：

- `READ COMMITTED` 下第二次读可能看到新提交的数据
- `REPEATABLE READ` 下同一事务内保持同一个快照
- 幻读为什么和“范围内新记录”有关

### 2. MVCC demo

对应代码：

- `mvcc/MvccDemoService.java`

会直接演示：

- 行版本链
- read view 如何决定能看到哪个版本
- 快照读和当前读的区别

### 3. 锁与死锁 demo

对应代码：

- `lock/LockingDemoService.java`

会直接演示：

- gap lock 为什么会拦住范围内插入
- next-key lock 背后的区间思路
- 两个事务锁顺序不一致时如何形成死锁

---

## 这个项目怎么学

建议按这个顺序看：

1. `IsolationLevelDemoService`
2. `MvccDemoService`
3. `LockingDemoService`
4. `demo/DemoRunner.java`
5. 各模块测试

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会按顺序打印这些案例：

1. `READ COMMITTED` 与 `REPEATABLE READ` 的对比
2. MVCC 版本链与 read view
3. gap lock 拦截插入
4. 死锁检测

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `IsolationLevelDemoTest`
- `MvccDemoTest`
- `LockingDemoTest`

---

## 面试里怎么说最稳

### 1. MVCC 是干什么的？

> MVCC 的目标是让读操作尽量不阻塞写。InnoDB 会给一行保留多个版本，快照读根据 read view 选择当前事务可见的那个版本。

### 2. `READ COMMITTED` 和 `REPEATABLE READ` 最大区别是什么？

> `READ COMMITTED` 每次读都生成新的 read view，所以第二次读可能看到别人的新提交；`REPEATABLE READ` 在事务第一次快照读时固定 read view，同一事务里重复读看到的是同一个历史版本。

### 3. gap lock 为什么会拦插入？

> 因为它锁的不是已有某一行，而是一个索引区间。只要新插入的值落在这个区间里，就要等当前事务结束。
