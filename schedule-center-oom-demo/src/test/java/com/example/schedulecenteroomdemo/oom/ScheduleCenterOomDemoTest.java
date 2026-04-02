package com.example.schedulecenteroomdemo.oom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ScheduleCenterOomDemoTest {

    @Autowired
    private ScheduleCenterOomDemoService scheduleCenterOomDemoService;

    @Test
    void shouldExplainAccumulationStyleOomWithSchedulerAndTriggerBacklogs() {
        ScheduleCenterOomDemoService.OomCaseResult result =
                scheduleCenterOomDemoService.accumulationOomCase();

        assertThat(result.runtimeProfile().schedulerFixedRateMillis()).isEqualTo(1000);
        assertThat(result.runtimeProfile().schedulerQueueCapacity()).isEqualTo(99999);
        assertThat(result.schedulerPressure().arrivalPerSecond()).isEqualTo(10);
        assertThat(result.schedulerPressure().backlogPerMinute()).isGreaterThan(490.0);
        assertThat(result.triggerPressure().arrivalPerSecond()).isEqualTo(120);
        assertThat(result.triggerPressure().capacityPerSecond()).isEqualTo(50.0);
        assertThat(result.triggerPressure().backlogPerMinute()).isCloseTo(4200.0, within(0.1));
        assertThat(result.triggerPressure().estimatedRetainedMbPerMinute()).isGreaterThan(70.0);
        assertThat(result.incidentSteps()).anyMatch(step -> step.contains("taskMapper.getTasksByTimeRange"));
        assertThat(result.fixes()).anyMatch(step -> step.contains("有限长度"));
    }
}
