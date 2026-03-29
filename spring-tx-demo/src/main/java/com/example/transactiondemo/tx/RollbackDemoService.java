package com.example.transactiondemo.tx;

import com.example.transactiondemo.account.Account;
import com.example.transactiondemo.account.AccountMapper;
import com.example.transactiondemo.audit.AuditService;
import com.example.transactiondemo.tx.exception.BusinessFailureException;
import com.example.transactiondemo.tx.exception.CheckedTransferException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class RollbackDemoService {

    private final AccountMapper accountMapper;
    private final AuditService auditService;

    public RollbackDemoService(AccountMapper accountMapper, AuditService auditService) {
        this.accountMapper = accountMapper;
        this.auditService = auditService;
    }

    @Transactional
    public void runtimeExceptionRollback(Long fromId, Long toId, BigDecimal amount) {
        move(fromId, toId, amount);
        throw new BusinessFailureException("RuntimeException 默认回滚");
    }

    @Transactional
    public void checkedExceptionNoRollback(Long fromId, Long toId, BigDecimal amount) throws CheckedTransferException {
        move(fromId, toId, amount);
        throw new CheckedTransferException("Checked Exception 默认不回滚");
    }

    @Transactional(rollbackFor = CheckedTransferException.class)
    public void checkedExceptionWithRollback(Long fromId, Long toId, BigDecimal amount) throws CheckedTransferException {
        move(fromId, toId, amount);
        throw new CheckedTransferException("声明 rollbackFor 后回滚");
    }

    // 这里虽然抛出了 RuntimeException，但被当前方法 catch 住了，Spring 会把事务当成正常结束并提交。
    @Transactional
    public void swallowRuntimeExceptionAndCommit(Long fromId, Long toId, BigDecimal amount) {
        try {
            move(fromId, toId, amount);
            throw new BusinessFailureException("异常被吞掉后，事务不会回滚");
        } catch (BusinessFailureException ignored) {
        }
    }

    // 当前事务一旦被标记为 rollback-only，外层代码继续执行也没用，最后提交时仍会失败。
    @Transactional
    public void markRollbackOnlyThenFailOnCommit(Long fromId, Long toId, BigDecimal amount) {
        move(fromId, toId, amount);
        auditService.markCurrentTransactionRollbackOnly("当前事务已被标记为 rollback-only");
    }

    private void move(Long fromId, Long toId, BigDecimal amount) {
        Account from = getAccount(fromId);
        Account to = getAccount(toId);
        from.withdraw(amount);
        to.deposit(amount);
        accountMapper.updateBalance(from.getId(), from.getBalance());
        accountMapper.updateBalance(to.getId(), to.getBalance());
    }

    private Account getAccount(Long id) {
        Account account = accountMapper.findById(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        return account;
    }
}
