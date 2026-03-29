package com.example.orderobservabilitypatterndemo.observability;

import com.example.orderobservabilitypatterndemo.treasury.TreasuryFlowDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ObservabilityDemoTest {

    @Autowired
    private TreasuryFlowDemoService treasuryFlowDemoService;

    @Autowired
    private ObservabilityDemoService observabilityDemoService;

    @Test
    void treasuryFlowShouldProduceTraceLogsAndMetrics() {
        TreasuryFlowDemoService.TreasuryFlowResult result =
                treasuryFlowDemoService.treasuryFlowDemo("TXN-OBS-01", "DIRECT_BANK", 120, 300);

        assertThat(result.traceId()).startsWith("trace-");
        assertThat(observabilityDemoService.logs()).anyMatch(log -> log.contains("traceId=" + result.traceId()));
        assertThat(observabilityDemoService.counter("treasury.flow.success")).isEqualTo(1);
        assertThat(observabilityDemoService.latency("treasury.flow.dispatch")).isEqualTo(35L);
    }
}
