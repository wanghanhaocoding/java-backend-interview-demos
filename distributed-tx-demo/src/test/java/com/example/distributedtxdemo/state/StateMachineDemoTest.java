package com.example.distributedtxdemo.state;

import com.example.distributedtxdemo.common.DemoDataResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StateMachineDemoTest {

    @Autowired
    private StateMachineDemoService stateMachineDemoService;

    @Autowired
    private DemoDataResetService demoDataResetService;

    @BeforeEach
    void setUp() {
        demoDataResetService.resetAll();
    }

    @Test
    void terminalStatusCannotBeOverwrittenByLateReceipt() {
        StateMachineDemoService.FlexibleDemoResult result = stateMachineDemoService.successThenLateProcessingDemo("REQ-STATE-01", BigDecimal.valueOf(88));

        assertThat(result.finalStatus()).isEqualTo(StateMachineDemoService.InstructionStatus.SUCCESS);
        assertThat(result.steps()).anyMatch(step -> step.contains("ignored"));
    }

    @Test
    void timeoutCompensationMovesInstructionToFail() {
        StateMachineDemoService.FlexibleDemoResult result = stateMachineDemoService.timeoutCompensationDemo(
                "REQ-STATE-02",
                BigDecimal.valueOf(66),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
        );

        assertThat(result.finalStatus()).isEqualTo(StateMachineDemoService.InstructionStatus.FAIL);
    }
}
