package com.example.distributedtxdemo.localtx;

import com.example.distributedtxdemo.common.WalletAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class LocalTransactionDemoService {

    private final WalletAccountRepository walletAccountRepository;

    public LocalTransactionDemoService(WalletAccountRepository walletAccountRepository) {
        this.walletAccountRepository = walletAccountRepository;
    }

    @Transactional
    public void transfer(String fromAccountNo, String toAccountNo, BigDecimal amount) {
        WalletAccountRepository.WalletAccount from = requireAccount(fromAccountNo);
        requireEnoughBalance(from, amount);

        walletAccountRepository.changeBalance(fromAccountNo, amount.negate());
        walletAccountRepository.changeBalance(toAccountNo, amount);
    }

    @Transactional
    public void transferAndFail(String fromAccountNo, String toAccountNo, BigDecimal amount) {
        transfer(fromAccountNo, toAccountNo, amount);
        throw new IllegalStateException("模拟外层业务异常，触发本地事务回滚");
    }

    private WalletAccountRepository.WalletAccount requireAccount(String accountNo) {
        WalletAccountRepository.WalletAccount account = walletAccountRepository.findByAccountNo(accountNo);
        if (account == null) {
            throw new IllegalArgumentException("账户不存在: " + accountNo);
        }
        return account;
    }

    private void requireEnoughBalance(WalletAccountRepository.WalletAccount account, BigDecimal amount) {
        if (account.balance().compareTo(amount) < 0) {
            throw new IllegalStateException("余额不足: account=" + account.accountNo());
        }
    }
}
