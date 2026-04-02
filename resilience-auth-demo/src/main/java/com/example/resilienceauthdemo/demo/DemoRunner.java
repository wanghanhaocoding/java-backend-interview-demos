package com.example.resilienceauthdemo.demo;

import com.example.resilienceauthdemo.auth.AuthDemoService;
import com.example.resilienceauthdemo.resilience.ResilienceDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final ResilienceDemoService resilienceDemoService;
    private final AuthDemoService authDemoService;

    public DemoRunner(ResilienceDemoService resilienceDemoService, AuthDemoService authDemoService) {
        this.resilienceDemoService = resilienceDemoService;
        this.authDemoService = authDemoService;
    }

    @Override
    public void run(String... args) {
        printTitle("1. 服务治理");
        ResilienceDemoService.RateLimitResult rateLimitResult = resilienceDemoService.rateLimitDemo(3, 5);
        System.out.println("allowed = " + rateLimitResult.allowedRequests());
        System.out.println("rejected = " + rateLimitResult.rejectedRequests());

        ResilienceDemoService.CircuitBreakerResult circuitBreakerResult = resilienceDemoService.circuitBreakerDemo();
        circuitBreakerResult.steps().forEach(System.out::println);

        ResilienceDemoService.BulkheadResult bulkheadResult = resilienceDemoService.bulkheadDemo(2, 1, 5);
        System.out.println("accepted = " + bulkheadResult.acceptedRequests());
        System.out.println("rejected = " + bulkheadResult.rejectedRequests());

        printTitle("2. 认证授权");
        AuthDemoService.LoginResult loginResult = authDemoService.loginAndAuthorizeDemo(
                "alice",
                linkedRoles("OPS", "ORDER_ADMIN")
        );
        loginResult.steps().forEach(System.out::println);
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private Set<String> linkedRoles(String... roles) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        java.util.Collections.addAll(result, roles);
        return result;
    }
}
