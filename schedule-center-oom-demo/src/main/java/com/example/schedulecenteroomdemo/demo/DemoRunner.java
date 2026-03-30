package com.example.schedulecenteroomdemo.demo;

import com.example.schedulecenteroomdemo.oom.ScheduleCenterOomDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final ScheduleCenterOomDemoService scheduleCenterOomDemoService;

    public DemoRunner(ScheduleCenterOomDemoService scheduleCenterOomDemoService) {
        this.scheduleCenterOomDemoService = scheduleCenterOomDemoService;
    }

    @Override
    public void run(String... args) {
        ScheduleCenterOomDemoService.OomCaseResult result =
                scheduleCenterOomDemoService.accumulationOomCase();

        printTitle("1. OOM 是怎么恶化出来的");
        result.incidentSteps().forEach(System.out::println);

        printTitle("2. 关键压测口径");
        System.out.println(result.schedulerPressure());
        System.out.println(result.triggerPressure());

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
