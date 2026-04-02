package com.example.distributedtxdemo.common;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class WalletAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public WalletAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WalletAccount findByAccountNo(String accountNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "select account_no, balance, frozen_amount, version from wallet_account where account_no = ?",
                    (rs, rowNum) -> new WalletAccount(
                            rs.getString("account_no"),
                            rs.getBigDecimal("balance"),
                            rs.getBigDecimal("frozen_amount"),
                            rs.getLong("version")
                    ),
                    accountNo
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public void changeBalance(String accountNo, BigDecimal delta) {
        jdbcTemplate.update(
                "update wallet_account set balance = balance + ?, version = version + 1 where account_no = ?",
                delta,
                accountNo
        );
    }

    public void addFrozen(String accountNo, BigDecimal amount) {
        jdbcTemplate.update(
                "update wallet_account set frozen_amount = frozen_amount + ?, version = version + 1 where account_no = ?",
                amount,
                accountNo
        );
    }

    public void releaseFrozen(String accountNo, BigDecimal amount) {
        jdbcTemplate.update(
                "update wallet_account set frozen_amount = frozen_amount - ?, version = version + 1 where account_no = ?",
                amount,
                accountNo
        );
    }

    public void commitFrozen(String accountNo, BigDecimal amount) {
        jdbcTemplate.update(
                "update wallet_account set balance = balance - ?, frozen_amount = frozen_amount - ?, version = version + 1 where account_no = ?",
                amount,
                amount,
                accountNo
        );
    }

    public static final class WalletAccount {

        private final String accountNo;
        private final BigDecimal balance;
        private final BigDecimal frozenAmount;
        private final long version;

        public WalletAccount(String accountNo, BigDecimal balance, BigDecimal frozenAmount, long version) {
            this.accountNo = accountNo;
            this.balance = balance;
            this.frozenAmount = frozenAmount;
            this.version = version;
        }

        public String accountNo() {
            return accountNo;
        }

        public BigDecimal balance() {
            return balance;
        }

        public BigDecimal frozenAmount() {
            return frozenAmount;
        }

        public long version() {
            return version;
        }

        public BigDecimal availableAmount() {
            return balance.subtract(frozenAmount);
        }
    }
}
