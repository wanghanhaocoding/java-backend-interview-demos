package com.example.schedulecentertriggerdemo.demo;

import com.example.schedulecentertriggerdemo.trigger.ScheduleCenterTriggerDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final ScheduleCenterTriggerDemoService scheduleCenterTriggerDemoService;

    public DemoRunner(ScheduleCenterTriggerDemoService scheduleCenterTriggerDemoService) {
        this.scheduleCenterTriggerDemoService = scheduleCenterTriggerDemoService;
    }

    @Override
    public void run(String... args) {
        ScheduleCenterTriggerDemoService.TriggerPlanResult result =
                scheduleCenterTriggerDemoService.minuteBucketTriggerDemo();

        printTitle("1. 分钟分片与滑动时间窗");
        result.steps().forEach(System.out::println);
        System.out.println("bucketTasks = " + result.bucketTasks());
        System.out.println("firedTaskIds = " + result.firedTaskIds());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
