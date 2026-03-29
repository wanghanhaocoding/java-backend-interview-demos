package com.example.distributedtxdemo.outbox;

import com.example.distributedtxdemo.common.DemoDataResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxDemoTest {

    @Autowired
    private OutboxDemoService outboxDemoService;

    @Autowired
    private DemoDataResetService demoDataResetService;

    @BeforeEach
    void setUp() {
        demoDataResetService.resetAll();
    }

    @Test
    void outboxEventCanBeRetriedAndConsumerIsIdempotent() {
        OutboxDemoService.OutboxFlowResult result = outboxDemoService.endToEndDemo("ORD-TEST-01", BigDecimal.valueOf(128));

        assertThat(result.orderRows()).isEqualTo(1);
        assertThat(result.finalEventStatus()).isEqualTo("SENT");
        assertThat(outboxDemoService.countConsumerRecords("order-consumer", "ORD-TEST-01")).isEqualTo(1);
    }
}
