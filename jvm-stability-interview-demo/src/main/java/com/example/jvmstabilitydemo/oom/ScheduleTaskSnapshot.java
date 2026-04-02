package com.example.jvmstabilitydemo.oom;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 这是更像业务里的“大对象”：
 * 任务 payload、触发结果、错误堆栈、诊断 headers 都被一起带进了兜底快照。
 */
public final class ScheduleTaskSnapshot {

    private final String jobId;
    private final String bizType;
    private final String workerRoute;
    private final String status;
    private final Map<String, String> diagnosticHeaders;
    private final byte[] taskPayload;
    private final byte[] responseBody;
    private final String retryDescription;
    private final String stackTracePreview;
    private final LocalDateTime failedAt;

    public ScheduleTaskSnapshot(String jobId,
                                String bizType,
                                String workerRoute,
                                String status,
                                Map<String, String> diagnosticHeaders,
                                byte[] taskPayload,
                                byte[] responseBody,
                                String retryDescription,
                                String stackTracePreview,
                                LocalDateTime failedAt) {
        this.jobId = jobId;
        this.bizType = bizType;
        this.workerRoute = workerRoute;
        this.status = status;
        this.diagnosticHeaders = Collections.unmodifiableMap(new HashMap<String, String>(diagnosticHeaders));
        this.taskPayload = taskPayload;
        this.responseBody = responseBody;
        this.retryDescription = retryDescription;
        this.stackTracePreview = stackTracePreview;
        this.failedAt = failedAt;
    }

    public static ScheduleTaskSnapshot from(ScheduleCenterTaskStormSimulator.TriggerTaskContext context,
                                            ScheduleCenterTaskStormSimulator.DispatchAttemptResult result) {
        return new ScheduleTaskSnapshot(
            context.jobId(),
            context.diagnosticHeaders().getOrDefault("scene", "UNKNOWN"),
            context.workerRoute(),
            context.status(),
            context.diagnosticHeaders(),
            context.taskPayload(),
            result.responseBody(),
            context.retryDescription(),
            result.stackTracePreview(),
            result.failedAt()
        );
    }

    public String jobId() {
        return jobId;
    }

    public String bizType() {
        return bizType;
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

    public byte[] responseBody() {
        return responseBody;
    }

    public String retryDescription() {
        return retryDescription;
    }

    public String stackTracePreview() {
        return stackTracePreview;
    }

    public LocalDateTime failedAt() {
        return failedAt;
    }
}
