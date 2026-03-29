package com.example.distributedtxdemo.saga;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SagaDemoTest {

    private final SagaDemoService sagaDemoService = new SagaDemoService();

    @Test
    void failedSagaRunsCompensationSteps() {
        SagaDemoService.SagaResult result = sagaDemoService.orchestrateWithCompensation("SAGA-TEST-01", true);

        assertThat(result.success()).isFalse();
        assertThat(result.compensations()).containsExactly("compensate inventory reservation", "compensate fund freeze");
    }
}
