package com.example.treasuryreceiptstatemachinedemo.receipt;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TreasuryReceiptStateMachineDemoService {

    public ReceiptFlowResult receiptStateMachineDemo() {
        List<String> steps = new ArrayList<>();
        Set<String> processedReceiptIds = new LinkedHashSet<>();
        List<String> ignoredEvents = new ArrayList<>();
        Map<String, Instruction> instructions = new LinkedHashMap<>();

        Instruction pay1001 = new Instruction("PAY-1001", InstructionStatus.SENT, 1);
        Instruction pay1002 = new Instruction("PAY-1002", InstructionStatus.SENT, 1);
        instructions.put(pay1001.instructionId(), pay1001);
        instructions.put(pay1002.instructionId(), pay1002);
        steps.add("1. 指令 PAY-1001 和 PAY-1002 发起成功，初始状态都进入 SENT");

        instructions.put("PAY-1001", applyReceipt(
                instructions.get("PAY-1001"),
                "BANK-RCP-1001",
                InstructionStatus.SUCCESS,
                processedReceiptIds,
                ignoredEvents,
                steps
        ));

        instructions.put("PAY-1001", applyReceipt(
                instructions.get("PAY-1001"),
                "BANK-RCP-1001",
                InstructionStatus.SUCCESS,
                processedReceiptIds,
                ignoredEvents,
                steps
        ));

        instructions.put("PAY-1001", applyReceipt(
                instructions.get("PAY-1001"),
                "BANK-RCP-LATE-1001",
                InstructionStatus.BANK_PROCESSING,
                processedReceiptIds,
                ignoredEvents,
                steps
        ));

        Instruction reconciling = instructions.get("PAY-1002").advanceTo(InstructionStatus.RECONCILING);
        instructions.put("PAY-1002", reconciling);
        steps.add("5. PAY-1002 超过 SLA 仍未收到回执，超时扫描把状态推进到 RECONCILING");

        instructions.put("PAY-1002", applyReceipt(
                instructions.get("PAY-1002"),
                "BANK-RCP-RECON-1002",
                InstructionStatus.SUCCESS,
                processedReceiptIds,
                ignoredEvents,
                steps
        ));

        Map<String, String> finalStates = new LinkedHashMap<>();
        Map<String, Integer> versionByInstruction = new LinkedHashMap<>();
        instructions.forEach((instructionId, instruction) -> {
            finalStates.put(instructionId, instruction.status().name());
            versionByInstruction.put(instructionId, instruction.version());
        });

        return new ReceiptFlowResult(steps, finalStates, ignoredEvents, versionByInstruction);
    }

    private Instruction applyReceipt(Instruction instruction,
                                     String receiptId,
                                     InstructionStatus targetStatus,
                                     Set<String> processedReceiptIds,
                                     List<String> ignoredEvents,
                                     List<String> steps) {
        if (processedReceiptIds.contains(receiptId)) {
            ignoredEvents.add("duplicate:" + receiptId);
            steps.add("3. 收到重复回执 " + receiptId + "，因为幂等键已存在，直接忽略");
            return instruction;
        }

        if (!instruction.status().canTransitTo(targetStatus)) {
            ignoredEvents.add("invalid-transition:" + receiptId);
            steps.add("4. 收到迟到回执 " + receiptId + "，目标状态是 " + targetStatus
                    + "，但当前已是终态 " + instruction.status() + "，因此拒绝回退");
            return instruction;
        }

        processedReceiptIds.add(receiptId);
        Instruction updated = instruction.advanceTo(targetStatus);
        steps.add("2. 回执 " + receiptId + " 把 " + instruction.instructionId()
                + " 从 " + instruction.status() + " 推进到 " + updated.status()
                + "，version=" + updated.version());
        return updated;
    }

    private static final class Instruction {

        private final String instructionId;
        private final InstructionStatus status;
        private final int version;

        private Instruction(String instructionId, InstructionStatus status, int version) {
            this.instructionId = instructionId;
            this.status = status;
            this.version = version;
        }

        private Instruction advanceTo(InstructionStatus nextStatus) {
            return new Instruction(instructionId, nextStatus, version + 1);
        }

        private String instructionId() {
            return instructionId;
        }

        private InstructionStatus status() {
            return status;
        }

        private int version() {
            return version;
        }
    }

    private enum InstructionStatus {
        SENT,
        BANK_PROCESSING,
        RECONCILING,
        SUCCESS;

        private boolean canTransitTo(InstructionStatus targetStatus) {
            switch (this) {
                case SENT:
                    return targetStatus == BANK_PROCESSING
                            || targetStatus == SUCCESS
                            || targetStatus == RECONCILING;
                case BANK_PROCESSING:
                    return targetStatus == SUCCESS || targetStatus == RECONCILING;
                case RECONCILING:
                    return targetStatus == SUCCESS;
                case SUCCESS:
                default:
                    return false;
            }
        }
    }

    public static final class ReceiptFlowResult {

        private final List<String> steps;
        private final Map<String, String> finalStates;
        private final List<String> ignoredEvents;
        private final Map<String, Integer> versionByInstruction;

        public ReceiptFlowResult(List<String> steps,
                                 Map<String, String> finalStates,
                                 List<String> ignoredEvents,
                                 Map<String, Integer> versionByInstruction) {
            this.steps = steps;
            this.finalStates = finalStates;
            this.ignoredEvents = ignoredEvents;
            this.versionByInstruction = versionByInstruction;
        }

        public List<String> steps() {
            return steps;
        }

        public Map<String, String> finalStates() {
            return finalStates;
        }

        public List<String> ignoredEvents() {
            return ignoredEvents;
        }

        public Map<String, Integer> versionByInstruction() {
            return versionByInstruction;
        }
    }
}
