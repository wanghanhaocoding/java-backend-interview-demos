package com.example.probemqtroubleshootingdemo;

import com.example.probemqtroubleshootingdemo.mq.InMemoryMqConsumerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "teaching.runner.enabled=false",
                "demo.node-id=test-node",
                "demo.fault.enabled=false",
                "demo.fault.block-seconds=2",
                "demo.readiness.down-threshold=1",
                "demo.mq.consumer-count=1",
                "demo.mq.produce-interval-ms=100",
                "demo.mq.auto-produce=true"
        }
)
class ProbeMqTroubleshootingDemoTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private InMemoryMqConsumerService inMemoryMqConsumerService;

    @Test
    void shouldFlipReadinessDownWhileMqKeepsAdvancing() throws Exception {
        long before = inMemoryMqConsumerService.snapshot().getConsumedCount();
        testRestTemplate.postForEntity(url("/api/fault/enable?blockSeconds=2"), null, Map.class);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<ResponseEntity<Map>> blockedRequest = CompletableFuture.supplyAsync(
                    () -> testRestTemplate.getForEntity(url("/api/traffic?businessKey=order-1"), Map.class),
                    executorService
            );

            awaitCondition(() -> "DOWN".equals(healthStatus()), 3000L);
            assertThat(healthStatus()).isEqualTo("DOWN");
            assertThat(inMemoryMqConsumerService.awaitConsumedAtLeast(before + 1, 3000L)).isTrue();

            ResponseEntity<Map> response = blockedRequest.get(5L, TimeUnit.SECONDS);
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            awaitCondition(() -> "UP".equals(healthStatus()), 3000L);
            assertThat(healthStatus()).isEqualTo("UP");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldExposeEvidenceEndpointWithNodeAndMqSignals() {
        ResponseEntity<Map> response = testRestTemplate.getForEntity(url("/api/evidence"), Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("nodeId", "test-node");
        assertThat(response.getBody()).containsKey("mqConsumedCount");
        assertThat(response.getBody()).containsKey("activeBlockedRequests");
    }

    private String healthStatus() {
        Map body = testRestTemplate.getForObject(url("/actuator/health/readiness"), Map.class);
        return (String) body.get("status");
    }

    private void awaitCondition(Check check, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (check.matches()) {
                return;
            }
            Thread.sleep(100L);
        }
        assertThat(check.matches()).isTrue();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }
}
