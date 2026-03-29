package com.example.asyncjobcentercoredemo.demo;

import com.example.asyncjobcentercoredemo.core.AsyncJobCenterCoreDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final AsyncJobCenterCoreDemoService asyncJobCenterCoreDemoService;

    public DemoRunner(AsyncJobCenterCoreDemoService asyncJobCenterCoreDemoService) {
        this.asyncJobCenterCoreDemoService = asyncJobCenterCoreDemoService;
    }

    @Override
    public void run(String... args) {
        AsyncJobCenterCoreDemoService.CoreFlowResult result =
                asyncJobCenterCoreDemoService.serverWorkerLifecycleDemo();

        printTitle("1. server + worker 最小链路");
        result.steps().forEach(System.out::println);
        System.out.println("statusTimeline = " + result.statusTimeline());
        System.out.println("stageTimeline = " + result.stageTimeline());
        System.out.println("tableTasks = " + result.tableTasks());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
