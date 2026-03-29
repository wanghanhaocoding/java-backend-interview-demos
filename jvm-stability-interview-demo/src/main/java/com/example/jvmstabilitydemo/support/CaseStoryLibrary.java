package com.example.jvmstabilitydemo.support;

public final class CaseStoryLibrary {

    private CaseStoryLibrary() {
    }

    public static String oomSummary() {
        return "AsyncJobCenter 在银行回调失败风暴下，把失败任务完整快照留在无上限本地 fallback buffer 中，"
            + "而且还维护了多份索引，导致对象长期强引用，最终触发 OOM。排查通过监控 -> jstat -> heap dump -> MAT 收敛到 JobCallbackSnapshot。";
    }

    public static String fullGcSummary() {
        return "ScheduleCenter 为保证秒级调度做了未来时间窗预取，但单批预取过大、背压不足，"
            + "导致任务对象在本地缓冲中停留过久并晋升到老年代，引发频繁 Full GC 和调度抖动。";
    }

    public static String deadlockSummary() {
        return "任务执行线程和补偿线程对任务锁、回执锁的获取顺序不一致，"
            + "并发时形成环形等待。通过线程 dump 发现 Java 级死锁，最终通过统一锁顺序和 tryLock 降风险。";
    }
}
