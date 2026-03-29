package com.example.redislockdemo.orderidempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.RedisConnectionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderSubmitIdempotencyDemoTest {

    @Autowired
    private OrderSubmitIdempotencyDemoService orderSubmitIdempotencyDemoService;

    @BeforeEach
    void setUp() {
        assumeTrue(redisAvailable());
        orderSubmitIdempotencyDemoService.clearClaim("health-check");
    }

    @Test
    void unsafeCheckThenCreateProducesDuplicateOrders() throws Exception {
        OrderSubmitIdempotencyDemoService.SubmitDemoResult result =
                orderSubmitIdempotencyDemoService.unsafeCheckThenCreate("REQ-UNSAFE-1", 42L, 12);

        assertThat(result.ordersCreated()).isGreaterThan(1);
        assertThat(result.createdResponses()).isGreaterThan(1);
        assertThat(result.observedOrderNos().size()).isGreaterThan(1);
    }

    @Test
    void claimFirstWithRedisAllowsOnlyOneCreatedOrder() throws Exception {
        OrderSubmitIdempotencyDemoService.SubmitDemoResult result =
                orderSubmitIdempotencyDemoService.claimFirstWithRedis("REQ-SAFE-1", 42L, 12);

        assertThat(result.ordersCreated()).isEqualTo(1);
        assertThat(result.createdResponses()).isEqualTo(1);
        assertThat(result.createdResponses() + result.replayedResponses() + result.processingResponses())
                .isEqualTo(result.threadCount());
        assertThat(result.observedOrderNos()).hasSize(1);
        assertThat(result.replayedResponses() + result.processingResponses()).isGreaterThanOrEqualTo(1);
    }

    private boolean redisAvailable() {
        try {
            orderSubmitIdempotencyDemoService.clearClaim("health-check");
            return true;
        } catch (RedisConnectionException ex) {
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
}
