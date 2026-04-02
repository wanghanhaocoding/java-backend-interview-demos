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
    public InstructionRecord submitInstruction(String requestNo, BigDecimal amount) {
        insertInstruction(requestNo, amount);
        return requireInstruction(requestNo);
    }

    @Transactional
    public void createInstruction(String requestNo, BigDecimal amount) {
        insertInstruction(requestNo, amount);
    }

    @Transactional
    public ReceiptApplyResult handleBankReceipt(String requestNo,
                                                String bankReceiptNo,
                                                InstructionStatus bankStatus,
                                                String channelMessage) {
        validateReceiptStatus(bankStatus);
        String detail = channelMessage == null || channelMessage.trim().isEmpty() ? "bank-callback" : channelMessage;
        return applyReceiptInternal(requestNo, bankStatus, bankReceiptNo + " | " + detail);
    }

    @Transactional
    public ReceiptApplyResult applyReceipt(String requestNo, InstructionStatus incomingStatus, String message) {
        return applyReceiptInternal(requestNo, incomingStatus, message);
    }

    @Transactional
    public int compensateTimeoutInstructions(Duration timeout) {
        return doReconcileTimeoutInstructions(timeout);
    }

    @Transactional
    public int reconcileTimeoutInstructions(Duration timeout) {
        return doReconcileTimeoutInstructions(timeout);
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
        submitInstruction(requestNo, amount);
        List<String> steps = new ArrayList<>();
        steps.add("step-1 submit payment instruction -> WAIT_RECEIPT");

        ReceiptApplyResult success = handleBankReceipt(
                requestNo,
                "BANK-RCP-SUCCESS-" + requestNo,
                InstructionStatus.SUCCESS,
                "first callback says success"
        );
        steps.add("step-2 bank callback success -> " + success.message() + " | reason=" + success.reason());

        ReceiptApplyResult processing = handleBankReceipt(
                requestNo,
                "BANK-RCP-LATE-" + requestNo,
                InstructionStatus.PROCESSING,
                "late callback still says processing"
        );
        steps.add("step-3 late callback processing -> " + processing.message() + " | reason=" + processing.reason());

        InstructionRecord finalRecord = requireInstruction(requestNo);
        steps.add("step-4 final db status = " + finalRecord.status() + ", version=" + finalRecord.version());
        return new FlexibleDemoResult(requestNo, finalRecord.status(), steps);
    }

    public FlexibleDemoResult timeoutCompensationDemo(String requestNo, BigDecimal amount, Duration overdueFor, Duration timeout) {
        submitInstruction(requestNo, amount);
        backdateInstruction(requestNo, overdueFor);

        List<String> steps = new ArrayList<>();
        steps.add("step-1 submit payment instruction -> WAIT_RECEIPT");
        steps.add("step-2 mark instruction overdue by " + overdueFor.toMinutes() + " minutes");
        steps.add("step-3 compensate timeout rows = " + compensateTimeoutInstructions(timeout));

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
        InstructionStatus firstReceiptStatus = successReceiptFirst ? InstructionStatus.SUCCESS : InstructionStatus.PROCESSING;
        return projectStyleFlowDemo(
                requestNo,
                amount,
                firstReceiptStatus,
                appendLateProcessingReceipt,
                triggerTimeoutCompensation,
                overdueFor,
                timeout
        );
    }

    public ProjectStyleDistributedTxResult projectStyleFlowDemo(String requestNo,
                                                                BigDecimal amount,
                                                                InstructionStatus firstReceiptStatus,
                                                                boolean appendLateProcessingReceipt,
                                                                boolean triggerTimeoutCompensation,
                                                                Duration overdueFor,
                                                                Duration timeout) {
        validateReceiptStatus(firstReceiptStatus);

        submitInstruction(requestNo, amount);
        List<String> timeline = new ArrayList<>();
        timeline.add("1) 发起支付接口：本地事务创建指令单，requestNo=" + requestNo + ", status=WAIT_RECEIPT");
        timeline.add("2) 银行处理和数据库事务分离，后续只能靠回调和补偿推进结果");

        ReceiptApplyResult firstReceipt = handleBankReceipt(
                requestNo,
                "BANK-RCP-FIRST-" + requestNo,
                firstReceiptStatus,
                "first-bank-callback"
        );
        timeline.add("3) 银行回调接口：status=" + firstReceiptStatus + " -> " + firstReceipt.message()
                + " | reason=" + firstReceipt.reason());

        if (appendLateProcessingReceipt) {
            ReceiptApplyResult processingLate = handleBankReceipt(
                    requestNo,
                    "BANK-RCP-LATE-" + requestNo,
                    InstructionStatus.PROCESSING,
                    "late-processing-callback"
            );
            timeline.add("4) 迟到回调接口：status=PROCESSING -> " + processingLate.message()
                    + " | reason=" + processingLate.reason());
        }

        if (triggerTimeoutCompensation) {
            backdateInstruction(requestNo, overdueFor);
            int updatedRows = compensateTimeoutInstructions(timeout);
            timeline.add("5) 定时补偿任务：扫描超时未终态记录，updatedRows=" + updatedRows);
        }

        InstructionRecord finalRecord = requireInstruction(requestNo);
        timeline.add("6) 最终结果：status=" + finalRecord.status()
                + ", version=" + finalRecord.version()
                + ", lastMessage=" + finalRecord.lastMessage());

        List<String> guarantees = new ArrayList<>();
        guarantees.add("本地事务保证：指令单和初始状态落库原子成功或一起失败");
        guarantees.add("状态机保证：终态一旦写入，迟到的中间态回执不能把状态冲回去");
        guarantees.add("幂等保证：同一 requestNo 只围绕同一条业务记录流转，重复回执不会重复生效");
        guarantees.add("补偿保证：长时间未终态的数据可以通过定时扫描 / 对账收敛到最终结果");

        return new ProjectStyleDistributedTxResult(requestNo, finalRecord.status(), timeline, guarantees);
    }

    private void insertInstruction(String requestNo, BigDecimal amount) {
        jdbcTemplate.update(
                "insert into payment_instruction(request_no, status, amount, version, last_message, created_at, updated_at) values (?, ?, ?, 0, ?, current_timestamp, current_timestamp)",
                requestNo,
                InstructionStatus.WAIT_RECEIPT.name(),
                amount,
                "instruction-created"
        );
    }

    private ReceiptApplyResult applyReceiptInternal(String requestNo, InstructionStatus incomingStatus, String message) {
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

    private int doReconcileTimeoutInstructions(Duration timeout) {
        LocalDateTime deadline = LocalDateTime.now().minus(timeout);
        return jdbcTemplate.update(
                "update payment_instruction set status = 'FAIL', version = version + 1, last_message = 'timeout-reconcile', updated_at = current_timestamp where status in ('WAIT_RECEIPT', 'PROCESSING') and updated_at < ?",
                deadline
        );
    }

    private void validateReceiptStatus(InstructionStatus bankStatus) {
        if (bankStatus == InstructionStatus.WAIT_RECEIPT) {
            throw new IllegalArgumentException("bank receipt status must be PROCESSING, SUCCESS or FAIL");
        }
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
        switch (current) {
            case WAIT_RECEIPT:
                return incoming == InstructionStatus.PROCESSING
                        || incoming == InstructionStatus.SUCCESS
                        || incoming == InstructionStatus.FAIL;
            case PROCESSING:
                return incoming == InstructionStatus.SUCCESS || incoming == InstructionStatus.FAIL;
            case SUCCESS:
            case FAIL:
            default:
                return false;
        }
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

    public static final class InstructionRecord {

        private final String requestNo;
        private final InstructionStatus status;
        private final BigDecimal amount;
        private final long version;
        private final String lastMessage;

        public InstructionRecord(String requestNo, InstructionStatus status, BigDecimal amount, long version, String lastMessage) {
            this.requestNo = requestNo;
            this.status = status;
            this.amount = amount;
            this.version = version;
            this.lastMessage = lastMessage;
        }

        public String requestNo() {
            return requestNo;
        }

        public InstructionStatus status() {
            return status;
        }

        public BigDecimal amount() {
            return amount;
        }

        public long version() {
            return version;
        }

        public String lastMessage() {
            return lastMessage;
        }
    }

    public static final class ReceiptApplyResult {

        private final boolean updated;
        private final InstructionStatus finalStatus;
        private final String message;
        private final String reason;

        public ReceiptApplyResult(boolean updated, InstructionStatus finalStatus, String message, String reason) {
            this.updated = updated;
            this.finalStatus = finalStatus;
            this.message = message;
            this.reason = reason;
        }

        public boolean updated() {
            return updated;
        }

        public InstructionStatus finalStatus() {
            return finalStatus;
        }

        public String message() {
            return message;
        }

        public String reason() {
            return reason;
        }
    }

    public static final class FlexibleDemoResult {

        private final String requestNo;
        private final InstructionStatus finalStatus;
        private final List<String> steps;

        public FlexibleDemoResult(String requestNo, InstructionStatus finalStatus, List<String> steps) {
            this.requestNo = requestNo;
            this.finalStatus = finalStatus;
            this.steps = steps;
        }

        public String requestNo() {
            return requestNo;
        }

        public InstructionStatus finalStatus() {
            return finalStatus;
        }

        public List<String> steps() {
            return steps;
        }
    }

    public static final class ProjectStyleDistributedTxResult {

        private final String requestNo;
        private final InstructionStatus finalStatus;
        private final List<String> timeline;
        private final List<String> guarantees;

        public ProjectStyleDistributedTxResult(String requestNo,
                                               InstructionStatus finalStatus,
                                               List<String> timeline,
                                               List<String> guarantees) {
            this.requestNo = requestNo;
            this.finalStatus = finalStatus;
            this.timeline = timeline;
            this.guarantees = guarantees;
        }

        public String requestNo() {
            return requestNo;
        }

        public InstructionStatus finalStatus() {
            return finalStatus;
        }

        public List<String> timeline() {
            return timeline;
        }

        public List<String> guarantees() {
            return guarantees;
        }
    }
}
