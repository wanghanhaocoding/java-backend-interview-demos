package com.example.jvmstabilitydemo.oom;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟 ScheduleCenter 在“任务触发失败 + 本地补偿快照持续堆积”场景下的行为。
 */
public class ScheduleCenterTaskStormSimulator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LeakyScheduleSnapshotBuffer fallbackBuffer;

    public ScheduleCenterTaskStormSimulator(LeakyScheduleSnapshotBuffer fallbackBuffer) {
        this.fallbackBuffer = fallbackBuffer;
    }

    public SimulationRoundResult previewOneRound() {
        return executeRound(1, 24, 128, false);
    }

    public void runUntilOom() {
        int round = 0;
        while (true) {
            SimulationRoundResult result = executeRound(++round, 180, 256, true);
            if (round == 1 || round % 5 == 0) {
                System.out.println(result.toLogLine());
            }
        }
    }

    private SimulationRoundResult executeRound(int round, int taskCount, int payloadKb, boolean pressureMode) {
        long beforeBytes = usedHeapBytes();
        int failed = 0;

        for (int i = 0; i < taskCount; i++) {
            TriggerTaskContext context = buildContext(round, i, payloadKb);
            DispatchAttemptResult result = dispatchScheduledTask(context);
            if (!result.success()) {
                failed++;
                fallbackBuffer.store(ScheduleTaskSnapshot.from(context, result));
            }
        }

        if (pressureMode) {
            sleepSilently(40);
        }

        long afterBytes = usedHeapBytes();
        return new SimulationRoundResult(
            round,
            taskCount,
            failed,
            fallbackBuffer.snapshotCount(),
            fallbackBuffer.retainedPayloadBytes() / 1024 / 1024,
            beforeBytes / 1024 / 1024,
            afterBytes / 1024 / 1024,
            fallbackBuffer.latestIncidentHint()
        );
    }

    private TriggerTaskContext buildContext(int round, int index, int payloadKb) {
        String jobId = "SC-TRIGGER-" + round + '-' + index;
        Map<String, String> headers = new HashMap<>();
        headers.put("bucket", "2026-03-30 10:0" + (index % 6) + '_' + (index % 4));
        headers.put("scene", index % 3 == 0 ? "RECEIPT_TIMEOUT_SCAN" : "BUDGET_WINDOW_TRIGGER");
        headers.put("traceId", UUID.randomUUID().toString());
        headers.put("tenantId", "treasury-group-A");
        return new TriggerTaskContext(
            jobId,
            "schedule-center-minute-trigger",
            "schedulerPool->workerPool",
            "TRIGGER_FAILED",
            headers,
            new byte[payloadKb * 1024],
            "planId=PLAN" + round + index + ", bucket=10:0" + (index % 6) + ", retryWindow=3"
        );
    }

    private DispatchAttemptResult dispatchScheduledTask(TriggerTaskContext context) {
        String stack = "java.net.SocketTimeoutException: Read timed out\n"
            + "\tat com.example.schedulecenter.dispatch.PlanDispatchClient.execute(PlanDispatchClient.java:91)\n"
            + "\tat com.example.schedulecenter.worker.ScheduleTriggerWorker.handle(ScheduleTriggerWorker.java:156)";
        byte[] response = ("dispatch failed: downstream timeout, jobId=" + context.jobId()).repeat(48).getBytes();
        return new DispatchAttemptResult(false, response, stack, LocalDateTime.now());
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record SimulationRoundResult(int round,
                                        int pulledTasks,
                                        int failedTasks,
                                        int fallbackBufferSize,
                                        long retainedPayloadMb,
                                        long heapBeforeMb,
                                        long heapAfterMb,
                                        String incidentHint) {

        public String toLogLine() {
            return "%s WARN ScheduleTriggerWorker - round=%d pulled=%d failed=%d localFallbackBuffer=%d retainedPayloadMb=%d heapBeforeMb=%d heapAfterMb=%d hint=%s"
                .formatted(LocalDateTime.now().format(TIME_FORMATTER), round, pulledTasks, failedTasks,
                    fallbackBufferSize, retainedPayloadMb, heapBeforeMb, heapAfterMb, incidentHint);
        }

        @Override
        public String toString() {
            return toLogLine();
        }
    }

    public record TriggerTaskContext(String jobId,
                                     String scheduleKey,
                                     String workerRoute,
                                     String status,
                                     Map<String, String> diagnosticHeaders,
                                     byte[] taskPayload,
                                     String retryDescription) {
    }

    public record DispatchAttemptResult(boolean success,
                                        byte[] responseBody,
                                        String stackTracePreview,
                                        LocalDateTime failedAt) {
    }
}
