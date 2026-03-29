package com.example.orderobservabilitypatterndemo.treasury;

import com.example.orderobservabilitypatterndemo.observability.ObservabilityDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class TreasuryFlowDemoTest {

    @Autowired
    private TreasuryFlowDemoService treasuryFlowDemoService;

    @Autowired
    private ObservabilityDemoService observabilityDemoService;

    @Test
    void directBankFlowShouldCompleteSuccessfully() {
        TreasuryFlowDemoService.TreasuryFlowResult result =
                treasuryFlowDemoService.treasuryFlowDemo("TXN-TEST-01", "DIRECT_BANK", 200, 500);

        assertThat(result.finalStatus()).isEqualTo("DISPATCHED");
        assertThat(result.strategyName()).isEqualTo("DirectBankDispatchStrategy");
        assertThat(result.compensationSteps()).isEmpty();
        assertThat(observabilityDemoService.counter("treasury.flow.success")).isEqualTo(1);
        assertThat(observabilityDemoService.latency("treasury.flow.dispatch")).isEqualTo(35L);
    }

    @Test
    void dispatchFailureShouldTriggerCompensation() {
        TreasuryFlowDemoService.TreasuryFlowResult result =
                treasuryFlowDemoService.treasuryFlowDemo("TXN-TEST-02", "DIRECT_BANK_FAIL", 200, 500);

        assertThat(result.finalStatus()).isEqualTo("COMPENSATED");
        assertThat(result.strategyName()).isEqualTo("FailBankDispatchStrategy");
        assertThat(result.compensationSteps()).containsExactly("release reserved quota for TXN-TEST-02");
        assertThat(observabilityDemoService.counter("treasury.flow.compensation")).isEqualTo(1);
    }
}
