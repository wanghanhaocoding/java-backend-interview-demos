package com.example.distributedtxdemo.state;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class StateMachineDemoService {

    private final JdbcTemplate jdbcTemplate;

    public StateMachineDemoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void createInstruction(String requestNo, BigDecimal amount) {
        jdbcTemplate.update(
                "insert into payment_instruction(request_no, status, amount, version, last_message, created_at, updated_at) values (?, ?, ?, 0, ?, current_timestamp, current_timestamp)",
                requestNo,
                InstructionStatus.WAIT_RECEIPT.name(),
                amount,
                "instruction-created"
        );
    }

    @Transactional
    public ReceiptApplyResult applyReceipt(String requestNo, InstructionStatus incomingStatus, String message) {
        InstructionRecord current = requireInstruction(requestNo);
        if (!canTransit(current.status(), incomingStatus)) {
            return new ReceiptApplyResult(false, current.status(), message + " -> ignored", "state-machine-blocked");
        }

        int updated = jdbcTemplate.update(
                "update payment_instruction set status = ?, version = version + 1, last_message = ?, updated_at = current_timestamp where request_no = ? and version = ?",
                incomingStatus.name(),
                message,
                requestNo,
                current.version()
        );

        if (updated == 0) {
            InstructionRecord latest = requireInstruction(requestNo);
            return new ReceiptApplyResult(false, latest.status(), message + " -> lost race", "optimistic-lock-race");
        }

        return new ReceiptApplyResult(true, incomingStatus, message + " -> applied", "version-check-passed");
    }

    @Transactional
    public int reconcileTimeoutInstructions(Duration timeout) {
        LocalDateTime deadline = LocalDateTime.now().minus(timeout);
        return jdbcTemplate.update(
                "update payment_instruction set status = 'FAIL', version = version + 1, last_message = 'timeout-reconcile', updated_at = current_timestamp where status in ('WAIT_RECEIPT', 'PROCESSING') and updated_at < ?",
                deadline
        );
    }

    public void backdateInstruction(String requestNo, Duration duration) {
        jdbcTemplate.update(
                "update payment_instruction set updated_at = ? where request_no = ?",
                LocalDateTime.now().minus(duration),
                requestNo
        );
    }

    public InstructionRecord findInstruction(String requestNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "select request_no, status, amount, version, last_message from payment_instruction where request_no = ?",
                    (rs, rowNum) -> new InstructionRecord(
                            rs.getString("request_no"),
                            InstructionStatus.valueOf(rs.getString("status")),
                            rs.getBigDecimal("amount"),
                            rs.getLong("version"),
                            rs.getString("last_message")
                    ),
                    requestNo
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public FlexibleDemoResult successThenLateProcessingDemo(String requestNo, BigDecimal amount) {
        createInstruction(requestNo, amount);
        List<String> steps = new ArrayList<>();
        steps.add("step-1 create instruction in local transaction -> WAIT_RECEIPT");

        ReceiptApplyResult success = applyReceipt(requestNo, InstructionStatus.SUCCESS, "bank-success-first");
        steps.add("step-2 first receipt -> " + success.message() + " | reason=" + success.reason());

        ReceiptApplyResult processing = applyReceipt(requestNo, InstructionStatus.PROCESSING, "bank-processing-late");
        steps.add("step-3 late receipt -> " + processing.message() + " | reason=" + processing.reason());

        InstructionRecord finalRecord = requireInstruction(requestNo);
        steps.add("step-4 final db status = " + finalRecord.status() + ", version=" + finalRecord.version());
        return new FlexibleDemoResult(requestNo, finalRecord.status(), steps);
    }

    public FlexibleDemoResult timeoutCompensationDemo(String requestNo, BigDecimal amount, Duration overdueFor, Duration timeout) {
        createInstruction(requestNo, amount);
        backdateInstruction(requestNo, overdueFor);

        List<String> steps = new ArrayList<>();
        steps.add("step-1 create instruction in local transaction -> WAIT_RECEIPT");
        steps.add("step-2 manually make instruction overdue by " + overdueFor.toMinutes() + " minutes");
        steps.add("step-3 reconcile updated rows = " + reconcileTimeoutInstructions(timeout));

        InstructionRecord finalRecord = requireInstruction(requestNo);
        steps.add("step-4 final db status = " + finalRecord.status() + ", lastMessage=" + finalRecord.lastMessage());
        return new FlexibleDemoResult(requestNo, finalRecord.status(), steps);
    }

    public ProjectStyleDistributedTxResult projectStyleFlowDemo(String requestNo,
                                                                BigDecimal amount,
                                                                boolean successReceiptFirst,
                                                                boolean appendLateProcessingReceipt,
                                                                boolean triggerTimeoutCompensation,
                                                                Duration overdueFor,
                                                                Duration timeout) {
        // 第一步只保证本地落库成功，银行处理结果不会被包含进这个本地事务。
        createInstruction(requestNo, amount);
        List<String> timeline = new ArrayList<>();
        timeline.add("1) 本地事务：创建指令单和初始状态，requestNo=" + requestNo + ", status=WAIT_RECEIPT");
        timeline.add("2) 外部系统：此时银行/第三方不在本地事务里，后续靠回执或补偿收敛结果");

        // 银行回执异步到达后，通过状态机和版本控制推进状态。
        if (successReceiptFirst) {
            ReceiptApplyResult success = applyReceipt(requestNo, InstructionStatus.SUCCESS, "bank-success-first");
            timeline.add("3) 第一次回执：SUCCESS -> " + success.message() + " | reason=" + success.reason());
        } else {
            ReceiptApplyResult processing = applyReceipt(requestNo, InstructionStatus.PROCESSING, "bank-processing-first");
            timeline.add("3) 第一次回执：PROCESSING -> " + processing.message() + " | reason=" + processing.reason());
        }

        // 迟到的中间态回执不会强行覆盖已经推进到更后面的状态。
        if (appendLateProcessingReceipt) {
            ReceiptApplyResult processingLate = applyReceipt(requestNo, InstructionStatus.PROCESSING, "bank-processing-late");
            timeline.add("4) 迟到回执：PROCESSING -> " + processingLate.message() + " | reason=" + processingLate.reason());
        }

        // 长时间未到终态时，靠定时补偿或对账任务把结果继续向终态收敛。
        if (triggerTimeoutCompensation) {
            backdateInstruction(requestNo, overdueFor);
            int updatedRows = reconcileTimeoutInstructions(timeout);
            timeline.add("5) 定时补偿/对账：扫描超时未终态记录，updatedRows=" + updatedRows);
        }

        InstructionRecord finalRecord = requireInstruction(requestNo);
        timeline.add("6) 最终结果：status=" + finalRecord.status() + ", version=" + finalRecord.version() + ", lastMessage=" + finalRecord.lastMessage());

        List<String> guarantees = List.of(
                "本地事务保证：指令单和初始状态落库原子成功或一起失败",
                "状态机保证：终态一旦写入，迟到的中间态回执不能把状态冲回去",
                "幂等保证：同一 requestNo 只围绕同一条业务记录流转，重复回执不会重复生效",
                "补偿保证：长时间未终态的数据可以通过定时扫描 / 对账收敛到最终结果"
        );

        return new ProjectStyleDistributedTxResult(requestNo, finalRecord.status(), timeline, guarantees);
    }

    private InstructionRecord requireInstruction(String requestNo) {
        InstructionRecord record = findInstruction(requestNo);
        if (record == null) {
            throw new IllegalArgumentException("instruction not found: " + requestNo);
        }
        return record;
    }

    private boolean canTransit(InstructionStatus current, InstructionStatus incoming) {
        if (current.isTerminal()) {
            return false;
        }
        return switch (current) {
            case WAIT_RECEIPT -> incoming == InstructionStatus.PROCESSING || incoming == InstructionStatus.SUCCESS || incoming == InstructionStatus.FAIL;
            case PROCESSING -> incoming == InstructionStatus.SUCCESS || incoming == InstructionStatus.FAIL;
            case SUCCESS, FAIL -> false;
        };
    }

    public enum InstructionStatus {
        WAIT_RECEIPT,
        PROCESSING,
        SUCCESS,
        FAIL;

        public boolean isTerminal() {
            return this == SUCCESS || this == FAIL;
        }
    }

    public record InstructionRecord(String requestNo, InstructionStatus status, BigDecimal amount, long version, String lastMessage) {
    }

    public record ReceiptApplyResult(boolean updated, InstructionStatus finalStatus, String message, String reason) {
    }

    public record FlexibleDemoResult(String requestNo, InstructionStatus finalStatus, List<String> steps) {
    }

    public record ProjectStyleDistributedTxResult(String requestNo,
                                                  InstructionStatus finalStatus,
                                                  List<String> timeline,
                                                  List<String> guarantees) {
    }
}
