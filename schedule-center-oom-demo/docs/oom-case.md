# OOM 案例：bitstorm-svr-xtimer 在双深队列积压后把堆打满

## 1. 这个问题为什么不是“简单内存泄漏”

它和普通泄漏最大的区别是：

- 对象不是永久不可回收
- 但对象来得太快、活得太久、流得太慢

xtimer 里的关键链路是：

1. `SchedulerWorker` 每秒提交 `10` 个分片任务
2. `SchedulerTask.asyncHandleSlice` 抢锁后同步进入 `TriggerWorker.work`
3. `TriggerWorker.work` 会把 scheduler 线程挂住接近 `60s`
4. `TriggerTimerTask` 每秒扫描 Redis `rangeByScore`
5. 扫到的任务再交给 `TriggerPoolTask` 异步投递
6. Redis 异常时走 `taskMapper.getTasksByTimeRange`

只要“提交速度 > 消费速度”，对象就会一直积压在两个位置：

- `schedulerPool` 队列
- `triggerPool` 队列

## 2. 用 xtimer 真实参数把压力算出来

### schedulerPool 这一层

真实口径：

- `5` 个 bucket
- 每秒两轮：上一分钟补偿 + 当前分钟实时
- 所以每秒提交分片数 = `10`
- 单个分片持有时间约 = `60s`
- `schedulerPool.maxPoolSize = 100`

那么：

- scheduler 稳态并发需求 = `10 * 60 = 600`
- 实际处理能力约 = `100 / 60 ≈ 1.67/s`
- 每分钟净积压约 = `(10 - 1.67) * 60 ≈ 500`

### triggerPool 这一层

为了模拟高峰期，每个活跃分片按平均每秒投递 `12` 个到期任务估算：

- trigger 到达速率 = `10 * 12 = 120/s`
- `triggerPool.maxPoolSize = 100`
- 假设平均回调耗时 = `2s`
- trigger 实际处理能力 = `100 / 2 = 50/s`
- trigger 每分钟净积压 = `(120 - 50) * 60 = 4200`

如果每个待执行任务连同上下文按 `18KB` 估算：

- trigger 单分钟新增保留内存约 = `4200 * 18KB ≈ 73.8MB`

再叠加 schedulerPool 里分片上下文、Redis 结果集、DB fallback 结果集和 Future，很快就会把堆顶高。

## 3. 为什么会先出现 Full GC，再走到 OOM

因为这是一个逐步恶化的过程：

1. 分片任务先堆在 `schedulerPool`
2. 回调慢了以后，任务对象继续堆到 `triggerPool`
3. Redis 正常时已经会堆 `TaskModel` 和集合对象
4. Redis 异常时 DB fallback 结果集更大，对象放大更明显
5. Young GC 逐渐回不掉这么多存活对象
6. 对象开始晋升到老年代
7. 先出现 Full GC 频繁
8. 再继续恶化，最终才是 OOM

所以它更像：

`Full GC -> Full GC 回收效果差 -> Old 区抬高 -> OOM`

而不是系统一上来就崩。

## 4. 我会怎么排查

### 第一步：先判断是不是积压

看这几个指标：

- `schedulerPool.queueSize`
- `triggerPool.queueSize`
- 每分钟提交数
- 每分钟完成数

如果队列持续单向增长，就说明是积压，不是瞬时高峰。

### 第二步：再判断是积压还是泄漏

积压型 OOM 的典型特征是：

- 流量下降或回调 RT 恢复后，内存和队列可能部分回落

而典型泄漏往往是：

- 负载降下来后，内存还是持续高位

常用命令：

```bash
jstat -gcutil <pid> 1000 20
jcmd <pid> GC.heap_info
jmap -histo:live <pid>
```

如果对象直方图里主要是：

- `ArrayList`
- `FutureTask`
- 任务 DTO
- 回调请求体
- 大量集合和上下文

再配合线程池队列增长，就更像积压放大。

### 第三步：专门看 Redis 和 fallback

这一步在 xtimer 里非常关键：

- Redis `rangeByScore` 是否超时或异常
- `taskMapper.getTasksByTimeRange` 是否在异常路径下拉出了过大结果集
- 回调下游 RT 是否把 triggerPool 卡死

因为这决定了问题是“本来就会积压”，还是“异常路径把积压再放大了一层”。

## 5. 我会怎么处理

### 先止血

1. 扩容节点，先把分片压力摊薄
2. 控制 trigger 投递速率，避免慢回调继续放大积压
3. 限制 DB fallback 结果集规模
4. 把 `queueCapacity=99999` 改成有限长度，让背压提前暴露

### 再根治

1. 抢锁前增加本机负载检查，不让饱和节点继续接分片
2. 缩短 `TriggerWorker.work` 这种长生命周期扫描任务
3. trigger 投递链路增加背压和限流
4. 给 Redis 异常路径加更严格的 fallback 保护
5. 联动监控队列长度、GC、fallback 命中率和 callback RT

## 6. 面试里怎么说

> 如果继续恶化，这类问题是可能进一步打到 OOM 的，但它更像积压型 OOM，不是典型代码泄漏。因为在 xtimer 里，外层 SchedulerWorker 每秒都在继续提交分片，而抢到锁后的 TriggerWorker 会把线程挂住近一分钟，导致 schedulerPool 和 triggerPool 的大队列里长期堆积任务对象、集合、Future 和回调上下文。如果 Redis 又异常，TriggerTimerTask 还会走 TaskMapper.getTasksByTimeRange 做 DB fallback，把结果集进一步放大。通常它不会一上来就 OOM，而是先出现 Full GC 越来越频繁、回收效果越来越差，最后老年代才被打满。处理重点不在于单纯加堆，而在于收住任务生产速度、缩短单任务生命周期、限制队列长度，并压住 fallback 放大量。 
