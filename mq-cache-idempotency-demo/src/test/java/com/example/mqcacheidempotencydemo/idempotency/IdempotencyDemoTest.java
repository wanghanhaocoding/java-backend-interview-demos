package com.example.mqcacheidempotencydemo.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class IdempotencyDemoTest {

    @Autowired
    private IdempotencyDemoService idempotencyDemoService;

    @Test
    void duplicateSubmissionShouldBeRejectedByToken() {
        IdempotencyDemoService.SubmissionResult result =
                idempotencyDemoService.duplicateSubmissionDemo("idem-token-01", BigDecimal.valueOf(66));

        assertThat(result.firstSubmissionStatus()).isEqualTo("ACCEPTED");
        assertThat(result.secondSubmissionStatus()).isEqualTo("DUPLICATE");
    }

    @Test
    void duplicateAndLateCallbacksShouldNotOverwriteFinalState() {
        IdempotencyDemoService.CallbackResult result =
                idempotencyDemoService.paymentCallbackDemo("PAY-1001");

        assertThat(result.finalStatus()).isEqualTo("SUCCESS");
    }
}
