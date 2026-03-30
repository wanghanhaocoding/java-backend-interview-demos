package com.example.schedulecenterfullgcdemo.fullgc;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleCenterFullGcDemoService {

    private static final int BUCKETS_PER_MINUTE = 5;
    private static final int SCANNED_MINUTE_WINDOWS = 2;
    private static final int SLICE_HOLD_SECONDS = 60;
    private static final int SCHEDULER_MAX_THREADS = 100;
    private static final int RETAINED_KB_PER_QUEUED_SLICE = 384;

    public FullGcCaseResult frequentFullGcCase() {
        int slicesSubmittedPerSecond = BUCKETS_PER_MINUTE * SCANNED_MINUTE_WINDOWS;
        int steadyStateConcurrentSlices = slicesSubmittedPerSecond * SLICE_HOLD_SECONDS;
        int queuedSlices = steadyStateConcurrentSlices - SCHEDULER_MAX_THREADS;
        double retainedMb = queuedSlices * RETAINED_KB_PER_QUEUED_SLICE / 1024.0;

        PressureMetrics pressureMetrics = new PressureMetrics(
                BUCKETS_PER_MINUTE,
                SCANNED_MINUTE_WINDOWS,
                slicesSubmittedPerSecond,
                SLICE_HOLD_SECONDS,
                SCHEDULER_MAX_THREADS,
                steadyStateConcurrentSlices,
                queuedSlices,
                retainedMb
        );

        List<String> incidentSteps = List.of(
                "1. 迁移模块把未来任务写入 Redis ZSet，key 形态是 yyyy-MM-dd HH:mm_{bucket}",
                "2. 调度层每秒都会同时扫描当前分钟和上一分钟补偿，所以每秒会提交 10 个分片任务",
                "3. 抢到分布式锁的节点不会立刻结束，而是会对这一分钟持续扫描接近 60 秒",
                "4. schedulerPool 最大线程只有 100，但稳态并发需求已经被抬到 600，剩余 500 个分片只能在线程池队列里等待",
                "5. 等待中的分片对象、Redis 查询结果、DB fallback 结果和回调上下文跨过多轮 Minor GC 后晋升到老年代",
                "6. 老年代回收压力变大，系统先表现成 Full GC 频繁、RT 抖动和触发延迟升高"
        );

        List<String> diagnosisSteps = List.of(
                "1. 先看 schedulerPool 和 triggerPool 的 activeCount、queueSize，确认是不是处理速率落后于提交速率",
                "2. 再看 Redis zrange、DB fallback 和 HTTP callback 的 RT，排除是否只是单点依赖变慢",
                "3. 接着用 jstat、jcmd、jmap 看 Young 回收效果、Old 区占用和存活对象排名",
                "4. 最后回到代码链路，确认问题根因是长生命周期分片扫描、大队列和缺少背压，而不是 JVM 参数本身太小"
        );

        List<String> diagnosisCommands = List.of(
                "jstat -gcutil <pid> 1000 20",
                "jcmd <pid> GC.heap_info",
                "jmap -histo:live <pid>"
        );

        List<String> fixes = List.of(
                "1. 先缩小单次扫描批次，避免每轮抓出过多任务对象",
                "2. 对分片扫描并发做硬限制，抢锁前先判断线程池和队列水位",
                "3. 队列从大而深改成可控上限，逼出背压而不是把压力藏进堆内存",
                "4. Redis 异常时限制 DB fallback 结果集规模，避免异常路径进一步放大对象数量",
                "5. 长期把近一分钟长任务拆成更细粒度、更短生命周期的扫描任务"
        );

        return new FullGcCaseResult(incidentSteps, pressureMetrics, diagnosisSteps, diagnosisCommands, fixes);
    }

    public record PressureMetrics(
            int bucketsPerMinute,
            int scannedMinuteWindows,
            int slicesSubmittedPerSecond,
            int sliceHoldSeconds,
            int schedulerMaxThreads,
            int steadyStateConcurrentSlices,
            int queuedSlices,
            double estimatedRetainedMb
    ) {
    }

    public record FullGcCaseResult(
            List<String> incidentSteps,
            PressureMetrics pressureMetrics,
            List<String> diagnosisSteps,
            List<String> diagnosisCommands,
            List<String> fixes
    ) {
    }
}
