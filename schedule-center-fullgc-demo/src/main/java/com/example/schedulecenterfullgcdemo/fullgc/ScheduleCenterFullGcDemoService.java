package com.example.schedulecenterfullgcdemo.fullgc;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ScheduleCenterFullGcDemoService {

    private static final int BUCKETS_PER_MINUTE = 5;
    private static final int SCANNED_MINUTE_WINDOWS = 2;
    private static final int SCHEDULER_FIXED_RATE_MILLIS = 1000;
    private static final int SLICE_HOLD_SECONDS = 60;
    private static final int SCHEDULER_POOL_CORE_THREADS = 10;
    private static final int SCHEDULER_MAX_THREADS = 100;
    private static final int SCHEDULER_QUEUE_CAPACITY = 99999;
    private static final int TRIGGER_ZRANGE_GAP_SECONDS = 1;
    private static final int TRIGGER_POOL_CORE_THREADS = 10;
    private static final int TRIGGER_MAX_THREADS = 100;
    private static final int TRIGGER_QUEUE_CAPACITY = 99999;
    private static final int ESTIMATED_RETAINED_KB_PER_QUEUED_SLICE = 448;

    public FullGcCaseResult frequentFullGcCase() {
        int slicesSubmittedPerSecond = BUCKETS_PER_MINUTE * SCANNED_MINUTE_WINDOWS;
        double schedulerCapacityPerSecond = (double) SCHEDULER_MAX_THREADS / SLICE_HOLD_SECONDS;
        int steadyStateConcurrentSlices = slicesSubmittedPerSecond * SLICE_HOLD_SECONDS;
        int queuedSlices = steadyStateConcurrentSlices - SCHEDULER_MAX_THREADS;
        double schedulerBacklogPerMinute = (slicesSubmittedPerSecond - schedulerCapacityPerSecond) * 60;
        double retainedMb = queuedSlices * ESTIMATED_RETAINED_KB_PER_QUEUED_SLICE / 1024.0;

        XtimerRuntimeProfile runtimeProfile = new XtimerRuntimeProfile(
                BUCKETS_PER_MINUTE,
                SCANNED_MINUTE_WINDOWS,
                SCHEDULER_FIXED_RATE_MILLIS,
                SLICE_HOLD_SECONDS,
                SCHEDULER_POOL_CORE_THREADS,
                SCHEDULER_MAX_THREADS,
                SCHEDULER_QUEUE_CAPACITY,
                TRIGGER_ZRANGE_GAP_SECONDS,
                TRIGGER_POOL_CORE_THREADS,
                TRIGGER_MAX_THREADS,
                TRIGGER_QUEUE_CAPACITY
        );

        PressureMetrics pressureMetrics = new PressureMetrics(
                slicesSubmittedPerSecond,
                schedulerCapacityPerSecond,
                steadyStateConcurrentSlices,
                queuedSlices,
                schedulerBacklogPerMinute,
                retainedMb,
                ESTIMATED_RETAINED_KB_PER_QUEUED_SLICE
        );

        List<String> incidentSteps = Arrays.asList(
                "1. 在 bitstorm-svr-xtimer 里，SchedulerWorker 使用 @Scheduled(fixedRate = 1000) 每秒触发一次调度。",
                "2. 每次调度都会对 5 个 bucket 同时提交“上一分钟补偿 + 当前分钟实时”两轮任务，所以 SchedulerTask.asyncHandleSlice 每秒固定提交 10 个分片扫描任务。",
                "3. 抢到锁后的 SchedulerTask 不会很快返回，而是直接调用 TriggerWorker.work；这个方法内部通过 Timer + CountDownLatch 持续扫描该 minuteBucketKey，生命周期接近 60 秒。",
                "4. 在真实配置里 schedulerPool.maxPoolSize=100、queueCapacity=99999，稳态需要同时挂住 600 个分片时，会有约 500 个分片长期堆在线程池队列里。",
                "5. TriggerTimerTask 每秒都会对 Redis ZSET 做 rangeByScore，Redis 异常时还会走 taskMapper.getTasksByTimeRange 做 DB fallback，再叠加 TaskModel、回调上下文和结果集合对象，很多对象会跨过多轮 Minor GC 晋升到老年代。",
                "6. 所以 xtimer 这类问题通常先表现成 Full GC 频繁、调度 RT 抖动和触发延迟升高，而不是一上来直接 OOM。"
        );

        List<String> diagnosisSteps = Arrays.asList(
                "1. 先看 schedulerPool 和 triggerPool 的 activeCount、queueSize，确认是不是提交速率已经持续超过处理速率。",
                "2. 再看 Redis rangeByScore RT、TaskMapper.getTasksByTimeRange RT 和回调 HTTP RT，确认是不是外部依赖变慢把长生命周期扫描进一步拖长。",
                "3. 接着用 jstat、jcmd、jmap 看 Young 回收效果、Old 区占用和存活对象类型，判断是不是对象晋升而不是静态泄漏。",
                "4. 最后回到 xtimer 代码链路，把 SchedulerWorker 每秒提交、TriggerWorker 挂住近一分钟、大队列隐藏压力、Redis 失败走 DB fallback 这些条件串成完整根因。"
        );

        List<String> diagnosisCommands = Arrays.asList(
                "jstat -gcutil <pid> 1000 20",
                "jcmd <pid> GC.heap_info",
                "jmap -histo:live <pid>",
                "top -Hp <pid>"
        );

        List<String> fixes = Arrays.asList(
                "1. 先缩小 TriggerTimerTask 每轮 handleBatch 的任务量，避免每秒从 Redis 或 DB 拉回过多对象。",
                "2. 给 schedulerPool 和 triggerPool 都加水位判断，抢锁前就识别本机是否已经饱和，而不是继续把压力推入 queueCapacity=99999 的深队列。",
                "3. 把大队列改成有界队列，让背压尽早暴露，而不是把对象和 Future 长时间堆在堆内存里。",
                "4. 限制 Redis 异常时的 DB fallback 结果集规模，避免 getTasksByTimeRange 在异常路径下放大对象数量。",
                "5. 长期把 TriggerWorker.work 这种接近一分钟的长生命周期扫描任务拆成更短、更易回收的子任务，并持续补齐 Full GC、队列积压和 fallback 命中率监控。"
        );

        return new FullGcCaseResult(runtimeProfile, incidentSteps, pressureMetrics, diagnosisSteps, diagnosisCommands, fixes);
    }

    public static final class XtimerRuntimeProfile {

        private final int bucketsPerMinute;
        private final int scannedMinuteWindows;
        private final int schedulerFixedRateMillis;
        private final int sliceHoldSeconds;
        private final int schedulerPoolCoreThreads;
        private final int schedulerMaxThreads;
        private final int schedulerQueueCapacity;
        private final int triggerZrangeGapSeconds;
        private final int triggerPoolCoreThreads;
        private final int triggerMaxThreads;
        private final int triggerQueueCapacity;

        public XtimerRuntimeProfile(int bucketsPerMinute,
                                    int scannedMinuteWindows,
                                    int schedulerFixedRateMillis,
                                    int sliceHoldSeconds,
                                    int schedulerPoolCoreThreads,
                                    int schedulerMaxThreads,
                                    int schedulerQueueCapacity,
                                    int triggerZrangeGapSeconds,
                                    int triggerPoolCoreThreads,
                                    int triggerMaxThreads,
                                    int triggerQueueCapacity) {
            this.bucketsPerMinute = bucketsPerMinute;
            this.scannedMinuteWindows = scannedMinuteWindows;
            this.schedulerFixedRateMillis = schedulerFixedRateMillis;
            this.sliceHoldSeconds = sliceHoldSeconds;
            this.schedulerPoolCoreThreads = schedulerPoolCoreThreads;
            this.schedulerMaxThreads = schedulerMaxThreads;
            this.schedulerQueueCapacity = schedulerQueueCapacity;
            this.triggerZrangeGapSeconds = triggerZrangeGapSeconds;
            this.triggerPoolCoreThreads = triggerPoolCoreThreads;
            this.triggerMaxThreads = triggerMaxThreads;
            this.triggerQueueCapacity = triggerQueueCapacity;
        }

        public int bucketsPerMinute() {
            return bucketsPerMinute;
        }

        public int scannedMinuteWindows() {
            return scannedMinuteWindows;
        }

        public int schedulerFixedRateMillis() {
            return schedulerFixedRateMillis;
        }

        public int sliceHoldSeconds() {
            return sliceHoldSeconds;
        }

        public int schedulerPoolCoreThreads() {
            return schedulerPoolCoreThreads;
        }

        public int schedulerMaxThreads() {
            return schedulerMaxThreads;
        }

        public int schedulerQueueCapacity() {
            return schedulerQueueCapacity;
        }

        public int triggerZrangeGapSeconds() {
            return triggerZrangeGapSeconds;
        }

        public int triggerPoolCoreThreads() {
            return triggerPoolCoreThreads;
        }

        public int triggerMaxThreads() {
            return triggerMaxThreads;
        }

        public int triggerQueueCapacity() {
            return triggerQueueCapacity;
        }

        @Override
        public String toString() {
            return "XtimerRuntimeProfile{" +
                    "bucketsPerMinute=" + bucketsPerMinute +
                    ", scannedMinuteWindows=" + scannedMinuteWindows +
                    ", schedulerFixedRateMillis=" + schedulerFixedRateMillis +
                    ", sliceHoldSeconds=" + sliceHoldSeconds +
                    ", schedulerPoolCoreThreads=" + schedulerPoolCoreThreads +
                    ", schedulerMaxThreads=" + schedulerMaxThreads +
                    ", schedulerQueueCapacity=" + schedulerQueueCapacity +
                    ", triggerZrangeGapSeconds=" + triggerZrangeGapSeconds +
                    ", triggerPoolCoreThreads=" + triggerPoolCoreThreads +
                    ", triggerMaxThreads=" + triggerMaxThreads +
                    ", triggerQueueCapacity=" + triggerQueueCapacity +
                    '}';
        }
    }

    public static final class PressureMetrics {

        private final int slicesSubmittedPerSecond;
        private final double schedulerCapacityPerSecond;
        private final int steadyStateConcurrentSlices;
        private final int queuedSlices;
        private final double schedulerBacklogPerMinute;
        private final double estimatedRetainedMb;
        private final int estimatedRetainedKbPerQueuedSlice;

        public PressureMetrics(int slicesSubmittedPerSecond,
                               double schedulerCapacityPerSecond,
                               int steadyStateConcurrentSlices,
                               int queuedSlices,
                               double schedulerBacklogPerMinute,
                               double estimatedRetainedMb,
                               int estimatedRetainedKbPerQueuedSlice) {
            this.slicesSubmittedPerSecond = slicesSubmittedPerSecond;
            this.schedulerCapacityPerSecond = schedulerCapacityPerSecond;
            this.steadyStateConcurrentSlices = steadyStateConcurrentSlices;
            this.queuedSlices = queuedSlices;
            this.schedulerBacklogPerMinute = schedulerBacklogPerMinute;
            this.estimatedRetainedMb = estimatedRetainedMb;
            this.estimatedRetainedKbPerQueuedSlice = estimatedRetainedKbPerQueuedSlice;
        }

        public int slicesSubmittedPerSecond() {
            return slicesSubmittedPerSecond;
        }

        public double schedulerCapacityPerSecond() {
            return schedulerCapacityPerSecond;
        }

        public int steadyStateConcurrentSlices() {
            return steadyStateConcurrentSlices;
        }

        public int queuedSlices() {
            return queuedSlices;
        }

        public double schedulerBacklogPerMinute() {
            return schedulerBacklogPerMinute;
        }

        public double estimatedRetainedMb() {
            return estimatedRetainedMb;
        }

        public int estimatedRetainedKbPerQueuedSlice() {
            return estimatedRetainedKbPerQueuedSlice;
        }

        @Override
        public String toString() {
            return "PressureMetrics{" +
                    "slicesSubmittedPerSecond=" + slicesSubmittedPerSecond +
                    ", schedulerCapacityPerSecond=" + schedulerCapacityPerSecond +
                    ", steadyStateConcurrentSlices=" + steadyStateConcurrentSlices +
                    ", queuedSlices=" + queuedSlices +
                    ", schedulerBacklogPerMinute=" + schedulerBacklogPerMinute +
                    ", estimatedRetainedMb=" + estimatedRetainedMb +
                    ", estimatedRetainedKbPerQueuedSlice=" + estimatedRetainedKbPerQueuedSlice +
                    '}';
        }
    }

    public static final class FullGcCaseResult {

        private final XtimerRuntimeProfile runtimeProfile;
        private final List<String> incidentSteps;
        private final PressureMetrics pressureMetrics;
        private final List<String> diagnosisSteps;
        private final List<String> diagnosisCommands;
        private final List<String> fixes;

        public FullGcCaseResult(XtimerRuntimeProfile runtimeProfile,
                                List<String> incidentSteps,
                                PressureMetrics pressureMetrics,
                                List<String> diagnosisSteps,
                                List<String> diagnosisCommands,
                                List<String> fixes) {
            this.runtimeProfile = runtimeProfile;
            this.incidentSteps = incidentSteps;
            this.pressureMetrics = pressureMetrics;
            this.diagnosisSteps = diagnosisSteps;
            this.diagnosisCommands = diagnosisCommands;
            this.fixes = fixes;
        }

        public XtimerRuntimeProfile runtimeProfile() {
            return runtimeProfile;
        }

        public List<String> incidentSteps() {
            return incidentSteps;
        }

        public PressureMetrics pressureMetrics() {
            return pressureMetrics;
        }

        public List<String> diagnosisSteps() {
            return diagnosisSteps;
        }

        public List<String> diagnosisCommands() {
            return diagnosisCommands;
        }

        public List<String> fixes() {
            return fixes;
        }
    }
}
