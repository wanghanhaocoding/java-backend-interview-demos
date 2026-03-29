package com.example.transactiondemo.account;

import java.math.BigDecimal;

public class Account {

    private Long id;
    private String owner;
    private BigDecimal balance;

    public Account() {
    }

    public Account(Long id, String owner, BigDecimal balance) {
        this.id = id;
        this.owner = owner;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void withdraw(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
