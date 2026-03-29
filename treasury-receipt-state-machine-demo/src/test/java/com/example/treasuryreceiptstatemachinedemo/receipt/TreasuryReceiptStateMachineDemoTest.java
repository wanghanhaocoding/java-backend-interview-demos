package com.example.treasuryreceiptstatemachinedemo.receipt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class TreasuryReceiptStateMachineDemoTest {

    @Autowired
    private TreasuryReceiptStateMachineDemoService treasuryReceiptStateMachineDemoService;

    @Test
    void shouldProtectTerminalStateAndCompleteReconciliation() {
        TreasuryReceiptStateMachineDemoService.ReceiptFlowResult result =
                treasuryReceiptStateMachineDemoService.receiptStateMachineDemo();

        assertThat(result.finalStates()).containsEntry("PAY-1001", "SUCCESS");
        assertThat(result.finalStates()).containsEntry("PAY-1002", "SUCCESS");
        assertThat(result.ignoredEvents()).containsExactly(
                "duplicate:BANK-RCP-1001",
                "invalid-transition:BANK-RCP-LATE-1001"
        );
        assertThat(result.versionByInstruction()).containsEntry("PAY-1001", 2);
        assertThat(result.versionByInstruction()).containsEntry("PAY-1002", 3);
    }
}
