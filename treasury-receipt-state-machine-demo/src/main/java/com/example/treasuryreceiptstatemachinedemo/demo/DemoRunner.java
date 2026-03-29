package com.example.treasuryreceiptstatemachinedemo.demo;

import com.example.treasuryreceiptstatemachinedemo.receipt.TreasuryReceiptStateMachineDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final TreasuryReceiptStateMachineDemoService treasuryReceiptStateMachineDemoService;

    public DemoRunner(TreasuryReceiptStateMachineDemoService treasuryReceiptStateMachineDemoService) {
        this.treasuryReceiptStateMachineDemoService = treasuryReceiptStateMachineDemoService;
    }

    @Override
    public void run(String... args) {
        TreasuryReceiptStateMachineDemoService.ReceiptFlowResult result =
                treasuryReceiptStateMachineDemoService.receiptStateMachineDemo();

        printTitle("1. 回执状态机与补偿");
        result.steps().forEach(System.out::println);
        System.out.println("finalStates = " + result.finalStates());
        System.out.println("ignoredEvents = " + result.ignoredEvents());
        System.out.println("versionByInstruction = " + result.versionByInstruction());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
