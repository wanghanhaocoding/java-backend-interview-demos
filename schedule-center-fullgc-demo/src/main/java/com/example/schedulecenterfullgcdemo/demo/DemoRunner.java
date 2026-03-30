package com.example.schedulecenterfullgcdemo.demo;

import com.example.schedulecenterfullgcdemo.fullgc.ScheduleCenterFullGcDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final ScheduleCenterFullGcDemoService scheduleCenterFullGcDemoService;

    public DemoRunner(ScheduleCenterFullGcDemoService scheduleCenterFullGcDemoService) {
        this.scheduleCenterFullGcDemoService = scheduleCenterFullGcDemoService;
    }

    @Override
    public void run(String... args) {
        ScheduleCenterFullGcDemoService.FullGcCaseResult result =
                scheduleCenterFullGcDemoService.frequentFullGcCase();

        printTitle("1. Full GC 是怎么出现的");
        result.incidentSteps().forEach(System.out::println);

        printTitle("2. 关键压测口径");
        System.out.println(result.pressureMetrics());

        printTitle("3. 怎么排查");
        result.diagnosisSteps().forEach(System.out::println);
        System.out.println("commands = " + result.diagnosisCommands());

        printTitle("4. 怎么解决");
        result.fixes().forEach(System.out::println);
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
