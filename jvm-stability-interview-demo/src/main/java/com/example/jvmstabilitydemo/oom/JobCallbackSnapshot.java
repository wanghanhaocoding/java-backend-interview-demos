package com.example.jvmstabilitydemo.oom;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 这是更像业务里的“大对象”：
 * 任务 payload、回调 response、错误堆栈、诊断 headers 都被一起带进了兜底快照。
 */
public record JobCallbackSnapshot(String jobId,
                                  String bizType,
                                  String callbackUrl,
                                  String status,
                                  Map<String, String> diagnosticHeaders,
                                  byte[] taskPayload,
                                  byte[] responseBody,
                                  String retryDescription,
                                  String stackTracePreview,
                                  LocalDateTime failedAt) {

    public static JobCallbackSnapshot from(AsyncJobFailureStormSimulator.RetryableTaskContext context,
                                           AsyncJobFailureStormSimulator.CallbackAttemptResult result) {
        return new JobCallbackSnapshot(
            context.jobId(),
            context.diagnosticHeaders().getOrDefault("scene", "UNKNOWN"),
            context.callbackUrl(),
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
