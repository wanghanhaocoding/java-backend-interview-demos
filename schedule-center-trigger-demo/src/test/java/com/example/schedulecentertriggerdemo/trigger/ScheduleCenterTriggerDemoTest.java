package com.example.schedulecentertriggerdemo.trigger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ScheduleCenterTriggerDemoTest {

    @Autowired
    private ScheduleCenterTriggerDemoService scheduleCenterTriggerDemoService;

    @Test
    void shouldRegisterTasksIntoMinuteBucketsAndFireInTimeOrder() {
        ScheduleCenterTriggerDemoService.TriggerPlanResult result =
                scheduleCenterTriggerDemoService.minuteBucketTriggerDemo();

        assertThat(result.bucketTasks()).isNotEmpty();
        assertThat(result.firedTaskIds()).containsExactly(
                "PLAN-090001",
                "PLAN-090002",
                "PLAN-090003",
                "PLAN-090004"
        );
        assertThat(result.steps()).anyMatch(step -> step.contains("扫描窗口"));
    }
}
