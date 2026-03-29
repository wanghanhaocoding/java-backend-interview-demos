package com.example.schedulecenterscalingdemo.scaling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ScheduleCenterScalingDemoTest {

    @Autowired
    private ScheduleCenterScalingDemoService scheduleCenterScalingDemoService;

    @Test
    void shouldDeduplicateBucketsAndExecuteEachTaskOnce() {
        ScheduleCenterScalingDemoService.ScalingResult result =
                scheduleCenterScalingDemoService.clusterCoordinationDemo();

        List<String> executedTasks = new ArrayList<>();
        result.workerExecutions().values().forEach(executedTasks::addAll);

        assertThat(result.bucketClaims()).hasSize(3);
        assertThat(executedTasks).doesNotHaveDuplicates();
        assertThat(executedTasks).containsExactlyInAnyOrder(
                "COLLECT-1001",
                "COLLECT-1002",
                "COLLECT-1003",
                "PLAN-2001",
                "PLAN-2002",
                "BILL-3001"
        );
        assertThat(result.steps()).anyMatch(step -> step.contains("背压"));
    }
}
