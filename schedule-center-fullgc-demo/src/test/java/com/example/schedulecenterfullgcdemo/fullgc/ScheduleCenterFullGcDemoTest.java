package com.example.schedulecenterfullgcdemo.fullgc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ScheduleCenterFullGcDemoTest {

    @Autowired
    private ScheduleCenterFullGcDemoService scheduleCenterFullGcDemoService;

    @Test
    void shouldExplainWhyScheduleCenterHitsFrequentFullGcBeforeOom() {
        ScheduleCenterFullGcDemoService.FullGcCaseResult result =
                scheduleCenterFullGcDemoService.frequentFullGcCase();

        assertThat(result.runtimeProfile().bucketsPerMinute()).isEqualTo(5);
        assertThat(result.runtimeProfile().schedulerFixedRateMillis()).isEqualTo(1000);
        assertThat(result.runtimeProfile().schedulerQueueCapacity()).isEqualTo(99999);
        assertThat(result.pressureMetrics().slicesSubmittedPerSecond()).isEqualTo(10);
        assertThat(result.pressureMetrics().schedulerCapacityPerSecond()).isCloseTo(1.67, within(0.01));
        assertThat(result.pressureMetrics().steadyStateConcurrentSlices()).isEqualTo(600);
        assertThat(result.pressureMetrics().queuedSlices()).isEqualTo(500);
        assertThat(result.pressureMetrics().estimatedRetainedMb()).isGreaterThan(200.0);
        assertThat(result.incidentSteps()).anyMatch(step -> step.contains("TriggerWorker.work"));
        assertThat(result.diagnosisCommands()).contains("jstat -gcutil <pid> 1000 20");
        assertThat(result.fixes()).anyMatch(step -> step.contains("99999"));
    }
}
