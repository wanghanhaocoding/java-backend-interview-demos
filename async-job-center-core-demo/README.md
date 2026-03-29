# async-job-center-core-demo

一个专门讲 `AsyncJobCenter` 核心骨架的教学项目。

这个 demo 直接对应你简历里的平台主线，重点回答：

1. 为什么要拆 `server + worker`
2. 三张核心表分别存什么
3. 任务从 `create -> hold -> execute -> set -> 下一阶段 -> 终态` 怎么流转
4. 为什么任务状态不能直接在 worker 本地改完了事

---

## 这个项目讲什么

对应代码：

- `core/AsyncJobCenterCoreDemoService.java`

会直接演示：

- `task_schedule_cfg`：存任务调度配置
- `task_pos`：存任务起止指针
- `task_info`：存真正的任务实例
- `server` 创建任务并落库
- `worker` 领任务并改成 `PROCESSING`
- 执行完一个阶段后回写下一阶段
- 再次领取后推进到终态 `SUCCESS`

---

## 这个项目怎么学

建议按这个顺序看：

1. `AsyncJobCenterCoreDemoService`
2. `demo/DemoRunner.java`
3. `AsyncJobCenterCoreDemoTest`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会顺序打印：

1. 初始化配置表和指针表
2. 创建任务
3. worker 领取并执行第一阶段
4. 回写下一阶段
5. 再次领取并完成终态

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `AsyncJobCenterCoreDemoTest`

---

## 面试里怎么说最稳

### 1. 为什么要拆 server 和 worker？

> 因为任务管理和任务执行不是一回事。server 负责配置、落库、调度入口；worker 负责拉取、执行、重试。拆开以后扩容和隔离都更容易做。

### 2. 为什么要有 `hold` 这一步？

> 因为多 worker 并发拉取时，如果没有先把任务占有为 `PROCESSING`，同一批任务很容易被重复消费。
