package com.example.schedulecenteroomdemo.oom;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleCenterOomDemoService {

    private static final int SCHEDULER_ARRIVAL_PER_SECOND = 10;
    private static final int SLICE_HOLD_SECONDS = 60;
    private static final int SCHEDULER_MAX_THREADS = 100;
    private static final int TRIGGER_TASKS_PER_SLICE_PER_SECOND = 12;
    private static final int TRIGGER_MAX_THREADS = 100;
    private static final int CALLBACK_SECONDS = 2;
    private static final int RETAINED_KB_PER_TRIGGER_TASK = 18;
    private static final int RETAINED_KB_PER_QUEUED_SLICE = 384;

    public OomCaseResult accumulationOomCase() {
        double schedulerCapacityPerSecond = (double) SCHEDULER_MAX_THREADS / SLICE_HOLD_SECONDS;
        double schedulerBacklogPerMinute = (SCHEDULER_ARRIVAL_PER_SECOND - schedulerCapacityPerSecond) * 60;
        int triggerArrivalPerSecond = SCHEDULER_ARRIVAL_PER_SECOND * TRIGGER_TASKS_PER_SLICE_PER_SECOND;
        double triggerCapacityPerSecond = (double) TRIGGER_MAX_THREADS / CALLBACK_SECONDS;
        double triggerBacklogPerMinute = (triggerArrivalPerSecond - triggerCapacityPerSecond) * 60;
        double triggerRetainedMbPerMinute = triggerBacklogPerMinute * RETAINED_KB_PER_TRIGGER_TASK / 1024.0;
        double schedulerRetainedMb = schedulerBacklogPerMinute * RETAINED_KB_PER_QUEUED_SLICE / 1024.0;

        SchedulerPressure schedulerPressure = new SchedulerPressure(
                SCHEDULER_ARRIVAL_PER_SECOND,
                SLICE_HOLD_SECONDS,
                SCHEDULER_MAX_THREADS,
                schedulerCapacityPerSecond,
                schedulerBacklogPerMinute,
                schedulerRetainedMb
        );

        TriggerPressure triggerPressure = new TriggerPressure(
                triggerArrivalPerSecond,
                TRIGGER_MAX_THREADS,
                CALLBACK_SECONDS,
                triggerCapacityPerSecond,
                triggerBacklogPerMinute,
                triggerRetainedMbPerMinute
        );

        List<String> incidentSteps = List.of(
                "1. 调度层每秒提交 10 个分钟分片扫描任务，但单个任务会持续接近 60 秒",
                "2. schedulerPool 的处理能力只有约 1.67 个分片每秒，所以每分钟仍会新增约 500 个待执行分片",
                "3. 每个分片又会持续向 triggerPool 投递到期任务，回调一慢，triggerPool 立刻跟着积压",
                "4. 线程池队列里开始堆 Future、集合对象、任务 DTO、HTTP 回调上下文和扫描结果",
                "5. Redis 异常触发 DB fallback 时，单次扫描拿到的结果集更大，堆里对象数量进一步被放大",
                "6. 现象通常先是 Full GC 越来越频繁，继续恶化后老年代回不下来，最终出现 OOM"
        );

        List<String> diagnosisSteps = List.of(
                "1. 先看 schedulerPool 和 triggerPool 的 queueSize 是否持续单向增长，确认这是积压不是瞬时尖峰",
                "2. 再看 Redis RT、DB fallback RT 和 callback RT，确认是不是某条异常链路把消费能力压垮了",
                "3. 用 GC 指标和对象直方图确认堆里主要是任务对象、集合、Future 和上下文，而不是典型静态引用泄漏",
                "4. 最后回到代码链路，把队列过大、长生命周期扫描和 fallback 放大量串成完整根因"
        );

        List<String> diagnosisCommands = List.of(
                "jstat -gcutil <pid> 1000 20",
                "jcmd <pid> GC.heap_info",
                "jmap -histo:live <pid>"
        );

        List<String> fixes = List.of(
                "1. 先扩容节点，把当前分片竞争压力快速摊薄",
                "2. 给 trigger 投递做限速和背压，避免慢回调继续把对象堆在内存里",
                "3. 把 schedulerPool 和 triggerPool 的超大队列改成有限长度",
                "4. Redis fallback 查询增加上限和更严格过滤，避免异常路径放大量过大",
                "5. 缩短单个分片扫描任务生命周期，把一分钟长任务切成更短批次"
        );

        return new OomCaseResult(incidentSteps, schedulerPressure, triggerPressure, diagnosisSteps, diagnosisCommands, fixes);
    }

    public record SchedulerPressure(
            int arrivalPerSecond,
            int sliceHoldSeconds,
            int maxThreads,
            double capacityPerSecond,
            double backlogPerMinute,
            double estimatedRetainedMb
    ) {
    }

    public record TriggerPressure(
            int arrivalPerSecond,
            int maxThreads,
            int callbackSeconds,
            double capacityPerSecond,
            double backlogPerMinute,
            double estimatedRetainedMbPerMinute
    ) {
    }

    public record OomCaseResult(
            List<String> incidentSteps,
            SchedulerPressure schedulerPressure,
            TriggerPressure triggerPressure,
            List<String> diagnosisSteps,
            List<String> diagnosisCommands,
            List<String> fixes
    ) {
    }
}
