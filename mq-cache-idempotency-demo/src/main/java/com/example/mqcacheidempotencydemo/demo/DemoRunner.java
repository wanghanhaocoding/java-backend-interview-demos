package com.example.mqcacheidempotencydemo.demo;

import com.example.mqcacheidempotencydemo.cache.CacheConsistencyDemoService;
import com.example.mqcacheidempotencydemo.idempotency.IdempotencyDemoService;
import com.example.mqcacheidempotencydemo.mq.MessageReliabilityDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final MessageReliabilityDemoService messageReliabilityDemoService;
    private final CacheConsistencyDemoService cacheConsistencyDemoService;
    private final IdempotencyDemoService idempotencyDemoService;

    public DemoRunner(MessageReliabilityDemoService messageReliabilityDemoService,
                      CacheConsistencyDemoService cacheConsistencyDemoService,
                      IdempotencyDemoService idempotencyDemoService) {
        this.messageReliabilityDemoService = messageReliabilityDemoService;
        this.cacheConsistencyDemoService = cacheConsistencyDemoService;
        this.idempotencyDemoService = idempotencyDemoService;
    }

    @Override
    public void run(String... args) {
        printTitle("1. MQ 可靠性：Outbox + 重试 + 消费幂等");
        MessageReliabilityDemoService.EndToEndResult mqResult =
                messageReliabilityDemoService.endToEndDemo("ORD-1001", BigDecimal.valueOf(128));
        mqResult.steps().forEach(System.out::println);
        System.out.println("finalEventStatus = " + mqResult.finalEventStatus());

        printTitle("2. 缓存一致性：脏读与延时双删");
        CacheConsistencyDemoService.CacheReadResult staleRead =
                cacheConsistencyDemoService.staleReadDemo("product:1001", 100, 80);
        staleRead.steps().forEach(System.out::println);
        System.out.println("staleCacheValue = " + staleRead.staleCacheValue());

        CacheConsistencyDemoService.CacheRepairResult repairResult =
                cacheConsistencyDemoService.delayedDoubleDeleteDemo("product:1001", 100, 80);
        repairResult.steps().forEach(System.out::println);
        System.out.println("finalReadValue = " + repairResult.finalReadValue());

        CacheConsistencyDemoService.BreakdownProtectionResult breakdownResult =
                cacheConsistencyDemoService.breakdownProtectionDemo("product:hot", 6);
        System.out.println("loadersWithoutMutex = " + breakdownResult.loadersWithoutMutex());
        System.out.println("loadersWithMutex = " + breakdownResult.loadersWithMutex());

        printTitle("3. 幂等：重复提交与重复回调");
        IdempotencyDemoService.SubmissionResult submissionResult =
                idempotencyDemoService.duplicateSubmissionDemo("idem-token-01", BigDecimal.valueOf(66));
        submissionResult.steps().forEach(System.out::println);
        System.out.println("secondSubmission = " + submissionResult.secondSubmissionStatus());

        IdempotencyDemoService.CallbackResult callbackResult =
                idempotencyDemoService.paymentCallbackDemo("PAY-1001");
        callbackResult.steps().forEach(System.out::println);
        System.out.println("finalCallbackStatus = " + callbackResult.finalStatus());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
