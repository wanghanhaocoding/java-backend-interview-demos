package com.example.cpuhightroubleshootingdemo.cpu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "teaching.runner.enabled=false",
        "demo.scenario.auto-start=none",
        "demo.scenario.log-interval-seconds=60"
})
class CpuScenarioRuntimeServiceTest {

    @Autowired
    private CpuScenarioRuntimeService cpuScenarioRuntimeService;

    @AfterEach
    void tearDown() {
        cpuScenarioRuntimeService.stopScenario();
    }

    @Test
    void shouldStartAndStopEmptyScanScenario() throws Exception {
        CpuScenarioRuntimeService.ScenarioStatus started =
                cpuScenarioRuntimeService.startScenario("empty-scan", Integer.valueOf(0));

        assertThat(started.isRunning()).isTrue();
        assertThat(started.getActiveScenario()).isEqualTo("empty-scan");

        CpuScenarioRuntimeService.ScenarioStatus running = awaitMetric("emptyScans");
        assertThat(running.getHotThreadName()).isEqualTo(XtimerEmptyScanCpuDemo.HOT_THREAD_NAME);
        assertThat(((Number) running.getMetrics().get("emptyScans")).longValue()).isGreaterThan(0L);

        CpuScenarioRuntimeService.ScenarioStatus stopped = cpuScenarioRuntimeService.stopScenario();
        assertThat(stopped.isRunning()).isFalse();
        assertThat(stopped.getStopReason()).isEqualTo("manual stop");
    }

    @Test
    void shouldAutoStopFallbackStormScenarioWhenDurationEnds() throws Exception {
        cpuScenarioRuntimeService.startScenario("fallback-storm", Integer.valueOf(1));

        CpuScenarioRuntimeService.ScenarioStatus completed = awaitStopped();
        assertThat(completed.isRunning()).isFalse();
        assertThat(completed.getActiveScenario()).isEqualTo("fallback-storm");
        assertThat(completed.getStopReason()).isEqualTo("duration elapsed");
        assertThat(((Number) completed.getMetrics().get("attempts")).longValue()).isGreaterThan(0L);
    }

    private CpuScenarioRuntimeService.ScenarioStatus awaitMetric(String metricKey) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            CpuScenarioRuntimeService.ScenarioStatus status = cpuScenarioRuntimeService.currentStatus();
            Object metricValue = status.getMetrics().get(metricKey);
            if (metricValue instanceof Number && ((Number) metricValue).longValue() > 0L) {
                return status;
            }
            Thread.sleep(100L);
        }
        return cpuScenarioRuntimeService.currentStatus();
    }

    private CpuScenarioRuntimeService.ScenarioStatus awaitStopped() throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            CpuScenarioRuntimeService.ScenarioStatus status = cpuScenarioRuntimeService.currentStatus();
            if (!status.isRunning()) {
                return status;
            }
            Thread.sleep(100L);
        }
        return cpuScenarioRuntimeService.currentStatus();
    }
}
