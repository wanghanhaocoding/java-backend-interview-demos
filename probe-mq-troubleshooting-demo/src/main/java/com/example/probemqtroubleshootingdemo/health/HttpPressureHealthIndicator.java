package com.example.probemqtroubleshootingdemo.health;

import com.example.probemqtroubleshootingdemo.http.RequestPressureTracker;
import com.example.probemqtroubleshootingdemo.mq.InMemoryMqConsumerService;
import com.example.probemqtroubleshootingdemo.node.NodeScenarioService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("httpPressure")
public class HttpPressureHealthIndicator implements HealthIndicator {

    private final NodeScenarioService nodeScenarioService;

    private final RequestPressureTracker requestPressureTracker;

    private final InMemoryMqConsumerService inMemoryMqConsumerService;

    public HttpPressureHealthIndicator(NodeScenarioService nodeScenarioService,
                                       RequestPressureTracker requestPressureTracker,
                                       InMemoryMqConsumerService inMemoryMqConsumerService) {
        this.nodeScenarioService = nodeScenarioService;
        this.requestPressureTracker = requestPressureTracker;
        this.inMemoryMqConsumerService = inMemoryMqConsumerService;
    }

    @Override
    public Health health() {
        RequestPressureTracker.PressureSnapshot pressure = requestPressureTracker.snapshot();
        InMemoryMqConsumerService.MqSnapshot mq = inMemoryMqConsumerService.snapshot();
        boolean readinessDown = requestPressureTracker.isReadinessDown(nodeScenarioService.getReadinessDownThreshold());

        Health.Builder builder = readinessDown ? Health.down() : Health.up();
        return builder
                .withDetail("nodeId", nodeScenarioService.getNodeId())
                .withDetail("faultEnabled", nodeScenarioService.isFaultEnabled())
                .withDetail("blockSeconds", nodeScenarioService.getBlockSeconds())
                .withDetail("activeBlockedRequests", pressure.getActiveBlockedRequests())
                .withDetail("readinessDownThreshold", nodeScenarioService.getReadinessDownThreshold())
                .withDetail("lastBlockingThread", pressure.getLastBlockingThread())
                .withDetail("mqConsumedCount", mq.getConsumedCount())
                .withDetail("mqConsumerThreads", mq.getRecentConsumerThreads())
                .build();
    }
}
