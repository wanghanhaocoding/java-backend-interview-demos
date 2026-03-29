package com.example.transactiondemo.account;

import com.example.transactiondemo.tx.exception.BusinessFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountService {

    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    // REQUIRED 是默认传播行为：成功时一起提交，失败时一起回滚。
    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        Account from = getAccount(fromId);
        Account to = getAccount(toId);
        from.withdraw(amount);
        to.deposit(amount);
        saveBalances(from, to);
    }

    // 这里故意抛出 RuntimeException，用来观察事务回滚。
    @Transactional
    public void transferAndFail(Long fromId, Long toId, BigDecimal amount) {
        Account from = getAccount(fromId);
        Account to = getAccount(toId);
        from.withdraw(amount);
        to.deposit(amount);
        saveBalances(from, to);
        throw new BusinessFailureException("模拟运行时异常，事务会回滚");
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Long id) {
        return getAccount(id).getBalance();
    }

    private void saveBalances(Account from, Account to) {
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
