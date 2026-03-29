package com.example.asyncjobcenterretryshardingdemo.demo;

import com.example.asyncjobcenterretryshardingdemo.retry.AsyncJobCenterRetryShardingDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final AsyncJobCenterRetryShardingDemoService asyncJobCenterRetryShardingDemoService;

    public DemoRunner(AsyncJobCenterRetryShardingDemoService asyncJobCenterRetryShardingDemoService) {
        this.asyncJobCenterRetryShardingDemoService = asyncJobCenterRetryShardingDemoService;
    }

    @Override
    public void run(String... args) {
        AsyncJobCenterRetryShardingDemoService.RetryShardingResult result =
                asyncJobCenterRetryShardingDemoService.retryAndRollingShardDemo();

        printTitle("1. 重试、分表与锁演进");
        result.steps().forEach(System.out::println);
        System.out.println("tableRoutes = " + result.tableRoutes());
        System.out.println("claimOwners = " + result.claimOwners());
        System.out.println("retryOrderTimes = " + result.retryOrderTimes());
        System.out.println("finalStatus = " + result.finalStatus());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
