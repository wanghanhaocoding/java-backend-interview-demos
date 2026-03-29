package com.example.resilienceauthdemo.resilience;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class ResilienceDemoService {

    public RateLimitResult rateLimitDemo(int permitsPerWindow, int totalRequests) {
        FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter(permitsPerWindow);
        int allowed = 0;
        int rejected = 0;
        for (int i = 0; i < totalRequests; i++) {
            if (rateLimiter.tryAcquire()) {
                allowed++;
            } else {
                rejected++;
            }
        }
        return new RateLimitResult(allowed, rejected);
    }

    public CircuitBreakerResult circuitBreakerDemo() {
        SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker(3);
        List<String> steps = new ArrayList<>();

        steps.add("1. 第 1 次调用失败，熔断器仍保持 CLOSED");
        circuitBreaker.onFailure();
        steps.add("2. 第 2 次调用失败，熔断器仍保持 CLOSED");
        circuitBreaker.onFailure();
        steps.add("3. 第 3 次调用失败，连续失败达到阈值，熔断器切到 OPEN");
        circuitBreaker.onFailure();

        String fallbackResult = circuitBreaker.execute(() -> "remote-ok", () -> "fallback-response");
        steps.add("4. OPEN 状态下不再访问下游，直接返回降级结果 " + fallbackResult);

        circuitBreaker.toHalfOpen();
        String recoveredResult = circuitBreaker.execute(() -> "remote-recovered", () -> "fallback-response");
        steps.add("5. HALF_OPEN 允许一次探测调用，成功后熔断器回到 CLOSED，结果为 " + recoveredResult);

        return new CircuitBreakerResult(steps, circuitBreaker.state());
    }

    public BulkheadResult bulkheadDemo(int workerCount, int queueCapacity, int totalRequests) {
        BoundedBulkhead bulkhead = new BoundedBulkhead(workerCount, queueCapacity);
        int accepted = 0;
        int rejected = 0;
        for (int i = 0; i < totalRequests; i++) {
            if (bulkhead.tryEnter()) {
                accepted++;
            } else {
                rejected++;
            }
        }
        return new BulkheadResult(accepted, rejected);
    }

    public record RateLimitResult(
            int allowedRequests,
            int rejectedRequests
    ) {
    }

    public record CircuitBreakerResult(
            List<String> steps,
            String finalState
    ) {
    }

    public record BulkheadResult(
            int acceptedRequests,
            int rejectedRequests
    ) {
    }

    private static final class FixedWindowRateLimiter {

        private final int permitsPerWindow;
        private int usedPermits;

        private FixedWindowRateLimiter(int permitsPerWindow) {
            this.permitsPerWindow = permitsPerWindow;
        }

        private boolean tryAcquire() {
            if (usedPermits >= permitsPerWindow) {
                return false;
            }
            usedPermits++;
            return true;
        }
    }

    private static final class SimpleCircuitBreaker {

        private final int failureThreshold;
        private int consecutiveFailures;
        private String state = "CLOSED";

        private SimpleCircuitBreaker(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        private void onFailure() {
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold) {
                state = "OPEN";
            }
        }

        private String execute(RemoteCall remoteCall, FallbackCall fallbackCall) {
            if ("OPEN".equals(state)) {
                return fallbackCall.get();
            }

            try {
                String result = remoteCall.get();
                consecutiveFailures = 0;
                state = "CLOSED";
                return result;
            } catch (RuntimeException ex) {
                onFailure();
                return fallbackCall.get();
            }
        }

        private void toHalfOpen() {
            state = "HALF_OPEN";
        }

        private String state() {
            return state;
        }
    }

    private interface RemoteCall {
        String get();
    }

    private interface FallbackCall {
        String get();
    }

    private static final class BoundedBulkhead {

        private final int capacity;
        private final Deque<Integer> acceptedSlots = new ArrayDeque<>();

        private BoundedBulkhead(int workers, int queueCapacity) {
            this.capacity = workers + queueCapacity;
        }

        private boolean tryEnter() {
            if (acceptedSlots.size() >= capacity) {
                return false;
            }
            acceptedSlots.addLast(acceptedSlots.size() + 1);
            return true;
        }
    }
}
