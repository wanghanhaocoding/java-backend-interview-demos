package com.example.transactiondemo.tx;

import com.example.transactiondemo.account.AccountService;
import com.example.transactiondemo.audit.AuditService;
import com.example.transactiondemo.tx.exception.BusinessFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PropagationDemoService {

    private final AccountService accountService;
    private final AuditService auditService;

    public PropagationDemoService(AccountService accountService, AuditService auditService) {
        this.accountService = accountService;
        this.auditService = auditService;
    }

    @Transactional
    public void outerRequired(Long fromId, Long toId, BigDecimal amount) {
        accountService.transfer(fromId, toId, amount);
    }

    // 外层失败后，观察 REQUIRES_NEW 的内层记录是否仍然保留。
    @Transactional
    public void outerRequiredThenRequiresNewAndFail(Long fromId, Long toId, BigDecimal amount) {
        accountService.transfer(fromId, toId, amount);
        auditService.saveInNewTransaction("REQUIRES_NEW 已单独提交");
        throw new BusinessFailureException("外层事务失败，账户余额回滚，但审计日志保留");
    }

    public void callMandatoryWithoutTransaction() {
        auditService.mandatoryAudit("没有外层事务，这里会失败");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void callMandatoryWithinTransaction() {
        auditService.mandatoryAudit("存在外层事务，MANDATORY 正常执行");
    }

    // NOT_SUPPORTED 不在事务里执行，所以外层失败后它的结果仍可能保留。
    @Transactional
    public void outerRequiredThenNotSupportedAndFail(Long fromId, Long toId, BigDecimal amount) {
        accountService.transfer(fromId, toId, amount);
        auditService.saveWithoutTransaction("NOT_SUPPORTED 挂起外层事务后执行");
        throw new BusinessFailureException("外层事务失败，但 NOT_SUPPORTED 的结果保留");
    }

    public void callSupportsWithoutTransaction() {
        auditService.supportsAudit("SUPPORTS 在无事务时直接执行");
    }

    // SUPPORTS 遇到外层事务时会加入，所以外层失败时它也会一起回滚。
    @Transactional
    public void outerRequiredThenSupportsAndFail(Long fromId, Long toId, BigDecimal amount) {
        accountService.transfer(fromId, toId, amount);
        auditService.supportsAudit("SUPPORTS 加入外层事务");
        throw new BusinessFailureException("外层事务失败，SUPPORTS 跟着一起回滚");
    }

    public void callNeverWithoutTransaction() {
        auditService.neverAudit("NEVER 在无事务时可以执行");
    }

    @Transactional
    public void callNeverWithinTransaction() {
        auditService.neverAudit("NEVER 在事务中会失败");
    }

    // NESTED 失败时只回滚到 savepoint，外层事务仍然可以继续提交。
    @Transactional
    public void outerRequiredThenNestedFailButOuterCommit(Long fromId, Long toId, BigDecimal amount) {
        accountService.transfer(fromId, toId, amount);
        try {
            auditService.nestedAuditThenFail("NESTED 内层先写后回滚");
        } catch (BusinessFailureException ignored) {
        }
    }
}
