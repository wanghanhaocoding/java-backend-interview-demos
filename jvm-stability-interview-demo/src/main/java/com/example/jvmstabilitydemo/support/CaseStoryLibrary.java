package com.example.jvmstabilitydemo.support;

public final class CaseStoryLibrary {

    private CaseStoryLibrary() {
    }

    public static String oomSummary() {
        return "ScheduleCenter 在任务触发失败风暴下，把失败任务完整快照留在无上限本地 fallback buffer 中，"
            + "而且还维护了多份索引，导致对象长期强引用，最终触发 OOM。排查通过监控 -> jstat -> heap dump -> MAT 收敛到 ScheduleTaskSnapshot。";
    }

    public static String fullGcSummary() {
        return "ScheduleCenter 为保证秒级调度做了未来时间窗预取，但单批预取过大、背压不足，"
            + "导致任务对象在本地缓冲中停留过久并晋升到老年代，引发频繁 Full GC 和调度抖动。";
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
