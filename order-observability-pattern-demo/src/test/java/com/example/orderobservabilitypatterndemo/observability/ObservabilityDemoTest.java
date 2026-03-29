package com.example.orderobservabilitypatterndemo.observability;

import com.example.orderobservabilitypatterndemo.order.OrderFulfillmentDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ObservabilityDemoTest {

    @Autowired
    private OrderFulfillmentDemoService orderFulfillmentDemoService;

    @Autowired
    private ObservabilityDemoService observabilityDemoService;

    @Test
    void checkoutShouldProduceTraceLogsAndMetrics() {
        OrderFulfillmentDemoService.OrderFlowResult result =
                orderFulfillmentDemoService.checkoutDemo("ORD-OBS-01", "WALLET", 1, 5);

        assertThat(result.traceId()).startsWith("trace-");
        assertThat(observabilityDemoService.logs()).anyMatch(log -> log.contains("traceId=" + result.traceId()));
        assertThat(observabilityDemoService.counter("order.success")).isEqualTo(1);
        assertThat(observabilityDemoService.latency("order.checkout")).isEqualTo(35L);
    }
}
