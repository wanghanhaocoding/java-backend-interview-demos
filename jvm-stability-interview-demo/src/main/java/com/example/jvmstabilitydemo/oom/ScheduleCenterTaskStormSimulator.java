package com.example.jvmstabilitydemo.oom;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
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
        byte[] response = buildResponseBody(context.jobId());
        return new DispatchAttemptResult(false, response, stack, LocalDateTime.now());
    }

    private byte[] buildResponseBody(String jobId) {
        String fragment = "dispatch failed: downstream timeout, jobId=" + jobId;
        StringBuilder builder = new StringBuilder(fragment.length() * 48);
        for (int i = 0; i < 48; i++) {
            builder.append(fragment);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
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

    public static final class SimulationRoundResult {

        private final int round;
        private final int pulledTasks;
        private final int failedTasks;
        private final int fallbackBufferSize;
        private final long retainedPayloadMb;
        private final long heapBeforeMb;
        private final long heapAfterMb;
        private final String incidentHint;

        public SimulationRoundResult(int round,
                                     int pulledTasks,
                                     int failedTasks,
                                     int fallbackBufferSize,
                                     long retainedPayloadMb,
                                     long heapBeforeMb,
                                     long heapAfterMb,
                                     String incidentHint) {
            this.round = round;
            this.pulledTasks = pulledTasks;
            this.failedTasks = failedTasks;
            this.fallbackBufferSize = fallbackBufferSize;
            this.retainedPayloadMb = retainedPayloadMb;
            this.heapBeforeMb = heapBeforeMb;
            this.heapAfterMb = heapAfterMb;
            this.incidentHint = incidentHint;
        }

        public String toLogLine() {
            return String.format("%s WARN ScheduleTriggerWorker - round=%d pulled=%d failed=%d localFallbackBuffer=%d retainedPayloadMb=%d heapBeforeMb=%d heapAfterMb=%d hint=%s",
                LocalDateTime.now().format(TIME_FORMATTER), round, pulledTasks, failedTasks,
                    fallbackBufferSize, retainedPayloadMb, heapBeforeMb, heapAfterMb, incidentHint);
        }

        @Override
        public String toString() {
            return toLogLine();
        }

        public int round() {
            return round;
        }

        public int pulledTasks() {
            return pulledTasks;
        }

        public int failedTasks() {
            return failedTasks;
        }

        public int fallbackBufferSize() {
            return fallbackBufferSize;
        }

        public long retainedPayloadMb() {
            return retainedPayloadMb;
        }

        public long heapBeforeMb() {
            return heapBeforeMb;
        }

        public long heapAfterMb() {
            return heapAfterMb;
        }

        public String incidentHint() {
            return incidentHint;
        }
    }

    public static final class TriggerTaskContext {

        private final String jobId;
        private final String scheduleKey;
        private final String workerRoute;
        private final String status;
        private final Map<String, String> diagnosticHeaders;
        private final byte[] taskPayload;
        private final String retryDescription;

        public TriggerTaskContext(String jobId,
                                  String scheduleKey,
                                  String workerRoute,
                                  String status,
                                  Map<String, String> diagnosticHeaders,
                                  byte[] taskPayload,
                                  String retryDescription) {
            this.jobId = jobId;
            this.scheduleKey = scheduleKey;
            this.workerRoute = workerRoute;
            this.status = status;
            this.diagnosticHeaders = diagnosticHeaders;
            this.taskPayload = taskPayload;
            this.retryDescription = retryDescription;
        }

        public String jobId() {
            return jobId;
        }

        public String scheduleKey() {
            return scheduleKey;
        }

        public String workerRoute() {
            return workerRoute;
        }

        public String status() {
            return status;
        }

        public Map<String, String> diagnosticHeaders() {
            return diagnosticHeaders;
        }

        public byte[] taskPayload() {
            return taskPayload;
        }

        public String retryDescription() {
            return retryDescription;
        }
    }

    public static final class DispatchAttemptResult {

        private final boolean success;
        private final byte[] responseBody;
        private final String stackTracePreview;
        private final LocalDateTime failedAt;

        public DispatchAttemptResult(boolean success,
                                     byte[] responseBody,
                                     String stackTracePreview,
                                     LocalDateTime failedAt) {
            this.success = success;
            this.responseBody = responseBody;
            this.stackTracePreview = stackTracePreview;
            this.failedAt = failedAt;
        }

        public boolean success() {
            return success;
        }

        public byte[] responseBody() {
            return responseBody;
        }

        public String stackTracePreview() {
            return stackTracePreview;
        }

        public LocalDateTime failedAt() {
            return failedAt;
        }
    }
}
