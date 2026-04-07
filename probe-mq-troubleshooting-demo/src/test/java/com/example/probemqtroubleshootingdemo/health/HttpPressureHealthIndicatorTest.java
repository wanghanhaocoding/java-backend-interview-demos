package com.example.probemqtroubleshootingdemo.health;

import com.example.probemqtroubleshootingdemo.config.DemoProperties;
import com.example.probemqtroubleshootingdemo.http.RequestPressureTracker;
import com.example.probemqtroubleshootingdemo.mq.InMemoryMqConsumerService;
import com.example.probemqtroubleshootingdemo.node.NodeScenarioService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPressureHealthIndicatorTest {

    @Test
    void shouldFlipDownWhenBlockedRequestsReachThreshold() {
        DemoProperties properties = new DemoProperties();
        properties.setNodeId("node-test");
        properties.getReadiness().setDownThreshold(2);
        properties.getMq().setAutoProduce(false);
        RequestPressureTracker tracker = new RequestPressureTracker();
        InMemoryMqConsumerService mqConsumerService = new InMemoryMqConsumerService(properties);
        NodeScenarioService scenarioService = new NodeScenarioService(properties, tracker, mqConsumerService, 8081);
        HttpPressureHealthIndicator indicator = new HttpPressureHealthIndicator(scenarioService, tracker, mqConsumerService);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");

        RequestPressureTracker.RequestToken first = tracker.beginRequest(true);
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");

        RequestPressureTracker.RequestToken second = tracker.beginRequest(true);
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(indicator.health().getDetails()).containsEntry("nodeId", "node-test");
        assertThat(indicator.health().getDetails()).containsEntry("activeBlockedRequests", 2);

        second.close();
        first.close();
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
    }
}
