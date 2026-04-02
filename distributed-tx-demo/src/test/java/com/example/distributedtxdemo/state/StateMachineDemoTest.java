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
    void submitInstructionPersistsInitialState() {
        StateMachineDemoService.InstructionRecord created = stateMachineDemoService.submitInstruction(
                "REQ-STATE-00",
                BigDecimal.valueOf(88)
        );

        assertThat(created.requestNo()).isEqualTo("REQ-STATE-00");
        assertThat(created.status()).isEqualTo(StateMachineDemoService.InstructionStatus.WAIT_RECEIPT);
        assertThat(created.version()).isZero();
    }

    @Test
    void terminalStatusCannotBeOverwrittenByLateReceipt() {
        stateMachineDemoService.submitInstruction("REQ-STATE-01", BigDecimal.valueOf(88));

        StateMachineDemoService.ReceiptApplyResult success = stateMachineDemoService.handleBankReceipt(
                "REQ-STATE-01",
                "BANK-RCP-0001",
                StateMachineDemoService.InstructionStatus.SUCCESS,
                "first callback says success"
        );
        StateMachineDemoService.ReceiptApplyResult lateProcessing = stateMachineDemoService.handleBankReceipt(
                "REQ-STATE-01",
                "BANK-RCP-0002",
                StateMachineDemoService.InstructionStatus.PROCESSING,
                "late callback still says processing"
        );
        StateMachineDemoService.InstructionRecord finalRecord = stateMachineDemoService.findInstruction("REQ-STATE-01");

        assertThat(success.updated()).isTrue();
        assertThat(lateProcessing.updated()).isFalse();
        assertThat(lateProcessing.reason()).isEqualTo("state-machine-blocked");
        assertThat(finalRecord.status()).isEqualTo(StateMachineDemoService.InstructionStatus.SUCCESS);
        assertThat(finalRecord.version()).isEqualTo(1);
    }

    @Test
    void timeoutCompensationMovesInstructionToFail() {
        stateMachineDemoService.submitInstruction("REQ-STATE-02", BigDecimal.valueOf(66));
        stateMachineDemoService.backdateInstruction("REQ-STATE-02", Duration.ofMinutes(10));

        int updatedRows = stateMachineDemoService.compensateTimeoutInstructions(Duration.ofMinutes(5));
        StateMachineDemoService.InstructionRecord finalRecord = stateMachineDemoService.findInstruction("REQ-STATE-02");

        assertThat(updatedRows).isEqualTo(1);
        assertThat(finalRecord.status()).isEqualTo(StateMachineDemoService.InstructionStatus.FAIL);
        assertThat(finalRecord.lastMessage()).isEqualTo("timeout-reconcile");
    }
}
