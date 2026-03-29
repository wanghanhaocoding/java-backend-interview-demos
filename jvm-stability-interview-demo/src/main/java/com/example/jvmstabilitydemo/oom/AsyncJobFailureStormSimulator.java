package com.example.jvmstabilitydemo.oom;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟 AsyncJobCenter worker 在“上游回调超时 + 失败重试堆积”场景下的行为。
 */
public class AsyncJobFailureStormSimulator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LeakyLocalRetrySnapshotBuffer fallbackBuffer;

    public AsyncJobFailureStormSimulator(LeakyLocalRetrySnapshotBuffer fallbackBuffer) {
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
            RetryableTaskContext context = buildContext(round, i, payloadKb);
            CallbackAttemptResult result = invokeBankCallback(context);
            if (!result.success()) {
                failed++;
                fallbackBuffer.store(JobCallbackSnapshot.from(context, result));
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

    private RetryableTaskContext buildContext(int round, int index, int payloadKb) {
        String jobId = "AJC-RETRY-" + round + '-' + index;
        Map<String, String> headers = new HashMap<>();
        headers.put("bankCode", index % 2 == 0 ? "ICBC" : "CCB");
        headers.put("scene", index % 3 == 0 ? "RECEIPT_MAKE" : "BUDGET_ANALYSIS");
        headers.put("traceId", UUID.randomUUID().toString());
        headers.put("tenantId", "treasury-group-A");
        return new RetryableTaskContext(
            jobId,
            "job-config-async-callback",
            "https://bank-gateway/callback/receipt",
            "RETRYING",
            headers,
            new byte[payloadKb * 1024],
            "ticketNo=T" + round + index + ", bizNo=BIZ" + round + index + ", retryTimes=3"
        );
    }

    private CallbackAttemptResult invokeBankCallback(RetryableTaskContext context) {
        String stack = "java.net.SocketTimeoutException: Read timed out\n"
            + "\tat com.example.asyncjobcenter.callback.BankCallbackClient.execute(BankCallbackClient.java:87)\n"
            + "\tat com.example.asyncjobcenter.worker.AsyncCallbackWorker.handle(AsyncCallbackWorker.java:143)";
        byte[] response = ("HTTP/1.1 504 Gateway Timeout, jobId=" + context.jobId()).repeat(48).getBytes();
        return new CallbackAttemptResult(false, response, stack, LocalDateTime.now());
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
            return "%s WARN AsyncJobCallbackWorker - round=%d pulled=%d failed=%d localFallbackBuffer=%d retainedPayloadMb=%d heapBeforeMb=%d heapAfterMb=%d hint=%s"
                .formatted(LocalDateTime.now().format(TIME_FORMATTER), round, pulledTasks, failedTasks,
                    fallbackBufferSize, retainedPayloadMb, heapBeforeMb, heapAfterMb, incidentHint);
        }

        @Override
        public String toString() {
            return toLogLine();
        }
    }

    public record RetryableTaskContext(String jobId,
                                       String configKey,
                                       String callbackUrl,
                                       String status,
                                       Map<String, String> diagnosticHeaders,
                                       byte[] taskPayload,
                                       String retryDescription) {
    }

    public record CallbackAttemptResult(boolean success,
                                        byte[] responseBody,
                                        String stackTracePreview,
                                        LocalDateTime failedAt) {
    }
}
