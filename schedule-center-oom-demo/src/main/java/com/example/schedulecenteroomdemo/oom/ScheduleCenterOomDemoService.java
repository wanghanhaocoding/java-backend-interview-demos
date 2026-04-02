package com.example.schedulecenteroomdemo.oom;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ScheduleCenterOomDemoService {

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
    private static final int ASSUMED_TRIGGER_TASKS_PER_SLICE_PER_SECOND = 12;
    private static final int ASSUMED_CALLBACK_SECONDS = 2;
    private static final int ESTIMATED_RETAINED_KB_PER_TRIGGER_TASK = 18;
    private static final int ESTIMATED_RETAINED_KB_PER_QUEUED_SLICE = 448;

    public OomCaseResult accumulationOomCase() {
        int schedulerArrivalPerSecond = BUCKETS_PER_MINUTE * SCANNED_MINUTE_WINDOWS;
        double schedulerCapacityPerSecond = (double) SCHEDULER_MAX_THREADS / SLICE_HOLD_SECONDS;
        double schedulerBacklogPerMinute = (schedulerArrivalPerSecond - schedulerCapacityPerSecond) * 60;
        double schedulerRetainedMb = schedulerBacklogPerMinute * ESTIMATED_RETAINED_KB_PER_QUEUED_SLICE / 1024.0;

        int triggerArrivalPerSecond = schedulerArrivalPerSecond * ASSUMED_TRIGGER_TASKS_PER_SLICE_PER_SECOND;
        double triggerCapacityPerSecond = (double) TRIGGER_MAX_THREADS / ASSUMED_CALLBACK_SECONDS;
        double triggerBacklogPerMinute = (triggerArrivalPerSecond - triggerCapacityPerSecond) * 60;
        double triggerRetainedMbPerMinute = triggerBacklogPerMinute * ESTIMATED_RETAINED_KB_PER_TRIGGER_TASK / 1024.0;

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

        SchedulerPressure schedulerPressure = new SchedulerPressure(
                schedulerArrivalPerSecond,
                SLICE_HOLD_SECONDS,
                SCHEDULER_MAX_THREADS,
                schedulerCapacityPerSecond,
                schedulerBacklogPerMinute,
                schedulerRetainedMb,
                ESTIMATED_RETAINED_KB_PER_QUEUED_SLICE
        );

        TriggerPressure triggerPressure = new TriggerPressure(
                triggerArrivalPerSecond,
                TRIGGER_MAX_THREADS,
                ASSUMED_CALLBACK_SECONDS,
                triggerCapacityPerSecond,
                triggerBacklogPerMinute,
                triggerRetainedMbPerMinute,
                ESTIMATED_RETAINED_KB_PER_TRIGGER_TASK
        );

        List<String> incidentSteps = Arrays.asList(
                "1. 在真实 xtimer 里，SchedulerWorker 每秒都会把 5 个 bucket 的“上一分钟补偿 + 当前分钟实时”两轮分片扔给 SchedulerTask.asyncHandleSlice，所以调度层固定每秒提交 10 个分片。",
                "2. SchedulerTask 抢到分布式锁后会同步进入 TriggerWorker.work；而 TriggerWorker 内部通过 Timer.scheduleAtFixedRate + CountDownLatch 把该 minuteBucketKey 扫到分钟结束，这会把 schedulerPool 线程挂住接近 60 秒。",
                "3. schedulerPool.maxPoolSize=100 但 queueCapacity=99999，压力不会立刻失败，而是先被大队列吃掉；同时 TriggerTimerTask 每秒做一次 rangeByScore，把拿到的 TaskModel 继续异步丢给 triggerPool。",
                "4. 一旦回调下游 RT 变慢，triggerPool.queueCapacity=99999 也会开始吞积压；队列里堆的不是简单整数，而是 TaskModel、回调请求体、Future、集合对象和上下文。",
                "5. Redis 取数异常时，TriggerTimerTask 会走 taskMapper.getTasksByTimeRange 做 DB fallback，这会把单批结果集进一步放大，导致 Full GC 先越来越频繁，继续恶化后老年代回不下来，最终才是 OOM。",
                "6. 所以这个问题更像 xtimer 的积压型 OOM，而不是典型静态集合泄漏。"
        );

        List<String> diagnosisSteps = Arrays.asList(
                "1. 先看 schedulerPool.queueSize 和 triggerPool.queueSize 是否持续单向增长，确认这是积压，不是瞬时峰值。",
                "2. 再看 Redis rangeByScore RT、TaskMapper.getTasksByTimeRange RT 和回调 RT，确认是不是 Redis 抖动或慢回调把消费能力拖垮了。",
                "3. 用 jstat、jcmd、jmap 看 Full GC 回收效果和对象直方图，如果堆里主要是任务 DTO、集合、Future 和回调上下文，就更像积压放大，不是纯泄漏。",
                "4. 最后回到 xtimer 真实代码链路，把每秒提交、近一分钟长扫描、99999 深队列、DB fallback 放大这几件事拼成根因。"
        );

        List<String> diagnosisCommands = Arrays.asList(
                "jstat -gcutil <pid> 1000 20",
                "jcmd <pid> GC.heap_info",
                "jmap -histo:live <pid>",
                "top -Hp <pid>"
        );

        List<String> fixes = Arrays.asList(
                "1. 先缩小扫描批次和触发投递速率，让 triggerPool 先从积压里爬出来。",
                "2. 立即把 schedulerPool 和 triggerPool 的超大队列改成有限长度，宁可暴露背压，也不要继续把对象堆进堆内存。",
                "3. 抢锁前增加本机水位判断，让已经饱和的节点不再继续拿 minuteBucketKey。",
                "4. 给 TaskCache 异常路径下的 DB fallback 加严格上限，避免 getTasksByTimeRange 把结果集做大。",
                "5. 长期把 TriggerWorker.work 这种近一分钟长任务拆短，并把回调链路进一步异步化，例如按你简历里的方向用 MQ 做削峰。"
        );

        return new OomCaseResult(runtimeProfile, incidentSteps, schedulerPressure, triggerPressure, diagnosisSteps, diagnosisCommands, fixes);
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

    public static final class SchedulerPressure {

        private final int arrivalPerSecond;
        private final int sliceHoldSeconds;
        private final int maxThreads;
        private final double capacityPerSecond;
        private final double backlogPerMinute;
        private final double estimatedRetainedMb;
        private final int estimatedRetainedKbPerQueuedSlice;

        public SchedulerPressure(int arrivalPerSecond,
                                 int sliceHoldSeconds,
                                 int maxThreads,
                                 double capacityPerSecond,
                                 double backlogPerMinute,
                                 double estimatedRetainedMb,
                                 int estimatedRetainedKbPerQueuedSlice) {
            this.arrivalPerSecond = arrivalPerSecond;
            this.sliceHoldSeconds = sliceHoldSeconds;
            this.maxThreads = maxThreads;
            this.capacityPerSecond = capacityPerSecond;
            this.backlogPerMinute = backlogPerMinute;
            this.estimatedRetainedMb = estimatedRetainedMb;
            this.estimatedRetainedKbPerQueuedSlice = estimatedRetainedKbPerQueuedSlice;
        }

        public int arrivalPerSecond() {
            return arrivalPerSecond;
        }

        public int sliceHoldSeconds() {
            return sliceHoldSeconds;
        }

        public int maxThreads() {
            return maxThreads;
        }

        public double capacityPerSecond() {
            return capacityPerSecond;
        }

        public double backlogPerMinute() {
            return backlogPerMinute;
        }

        public double estimatedRetainedMb() {
            return estimatedRetainedMb;
        }

        public int estimatedRetainedKbPerQueuedSlice() {
            return estimatedRetainedKbPerQueuedSlice;
        }

        @Override
        public String toString() {
            return "SchedulerPressure{" +
                    "arrivalPerSecond=" + arrivalPerSecond +
                    ", sliceHoldSeconds=" + sliceHoldSeconds +
                    ", maxThreads=" + maxThreads +
                    ", capacityPerSecond=" + capacityPerSecond +
                    ", backlogPerMinute=" + backlogPerMinute +
                    ", estimatedRetainedMb=" + estimatedRetainedMb +
                    ", estimatedRetainedKbPerQueuedSlice=" + estimatedRetainedKbPerQueuedSlice +
                    '}';
        }
    }

    public static final class TriggerPressure {

        private final int arrivalPerSecond;
        private final int maxThreads;
        private final int callbackSeconds;
        private final double capacityPerSecond;
        private final double backlogPerMinute;
        private final double estimatedRetainedMbPerMinute;
        private final int estimatedRetainedKbPerTask;

        public TriggerPressure(int arrivalPerSecond,
                               int maxThreads,
                               int callbackSeconds,
                               double capacityPerSecond,
                               double backlogPerMinute,
                               double estimatedRetainedMbPerMinute,
                               int estimatedRetainedKbPerTask) {
            this.arrivalPerSecond = arrivalPerSecond;
            this.maxThreads = maxThreads;
            this.callbackSeconds = callbackSeconds;
            this.capacityPerSecond = capacityPerSecond;
            this.backlogPerMinute = backlogPerMinute;
            this.estimatedRetainedMbPerMinute = estimatedRetainedMbPerMinute;
            this.estimatedRetainedKbPerTask = estimatedRetainedKbPerTask;
        }

        public int arrivalPerSecond() {
            return arrivalPerSecond;
        }

        public int maxThreads() {
            return maxThreads;
        }

        public int callbackSeconds() {
            return callbackSeconds;
        }

        public double capacityPerSecond() {
            return capacityPerSecond;
        }

        public double backlogPerMinute() {
            return backlogPerMinute;
        }

        public double estimatedRetainedMbPerMinute() {
            return estimatedRetainedMbPerMinute;
        }

        public int estimatedRetainedKbPerTask() {
            return estimatedRetainedKbPerTask;
        }

        @Override
        public String toString() {
            return "TriggerPressure{" +
                    "arrivalPerSecond=" + arrivalPerSecond +
                    ", maxThreads=" + maxThreads +
                    ", callbackSeconds=" + callbackSeconds +
                    ", capacityPerSecond=" + capacityPerSecond +
                    ", backlogPerMinute=" + backlogPerMinute +
                    ", estimatedRetainedMbPerMinute=" + estimatedRetainedMbPerMinute +
                    ", estimatedRetainedKbPerTask=" + estimatedRetainedKbPerTask +
                    '}';
        }
    }

    public static final class OomCaseResult {

        private final XtimerRuntimeProfile runtimeProfile;
        private final List<String> incidentSteps;
        private final SchedulerPressure schedulerPressure;
        private final TriggerPressure triggerPressure;
        private final List<String> diagnosisSteps;
        private final List<String> diagnosisCommands;
        private final List<String> fixes;

        public OomCaseResult(XtimerRuntimeProfile runtimeProfile,
                             List<String> incidentSteps,
                             SchedulerPressure schedulerPressure,
                             TriggerPressure triggerPressure,
                             List<String> diagnosisSteps,
                             List<String> diagnosisCommands,
                             List<String> fixes) {
            this.runtimeProfile = runtimeProfile;
            this.incidentSteps = incidentSteps;
            this.schedulerPressure = schedulerPressure;
            this.triggerPressure = triggerPressure;
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

        public SchedulerPressure schedulerPressure() {
            return schedulerPressure;
        }

        public TriggerPressure triggerPressure() {
            return triggerPressure;
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
