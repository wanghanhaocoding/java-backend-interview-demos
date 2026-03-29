package com.example.schedulecenterscalingdemo.demo;

import com.example.schedulecenterscalingdemo.scaling.ScheduleCenterScalingDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final ScheduleCenterScalingDemoService scheduleCenterScalingDemoService;

    public DemoRunner(ScheduleCenterScalingDemoService scheduleCenterScalingDemoService) {
        this.scheduleCenterScalingDemoService = scheduleCenterScalingDemoService;
    }

    @Override
    public void run(String... args) {
        ScheduleCenterScalingDemoService.ScalingResult result =
                scheduleCenterScalingDemoService.clusterCoordinationDemo();

        printTitle("1. 多机去重与双线程池");
        result.steps().forEach(System.out::println);
        System.out.println("bucketClaims = " + result.bucketClaims());
        System.out.println("workerExecutions = " + result.workerExecutions());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
