package com.example.transactiondemo.tx;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SelfInvocationDemoService {

    public boolean callInnerByThis() {
        return innerTransactional();
    }

    @Transactional
    public boolean innerTransactional() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
