package com.example.mqcacheidempotencydemo.mq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class MessageReliabilityDemoTest {

    @Autowired
    private MessageReliabilityDemoService messageReliabilityDemoService;

    @Test
    void outboxRetryAndConsumerIdempotencyShouldWorkTogether() {
        MessageReliabilityDemoService.EndToEndResult result =
                messageReliabilityDemoService.endToEndDemo("ORD-TEST-01", BigDecimal.valueOf(128));

        assertThat(result.deliveryAttempts()).isEqualTo(2);
        assertThat(result.finalEventStatus()).isEqualTo("SENT");
        assertThat(result.consumerRows()).isEqualTo(1);
        assertThat(messageReliabilityDemoService.countConsumerRecords("order-consumer", "ORD-TEST-01")).isEqualTo(1);
    }
}
