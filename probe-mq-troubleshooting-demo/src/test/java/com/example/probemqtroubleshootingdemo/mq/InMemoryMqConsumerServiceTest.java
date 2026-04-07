package com.example.probemqtroubleshootingdemo.mq;

import com.example.probemqtroubleshootingdemo.config.DemoProperties;
import com.example.probemqtroubleshootingdemo.http.RequestPressureTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMqConsumerServiceTest {

    private InMemoryMqConsumerService inMemoryMqConsumerService;

    @AfterEach
    void tearDown() {
        if (inMemoryMqConsumerService != null) {
            inMemoryMqConsumerService.stop();
        }
    }

    @Test
    void shouldKeepConsumingWhileRequestPressureIsUnhealthy() {
        DemoProperties properties = new DemoProperties();
        properties.setNodeId("node-b");
        properties.getMq().setConsumerCount(1);
        properties.getMq().setAutoProduce(false);
        RequestPressureTracker tracker = new RequestPressureTracker();

        inMemoryMqConsumerService = new InMemoryMqConsumerService(properties);
        inMemoryMqConsumerService.start();

        RequestPressureTracker.RequestToken first = tracker.beginRequest(true);
        RequestPressureTracker.RequestToken second = tracker.beginRequest(true);
        inMemoryMqConsumerService.enqueue("job-1");
        inMemoryMqConsumerService.enqueue("job-2");

        assertThat(tracker.isReadinessDown(2)).isTrue();
        assertThat(inMemoryMqConsumerService.awaitConsumedAtLeast(2L, 3000L)).isTrue();

        InMemoryMqConsumerService.MqSnapshot snapshot = inMemoryMqConsumerService.snapshot();
        assertThat(snapshot.getConsumedCount()).isGreaterThanOrEqualTo(2L);
        assertThat(snapshot.getRecentConsumerThreads()).anyMatch(thread -> thread.startsWith("node-b-mq-consumer-"));

        second.close();
        first.close();
    }
}
