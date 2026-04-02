package com.example.jvmstabilitydemo.support;

public final class CaseStoryLibrary {

    private CaseStoryLibrary() {
    }

    public static String oomSummary() {
        return "在 ScheduleCenter / bitstorm-svr-xtimer 里，OOM 通常不是第一现场，而是 Full GC 频繁继续恶化后的积压型 OOM。"
            + "SchedulerWorker 每秒持续提交 minute bucket 分片，TriggerWorker.work 单分片又会扫接近一分钟，"
            + "再叠加 Redis rangeByScore、DB fallback taskMapper.getTasksByTimeRange、TaskModel 和回调上下文，"
            + "对象会长期滞留并把老年代慢慢打满。";
    }

    public static String fullGcSummary() {
        return "在 ScheduleCenter / xtimer 里，更先暴露出来的通常是 Full GC。因为 @Scheduled(fixedRate = 1000) 每秒都在继续提分片，"
            + "5 bucket * 2 个分钟窗口约等于 10 个分片/秒，但抢到锁后的 TriggerWorker.work 会持续接近 60 秒，"
            + "schedulerPool/triggerPool 深队列吞吐跟不上时，对象会逐步晋升老年代，先表现成 Full GC 频繁、RT 抖动和触发延迟。";
    }

    public static String deadlockSummary() {
        return "xtimer 里执行回调线程和停用定时器线程对 timer_task、xtimer 两类资源的加锁顺序不一致，"
            + "并发时形成环形等待。通过线程 dump 发现 Java 级死锁，最终通过统一锁顺序和 tryLock 降风险。";
    }

    public static String threadTroubleshootingSummary() {
        return "线程卡住时先拿线程 dump，按线程名 -> 线程状态 -> 方法栈的顺序回到代码。"
            + "像回调忙线程、锁阻塞线程、队列等待线程、调度休眠线程，在线程 dump 里的特征都不一样。";
    }
}
