package com.example.orderobservabilitypatterndemo.order;

import com.example.orderobservabilitypatterndemo.observability.ObservabilityDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class OrderFulfillmentDemoTest {

    @Autowired
    private OrderFulfillmentDemoService orderFulfillmentDemoService;

    @Autowired
    private ObservabilityDemoService observabilityDemoService;

    @Test
    void walletCheckoutShouldCompleteSuccessfully() {
        OrderFulfillmentDemoService.OrderFlowResult result =
                orderFulfillmentDemoService.checkoutDemo("ORD-TEST-01", "WALLET", 2, 5);

        assertThat(result.finalStatus()).isEqualTo("COMPLETED");
        assertThat(result.strategyName()).isEqualTo("WalletPaymentStrategy");
        assertThat(result.compensationSteps()).isEmpty();
        assertThat(observabilityDemoService.counter("order.success")).isEqualTo(1);
        assertThat(observabilityDemoService.latency("order.checkout")).isEqualTo(35L);
    }

    @Test
    void paymentFailureShouldTriggerCompensation() {
        OrderFulfillmentDemoService.OrderFlowResult result =
                orderFulfillmentDemoService.checkoutDemo("ORD-TEST-02", "CARD_FAIL", 2, 5);

        assertThat(result.finalStatus()).isEqualTo("CANCELLED");
        assertThat(result.strategyName()).isEqualTo("FailCardPaymentStrategy");
        assertThat(result.compensationSteps()).containsExactly("release inventory for ORD-TEST-02");
        assertThat(observabilityDemoService.counter("order.compensation")).isEqualTo(1);
    }
}
