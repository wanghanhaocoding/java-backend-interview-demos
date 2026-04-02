package com.example.distributedtxdemo.projectpattern;

import com.example.distributedtxdemo.common.DemoDataResetService;
import com.example.distributedtxdemo.state.StateMachineDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProjectStyleDistributedTxTest {

    @Autowired
    private StateMachineDemoService stateMachineDemoService;

    @Autowired
    private DemoDataResetService demoDataResetService;

    @BeforeEach
    void setUp() {
        demoDataResetService.resetAll();
    }

    @Test
    void projectStyleFlowShowsLocalTransactionStateMachineAndCompensation() {
        StateMachineDemoService.ProjectStyleDistributedTxResult result = stateMachineDemoService.projectStyleFlowDemo(
                "REQ-PROJECT-01",
                BigDecimal.valueOf(108),
                StateMachineDemoService.InstructionStatus.SUCCESS,
                true,
                false,
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
        );

        assertThat(result.finalStatus()).isEqualTo(StateMachineDemoService.InstructionStatus.SUCCESS);
        assertThat(result.timeline()).anyMatch(line -> line.contains("发起支付接口"));
        assertThat(result.timeline()).anyMatch(line -> line.contains("迟到回调接口"));
        assertThat(result.guarantees()).hasSize(4);
    }

    @Test
    void projectStyleFlowCanUseCompensationToConvergeToFail() {
        StateMachineDemoService.ProjectStyleDistributedTxResult result = stateMachineDemoService.projectStyleFlowDemo(
                "REQ-PROJECT-02",
                BigDecimal.valueOf(58),
                StateMachineDemoService.InstructionStatus.PROCESSING,
                false,
                true,
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
        );

        assertThat(result.finalStatus()).isEqualTo(StateMachineDemoService.InstructionStatus.FAIL);
        assertThat(result.timeline()).anyMatch(line -> line.contains("定时补偿任务"));
    }
}
