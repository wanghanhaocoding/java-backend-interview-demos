package com.example.transactiondemo.audit;

import com.example.transactiondemo.tx.exception.BusinessFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    // REQUIRES_NEW：挂起外层事务，自己开一个新事务。
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInNewTransaction(String message) {
        auditLogMapper.insert(new AuditLog(message));
    }

    // NOT_SUPPORTED：如果外层有事务，先挂起，然后在无事务状态下执行。
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void saveWithoutTransaction(String message) {
        auditLogMapper.insert(new AuditLog(message));
    }

    // MANDATORY：要求调用方已经处于事务中，否则直接报错。
    @Transactional(propagation = Propagation.MANDATORY)
    public void mandatoryAudit(String message) {
        auditLogMapper.insert(new AuditLog(message));
    }

    // SUPPORTS：有事务就加入，没有事务就直接执行。
    @Transactional(propagation = Propagation.SUPPORTS)
    public void supportsAudit(String message) {
        auditLogMapper.insert(new AuditLog(message));
    }

    // NEVER：当前如果已经有事务，就直接报错。
    @Transactional(propagation = Propagation.NEVER)
    public void neverAudit(String message) {
        auditLogMapper.insert(new AuditLog(message));
    }

    // NESTED：在同一个大事务里建立 savepoint，失败时只回滚到这个点。
    @Transactional(propagation = Propagation.NESTED)
    public void nestedAuditThenFail(String message) {
        auditLogMapper.insert(new AuditLog(message));
        throw new BusinessFailureException("NESTED 内层回滚到 savepoint");
    }

    // 加入当前事务后把它标记为 rollback-only，外层代码继续执行也无法成功提交。
    @Transactional(propagation = Propagation.REQUIRED)
    public void markCurrentTransactionRollbackOnly(String message) {
        auditLogMapper.insert(new AuditLog(message));
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }

    public long count() {
        return auditLogMapper.count();
    }
}
