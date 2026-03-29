package com.example.resilienceauthdemo.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ResilienceDemoTest {

    @Autowired
    private ResilienceDemoService resilienceDemoService;

    @Test
    void rateLimiterShouldRejectRequestsAboveWindowQuota() {
        ResilienceDemoService.RateLimitResult result = resilienceDemoService.rateLimitDemo(3, 5);

        assertThat(result.allowedRequests()).isEqualTo(3);
        assertThat(result.rejectedRequests()).isEqualTo(2);
    }

    @Test
    void circuitBreakerShouldOpenThenCloseAfterProbeSucceeds() {
        ResilienceDemoService.CircuitBreakerResult result = resilienceDemoService.circuitBreakerDemo();

        assertThat(result.steps()).hasSize(5);
        assertThat(result.finalState()).isEqualTo("CLOSED");
    }

    @Test
    void bulkheadShouldRejectRequestsAboveCapacity() {
        ResilienceDemoService.BulkheadResult result = resilienceDemoService.bulkheadDemo(2, 1, 5);

        assertThat(result.acceptedRequests()).isEqualTo(3);
        assertThat(result.rejectedRequests()).isEqualTo(2);
    }
}
