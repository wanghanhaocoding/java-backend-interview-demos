package com.example.jvmstabilitydemo.oom;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 这是更像业务里的“大对象”：
 * 任务 payload、触发结果、错误堆栈、诊断 headers 都被一起带进了兜底快照。
 */
public record ScheduleTaskSnapshot(String jobId,
                                   String bizType,
                                   String workerRoute,
                                   String status,
                                   Map<String, String> diagnosticHeaders,
                                   byte[] taskPayload,
                                   byte[] responseBody,
                                   String retryDescription,
                                   String stackTracePreview,
                                   LocalDateTime failedAt) {

    public static ScheduleTaskSnapshot from(ScheduleCenterTaskStormSimulator.TriggerTaskContext context,
                                            ScheduleCenterTaskStormSimulator.DispatchAttemptResult result) {
        return new ScheduleTaskSnapshot(
            context.jobId(),
            context.diagnosticHeaders().getOrDefault("scene", "UNKNOWN"),
            context.workerRoute(),
            context.status(),
            Map.copyOf(context.diagnosticHeaders()),
            context.taskPayload(),
            result.responseBody(),
            context.retryDescription(),
            result.stackTracePreview(),
            result.failedAt()
        );
    }
}
