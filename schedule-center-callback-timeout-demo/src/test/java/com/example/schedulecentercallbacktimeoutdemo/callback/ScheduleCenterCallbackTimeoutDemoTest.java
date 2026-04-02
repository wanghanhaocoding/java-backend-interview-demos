package com.example.schedulecentercallbacktimeoutdemo.callback;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ScheduleCenterCallbackTimeoutDemoTest {

    @Autowired
    private ScheduleCenterCallbackTimeoutDemoService scheduleCenterCallbackTimeoutDemoService;

    @Test
    void shouldModelCallbackBacklogAndCallerRunsDelayPropagation() {
        ScheduleCenterCallbackTimeoutDemoService.CallbackTimeoutCaseStudy result =
                scheduleCenterCallbackTimeoutDemoService.callbackTimeoutCaseStudy();

        assertThat(result.runtimeProfile().schedulerFixedRateMillis()).isEqualTo(1000);
        assertThat(result.runtimeProfile().schedulerQueueCapacity()).isEqualTo(99999);
        assertThat(result.runtimeProfile().triggerQueueCapacity()).isEqualTo(99999);
        assertThat(result.runtimeProfile().rejectionPolicy()).isEqualTo("CallerRunsPolicy");
        assertThat(result.pressureMetrics().sliceArrivalPerSecond()).isEqualTo(10);
        assertThat(result.pressureMetrics().callbackArrivalPerSecond()).isEqualTo(120);
        assertThat(result.pressureMetrics().callbackRtMillis()).isEqualTo(3000);
        assertThat(result.pressureMetrics().callbackTimeoutBudgetMillis()).isEqualTo(2000);
        assertThat(result.pressureMetrics().triggerCapacityPerSecond()).isCloseTo(33.33, within(0.01));
        assertThat(result.pressureMetrics().triggerBacklogPerSecond()).isCloseTo(86.67, within(0.01));
        assertThat(result.pressureMetrics().triggerBacklogPerMinute()).isGreaterThan(5000.0);
        assertThat(result.pressureMetrics().estimatedQueueFillSeconds()).isGreaterThan(1000.0);
        assertThat(result.pressureMetrics().estimatedQueueFillMinutes()).isCloseTo(19.23, within(0.01));
        assertThat(result.pressureMetrics().estimatedCallerThreadSecondsPerSecond()).isGreaterThan(200.0);
        assertThat(result.propagationStages()).hasSize(4);
        assertThat(result.propagationStages()).anyMatch(stage -> stage.whyItHappens().contains("CallerRunsPolicy"));
        assertThat(result.evidenceSignals()).anyMatch(signal -> signal.signal().contains("triggerPool.queueSize"));
        assertThat(result.mitigationActions()).anyMatch(action -> action.phase().equals("止血"));
        assertThat(result.mitigationActions()).anyMatch(action -> action.phase().equals("根治"));
    }

    @Test
    void shouldKeepCallbackTimeoutScopeSeparateFromOtherXtTimerIncidentDemos() {
        ScheduleCenterCallbackTimeoutDemoService.CallbackTimeoutCaseStudy result =
                scheduleCenterCallbackTimeoutDemoService.callbackTimeoutCaseStudy();

        assertThat(result.codeAnchors()).contains(
                "SchedulerWorker @Scheduled(fixedRate = 1000)",
                "TriggerPoolTask.runExecutor",
                "taskMapper.getTasksByTimerIdUnix",
                "ExecutorWorker.executeTimerCallBack",
                "AsyncPool.triggerPoolExecutor"
        );
        assertThat(result.scopeBoundary()).anyMatch(item -> item.contains("Full GC"));
        assertThat(result.scopeBoundary()).anyMatch(item -> item.contains("OOM"));
        assertThat(result.scopeBoundary()).anyMatch(item -> item.contains("CPU"));
        assertThat(result.scopeBoundary()).anyMatch(item -> item.contains("callback timeout"));
    }
}
