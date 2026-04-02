package com.example.schedulecentercallbacktimeoutdemo.callback;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ScheduleCenterCallbackTimeoutDemoService {

    private static final int BUCKETS_PER_MINUTE = 5;
    private static final int SCANNED_MINUTE_WINDOWS = 2;
    private static final int SCHEDULER_FIXED_RATE_MILLIS = 1000;
    private static final int SCHEDULER_POOL_CORE_THREADS = 10;
    private static final int SCHEDULER_MAX_THREADS = 100;
    private static final int SCHEDULER_QUEUE_CAPACITY = 99999;
    private static final int TRIGGER_ZRANGE_GAP_SECONDS = 1;
    private static final int TRIGGER_POOL_CORE_THREADS = 10;
    private static final int TRIGGER_MAX_THREADS = 100;
    private static final int TRIGGER_QUEUE_CAPACITY = 99999;
    private static final String REJECTION_POLICY = "CallerRunsPolicy";
    private static final int ASSUMED_CALLBACK_TASKS_PER_SLICE = 12;
    private static final int ASSUMED_CALLBACK_RT_MILLIS = 3000;
    private static final int TEACHING_CALLBACK_TIMEOUT_BUDGET_MILLIS = 2000;

    public CallbackTimeoutCaseStudy callbackTimeoutCaseStudy() {
        int sliceArrivalPerSecond = BUCKETS_PER_MINUTE * SCANNED_MINUTE_WINDOWS;
        int callbackArrivalPerSecond = sliceArrivalPerSecond * ASSUMED_CALLBACK_TASKS_PER_SLICE;
        double triggerCapacityPerSecond = TRIGGER_MAX_THREADS / (ASSUMED_CALLBACK_RT_MILLIS / 1000.0);
        double triggerBacklogPerSecond = Math.max(0.0, callbackArrivalPerSecond - triggerCapacityPerSecond);
        double triggerBacklogPerMinute = triggerBacklogPerSecond * 60.0;
        double estimatedQueueFillSeconds = triggerBacklogPerSecond > 0.0
                ? TRIGGER_QUEUE_CAPACITY / triggerBacklogPerSecond
                : Double.POSITIVE_INFINITY;
        double estimatedQueueFillMinutes = estimatedQueueFillSeconds / 60.0;
        double estimatedCallerRunsCallbacksPerSecond = triggerBacklogPerSecond;
        double estimatedCallerThreadSecondsPerSecond = estimatedCallerRunsCallbacksPerSecond
                * (ASSUMED_CALLBACK_RT_MILLIS / 1000.0);

        RuntimeProfile runtimeProfile = new RuntimeProfile(
                BUCKETS_PER_MINUTE,
                SCANNED_MINUTE_WINDOWS,
                SCHEDULER_FIXED_RATE_MILLIS,
                SCHEDULER_POOL_CORE_THREADS,
                SCHEDULER_MAX_THREADS,
                SCHEDULER_QUEUE_CAPACITY,
                TRIGGER_ZRANGE_GAP_SECONDS,
                TRIGGER_POOL_CORE_THREADS,
                TRIGGER_MAX_THREADS,
                TRIGGER_QUEUE_CAPACITY,
                REJECTION_POLICY
        );

        PressureMetrics pressureMetrics = new PressureMetrics(
                sliceArrivalPerSecond,
                callbackArrivalPerSecond,
                ASSUMED_CALLBACK_RT_MILLIS,
                TEACHING_CALLBACK_TIMEOUT_BUDGET_MILLIS,
                triggerCapacityPerSecond,
                triggerBacklogPerSecond,
                triggerBacklogPerMinute,
                estimatedQueueFillSeconds,
                estimatedQueueFillMinutes,
                estimatedCallerRunsCallbacksPerSecond,
                estimatedCallerThreadSecondsPerSecond
        );

        List<String> codeAnchors = Arrays.asList(
                "SchedulerWorker @Scheduled(fixedRate = 1000)",
                "TriggerTimerTask.handleBatch",
                "TriggerPoolTask.runExecutor",
                "ExecutorWorker.work",
                "taskMapper.getTasksByTimerIdUnix",
                "ExecutorWorker.executeTimerCallBack",
                "AsyncPool.schedulerPoolExecutor",
                "AsyncPool.triggerPoolExecutor"
        );

        List<String> incidentTimeline = Arrays.asList(
                "1. SchedulerWorker 仍按 fixedRate=1000ms 提交 5 个 bucket * 2 个分钟窗口，总计 10 个 minute slice/s。",
                "2. TriggerTimerTask.handleBatch 拉到到期任务后，通过 TriggerPoolTask.runExecutor 继续把 callback 压进 triggerPool。",
                "3. ExecutorWorker.work 会先查 taskMapper.getTasksByTimerIdUnix，随后在 ExecutorWorker.executeTimerCallBack 里同步发 HTTP callback。",
                "4. 当业务 callback RT 稳定落在 3000ms，而教学预算只允许 2000ms 时，triggerPool 的有效处理能力只剩约 33.33/s，却要接住约 120/s 的 callback 到达速率。",
                "5. triggerPool 深队列会先把问题伪装成“只是 queueSize 在涨”，真正的调度延迟要等到队列顶满、CallerRunsPolicy 把 callback 压回调用线程之后才会彻底暴露出来。"
        );

        List<PropagationStage> propagationStages = Arrays.asList(
                new PropagationStage(
                        "阶段 1：triggerPool backlog 先增长",
                        "triggerPool.activeCount 接近 100，queueSize 单向上升。",
                        "TriggerPoolTask.runExecutor 的投递速率高于 ExecutorWorker.executeTimerCallBack 的同步 HTTP 完成速率。",
                        "AsyncPool.triggerPoolExecutor / ExecutorWorker.executeTimerCallBack"
                ),
                new PropagationStage(
                        "阶段 2：慢 callback 开始制造 delay propagation",
                        "TriggerTimerTask.handleBatch 虽然还在投递，但 backlog 已经按每秒 86.67 个 callback 的速度累积。",
                        "队列把问题短暂隐藏起来，可每个 callback 都要占住 triggerPool 工作线程约 3s，积压已经不可逆。",
                        "TriggerTimerTask.handleBatch / TriggerPoolTask.runExecutor"
                ),
                new PropagationStage(
                        "阶段 3：CallerRunsPolicy 把阻塞反弹给调用线程",
                        "当 triggerPool 队列接近 99999 上限后，新任务不会再进新线程，而是由调用 TriggerPoolTask.runExecutor 的线程自己执行 callback。",
                        "AsyncPool.triggerPoolExecutor 使用 CallerRunsPolicy，所以拒绝并不丢任务，而是把慢回调执行时间压回扫描线程。",
                        "AsyncPool.triggerPoolExecutor / TriggerPoolTask.runExecutor"
                ),
                new PropagationStage(
                        "阶段 4：schedulerPool lag 被放大",
                        "调用线程被迫执行 callback 后，下一轮 SchedulerWorker fixedRate tick 会与上一轮扫描重叠，调度提交延迟开始扩散。",
                        "schedulerPool 也挂着 CallerRunsPolicy，调度链路缺少背压时，callback 慢 RT 最终会回传到 slice 提交和 minute bucket 扫描。",
                        "SchedulerWorker @Scheduled(fixedRate = 1000) / AsyncPool.schedulerPoolExecutor"
                )
        );

        List<EvidenceSignal> evidenceSignals = Arrays.asList(
                new EvidenceSignal(
                        "callback timeout 告警",
                        "业务 callback RT 超过 2000ms 的教学预算，但 ExecutorWorker.executeTimerCallBack 仍在同步等待响应或异常返回。",
                        "ExecutorWorker.executeTimerCallBack"
                ),
                new EvidenceSignal(
                        "triggerPool.activeCount=100",
                        "triggerPool 线程全部被慢 callback 占满，说明瓶颈在 callback 执行而不是 Redis 空扫。",
                        "AsyncPool.triggerPoolExecutor"
                ),
                new EvidenceSignal(
                        "triggerPool.queueSize 持续增长",
                        "投递仍在继续，但消费速率明显落后，问题主线是 backlog 而不是瞬时抖动。",
                        "TriggerPoolTask.runExecutor / AsyncPool.triggerPoolExecutor"
                ),
                new EvidenceSignal(
                        "minute bucket lag 抬高",
                        "CallerRunsPolicy 开始让扫描线程自己执行 callback 后，下一轮 fixedRate 调度会延后。",
                        "SchedulerWorker @Scheduled(fixedRate = 1000) / TriggerTimerTask.handleBatch"
                )
        );

        List<String> scopeBoundary = Arrays.asList(
                "边界 1：这里不把 Full GC 或老年代晋升当主问题，那部分由 schedule-center-fullgc-demo 承担。",
                "边界 2：这里不把堆对象持续积压到 OOM 当主问题，那部分由 schedule-center-oom-demo 承担。",
                "边界 3：这里不把空扫热点或 DB fallback 查询风暴导致的 CPU 高当主问题，那部分由 cpu-high-troubleshooting-demo 承担。",
                "边界 4：这个模块只聚焦 callback timeout -> triggerPool backlog -> schedulerPool lag -> CallerRunsPolicy 反压链路。"
        );

        List<MitigationAction> mitigationActions = Arrays.asList(
                new MitigationAction(
                        "止血",
                        "先限制最热 callback 路由或 app 的并发，把 TriggerPoolTask.runExecutor 的入流压下来。",
                        "先让 triggerPool 从 120/s 到达速率回到可消费区间，避免 queueSize 继续滚雪球。",
                        "TriggerPoolTask.runExecutor"
                ),
                new MitigationAction(
                        "止血",
                        "缩小 TriggerTimerTask.handleBatch 单轮投递批次，必要时暂停最慢的 callback 场景。",
                        "callback RT 未恢复前，减少每秒进入 triggerPool 的新任务数量，比盲目扩容更快止住传播。",
                        "TriggerTimerTask.handleBatch"
                ),
                new MitigationAction(
                        "止血",
                        "按 triggerPool 水位给 SchedulerWorker 增加反压，避免 schedulerPool 继续按 fixedRate 推高 backlog。",
                        "如果 triggerPool 已经接近 CallerRunsPolicy 触发点，继续按 1000ms 固定节奏提交只会把 lag 扩散到调度层。",
                        "SchedulerWorker @Scheduled(fixedRate = 1000) / AsyncPool.schedulerPoolExecutor"
                ),
                new MitigationAction(
                        "根治",
                        "给 callback 执行链路补齐明确的 connect/read timeout 预算，并治理下游慢接口。",
                        "ExecutorWorker.executeTimerCallBack 是同步 HTTP 调用，超时治理不到位就会直接吞掉 triggerPool 工作线程。",
                        "ExecutorWorker.executeTimerCallBack"
                ),
                new MitigationAction(
                        "根治",
                        "重审 AsyncPool 的深队列策略，改成更早暴露压力的有界队列或分级隔离方案。",
                        "99999 深队列会把问题延后暴露，直到 CallerRunsPolicy 开始反压时已经进入系统级延迟传播。",
                        "AsyncPool.schedulerPoolExecutor / AsyncPool.triggerPoolExecutor"
                ),
                new MitigationAction(
                        "根治",
                        "把重 callback 场景解耦到独立执行通道，例如 MQ 削峰或按 app 隔离线程池。",
                        "根因不是一次 timeout 告警，而是慢 callback 直接占住 xtimer 的 trigger 执行线程。",
                        "ExecutorWorker.executeTimerCallBack / AsyncPool.triggerPoolExecutor"
                )
        );

        return new CallbackTimeoutCaseStudy(
                runtimeProfile,
                pressureMetrics,
                codeAnchors,
                incidentTimeline,
                propagationStages,
                evidenceSignals,
                scopeBoundary,
                mitigationActions
        );
    }

    public CallbackTimeoutCaseStudy callbackTimeoutSkeleton() {
        return callbackTimeoutCaseStudy();
    }

    public static final class RuntimeProfile {

        private final int bucketsPerMinute;
        private final int scannedMinuteWindows;
        private final int schedulerFixedRateMillis;
        private final int schedulerPoolCoreThreads;
        private final int schedulerMaxThreads;
        private final int schedulerQueueCapacity;
        private final int triggerZrangeGapSeconds;
        private final int triggerPoolCoreThreads;
        private final int triggerMaxThreads;
        private final int triggerQueueCapacity;
        private final String rejectionPolicy;

        public RuntimeProfile(int bucketsPerMinute,
                              int scannedMinuteWindows,
                              int schedulerFixedRateMillis,
                              int schedulerPoolCoreThreads,
                              int schedulerMaxThreads,
                              int schedulerQueueCapacity,
                              int triggerZrangeGapSeconds,
                              int triggerPoolCoreThreads,
                              int triggerMaxThreads,
                              int triggerQueueCapacity,
                              String rejectionPolicy) {
            this.bucketsPerMinute = bucketsPerMinute;
            this.scannedMinuteWindows = scannedMinuteWindows;
            this.schedulerFixedRateMillis = schedulerFixedRateMillis;
            this.schedulerPoolCoreThreads = schedulerPoolCoreThreads;
            this.schedulerMaxThreads = schedulerMaxThreads;
            this.schedulerQueueCapacity = schedulerQueueCapacity;
            this.triggerZrangeGapSeconds = triggerZrangeGapSeconds;
            this.triggerPoolCoreThreads = triggerPoolCoreThreads;
            this.triggerMaxThreads = triggerMaxThreads;
            this.triggerQueueCapacity = triggerQueueCapacity;
            this.rejectionPolicy = rejectionPolicy;
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

        public String rejectionPolicy() {
            return rejectionPolicy;
        }

        @Override
        public String toString() {
            return "RuntimeProfile{" +
                    "bucketsPerMinute=" + bucketsPerMinute +
                    ", scannedMinuteWindows=" + scannedMinuteWindows +
                    ", schedulerFixedRateMillis=" + schedulerFixedRateMillis +
                    ", schedulerPoolCoreThreads=" + schedulerPoolCoreThreads +
                    ", schedulerMaxThreads=" + schedulerMaxThreads +
                    ", schedulerQueueCapacity=" + schedulerQueueCapacity +
                    ", triggerZrangeGapSeconds=" + triggerZrangeGapSeconds +
                    ", triggerPoolCoreThreads=" + triggerPoolCoreThreads +
                    ", triggerMaxThreads=" + triggerMaxThreads +
                    ", triggerQueueCapacity=" + triggerQueueCapacity +
                    ", rejectionPolicy='" + rejectionPolicy + '\'' +
                    '}';
        }
    }

    public static final class PressureMetrics {

        private final int sliceArrivalPerSecond;
        private final int callbackArrivalPerSecond;
        private final int callbackRtMillis;
        private final int callbackTimeoutBudgetMillis;
        private final double triggerCapacityPerSecond;
        private final double triggerBacklogPerSecond;
        private final double triggerBacklogPerMinute;
        private final double estimatedQueueFillSeconds;
        private final double estimatedQueueFillMinutes;
        private final double estimatedCallerRunsCallbacksPerSecond;
        private final double estimatedCallerThreadSecondsPerSecond;

        public PressureMetrics(int sliceArrivalPerSecond,
                               int callbackArrivalPerSecond,
                               int callbackRtMillis,
                               int callbackTimeoutBudgetMillis,
                               double triggerCapacityPerSecond,
                               double triggerBacklogPerSecond,
                               double triggerBacklogPerMinute,
                               double estimatedQueueFillSeconds,
                               double estimatedQueueFillMinutes,
                               double estimatedCallerRunsCallbacksPerSecond,
                               double estimatedCallerThreadSecondsPerSecond) {
            this.sliceArrivalPerSecond = sliceArrivalPerSecond;
            this.callbackArrivalPerSecond = callbackArrivalPerSecond;
            this.callbackRtMillis = callbackRtMillis;
            this.callbackTimeoutBudgetMillis = callbackTimeoutBudgetMillis;
            this.triggerCapacityPerSecond = triggerCapacityPerSecond;
            this.triggerBacklogPerSecond = triggerBacklogPerSecond;
            this.triggerBacklogPerMinute = triggerBacklogPerMinute;
            this.estimatedQueueFillSeconds = estimatedQueueFillSeconds;
            this.estimatedQueueFillMinutes = estimatedQueueFillMinutes;
            this.estimatedCallerRunsCallbacksPerSecond = estimatedCallerRunsCallbacksPerSecond;
            this.estimatedCallerThreadSecondsPerSecond = estimatedCallerThreadSecondsPerSecond;
        }

        public int sliceArrivalPerSecond() {
            return sliceArrivalPerSecond;
        }

        public int callbackArrivalPerSecond() {
            return callbackArrivalPerSecond;
        }

        public int callbackRtMillis() {
            return callbackRtMillis;
        }

        public int callbackTimeoutBudgetMillis() {
            return callbackTimeoutBudgetMillis;
        }

        public int callbackTimeoutMillis() {
            return callbackTimeoutBudgetMillis;
        }

        public double triggerCapacityPerSecond() {
            return triggerCapacityPerSecond;
        }

        public double triggerBacklogPerSecond() {
            return triggerBacklogPerSecond;
        }

        public double triggerBacklogPerMinute() {
            return triggerBacklogPerMinute;
        }

        public double estimatedQueueFillSeconds() {
            return estimatedQueueFillSeconds;
        }

        public double estimatedQueueFillMinutes() {
            return estimatedQueueFillMinutes;
        }

        public double estimatedCallerRunsCallbacksPerSecond() {
            return estimatedCallerRunsCallbacksPerSecond;
        }

        public double estimatedCallerThreadSecondsPerSecond() {
            return estimatedCallerThreadSecondsPerSecond;
        }

        @Override
        public String toString() {
            return "PressureMetrics{" +
                    "sliceArrivalPerSecond=" + sliceArrivalPerSecond +
                    ", callbackArrivalPerSecond=" + callbackArrivalPerSecond +
                    ", callbackRtMillis=" + callbackRtMillis +
                    ", callbackTimeoutBudgetMillis=" + callbackTimeoutBudgetMillis +
                    ", triggerCapacityPerSecond=" + triggerCapacityPerSecond +
                    ", triggerBacklogPerSecond=" + triggerBacklogPerSecond +
                    ", triggerBacklogPerMinute=" + triggerBacklogPerMinute +
                    ", estimatedQueueFillSeconds=" + estimatedQueueFillSeconds +
                    ", estimatedQueueFillMinutes=" + estimatedQueueFillMinutes +
                    ", estimatedCallerRunsCallbacksPerSecond=" + estimatedCallerRunsCallbacksPerSecond +
                    ", estimatedCallerThreadSecondsPerSecond=" + estimatedCallerThreadSecondsPerSecond +
                    '}';
        }
    }

    public static final class PropagationStage {

        private final String title;
        private final String symptom;
        private final String whyItHappens;
        private final String anchor;

        public PropagationStage(String title, String symptom, String whyItHappens, String anchor) {
            this.title = title;
            this.symptom = symptom;
            this.whyItHappens = whyItHappens;
            this.anchor = anchor;
        }

        public String title() {
            return title;
        }

        public String symptom() {
            return symptom;
        }

        public String whyItHappens() {
            return whyItHappens;
        }

        public String anchor() {
            return anchor;
        }

        @Override
        public String toString() {
            return title + " | symptom=" + symptom + " | why=" + whyItHappens + " | anchor=" + anchor;
        }
    }

    public static final class EvidenceSignal {

        private final String signal;
        private final String meaning;
        private final String anchor;

        public EvidenceSignal(String signal, String meaning, String anchor) {
            this.signal = signal;
            this.meaning = meaning;
            this.anchor = anchor;
        }

        public String signal() {
            return signal;
        }

        public String meaning() {
            return meaning;
        }

        public String anchor() {
            return anchor;
        }

        @Override
        public String toString() {
            return signal + " | meaning=" + meaning + " | anchor=" + anchor;
        }
    }

    public static final class MitigationAction {

        private final String phase;
        private final String action;
        private final String rationale;
        private final String anchor;

        public MitigationAction(String phase, String action, String rationale, String anchor) {
            this.phase = phase;
            this.action = action;
            this.rationale = rationale;
            this.anchor = anchor;
        }

        public String phase() {
            return phase;
        }

        public String action() {
            return action;
        }

        public String rationale() {
            return rationale;
        }

        public String anchor() {
            return anchor;
        }

        @Override
        public String toString() {
            return phase + " | action=" + action + " | rationale=" + rationale + " | anchor=" + anchor;
        }
    }

    public static final class CallbackTimeoutCaseStudy {

        private final RuntimeProfile runtimeProfile;
        private final PressureMetrics pressureMetrics;
        private final List<String> codeAnchors;
        private final List<String> incidentTimeline;
        private final List<PropagationStage> propagationStages;
        private final List<EvidenceSignal> evidenceSignals;
        private final List<String> scopeBoundary;
        private final List<MitigationAction> mitigationActions;

        public CallbackTimeoutCaseStudy(RuntimeProfile runtimeProfile,
                                        PressureMetrics pressureMetrics,
                                        List<String> codeAnchors,
                                        List<String> incidentTimeline,
                                        List<PropagationStage> propagationStages,
                                        List<EvidenceSignal> evidenceSignals,
                                        List<String> scopeBoundary,
                                        List<MitigationAction> mitigationActions) {
            this.runtimeProfile = runtimeProfile;
            this.pressureMetrics = pressureMetrics;
            this.codeAnchors = codeAnchors;
            this.incidentTimeline = incidentTimeline;
            this.propagationStages = propagationStages;
            this.evidenceSignals = evidenceSignals;
            this.scopeBoundary = scopeBoundary;
            this.mitigationActions = mitigationActions;
        }

        public RuntimeProfile runtimeProfile() {
            return runtimeProfile;
        }

        public PressureMetrics pressureMetrics() {
            return pressureMetrics;
        }

        public List<String> codeAnchors() {
            return codeAnchors;
        }

        public List<String> incidentTimeline() {
            return incidentTimeline;
        }

        public List<String> incidentOutline() {
            return incidentTimeline;
        }

        public List<PropagationStage> propagationStages() {
            return propagationStages;
        }

        public List<EvidenceSignal> evidenceSignals() {
            return evidenceSignals;
        }

        public List<String> scopeBoundary() {
            return scopeBoundary;
        }

        public List<MitigationAction> mitigationActions() {
            return mitigationActions;
        }

        public List<String> stabilizationActions() {
            return Arrays.asList(
                    mitigationActions.get(0).action(),
                    mitigationActions.get(1).action(),
                    mitigationActions.get(2).action(),
                    mitigationActions.get(3).action(),
                    mitigationActions.get(4).action(),
                    mitigationActions.get(5).action()
            );
        }
    }
}
