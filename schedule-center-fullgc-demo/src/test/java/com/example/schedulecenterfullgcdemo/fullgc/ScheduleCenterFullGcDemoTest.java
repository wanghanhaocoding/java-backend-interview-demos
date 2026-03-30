package com.example.schedulecenterfullgcdemo.fullgc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ScheduleCenterFullGcDemoTest {

    @Autowired
    private ScheduleCenterFullGcDemoService scheduleCenterFullGcDemoService;

    @Test
    void shouldExplainWhyScheduleCenterHitsFrequentFullGcBeforeOom() {
        ScheduleCenterFullGcDemoService.FullGcCaseResult result =
                scheduleCenterFullGcDemoService.frequentFullGcCase();

        assertThat(result.pressureMetrics().slicesSubmittedPerSecond()).isEqualTo(10);
        assertThat(result.pressureMetrics().steadyStateConcurrentSlices()).isEqualTo(600);
        assertThat(result.pressureMetrics().queuedSlices()).isEqualTo(500);
        assertThat(result.pressureMetrics().estimatedRetainedMb()).isGreaterThan(180.0);
        assertThat(result.incidentSteps()).anyMatch(step -> step.contains("Full GC"));
        assertThat(result.diagnosisCommands()).contains("jstat -gcutil <pid> 1000 20");
        assertThat(result.fixes()).anyMatch(step -> step.contains("背压"));
    }
}
