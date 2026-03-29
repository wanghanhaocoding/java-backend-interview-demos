package com.example.treasuryplancollectiondemo.demo;

import com.example.treasuryplancollectiondemo.plan.TreasuryPlanCollectionDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final TreasuryPlanCollectionDemoService treasuryPlanCollectionDemoService;

    public DemoRunner(TreasuryPlanCollectionDemoService treasuryPlanCollectionDemoService) {
        this.treasuryPlanCollectionDemoService = treasuryPlanCollectionDemoService;
    }

    @Override
    public void run(String... args) {
        TreasuryPlanCollectionDemoService.PlanCollectionResult result =
                treasuryPlanCollectionDemoService.dailyPlanCollectionDemo();

        printTitle("1. 日计划、预算与归集");
        result.steps().forEach(System.out::println);
        System.out.println("acceptedPlanIds = " + result.acceptedPlanIds());
        System.out.println("rejectedPlanReasons = " + result.rejectedPlanReasons());
        System.out.println("windowAssignments = " + result.windowAssignments());
        System.out.println("executionOrder = " + result.executionOrder());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
