package com.example.distributedtxdemo.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DemoDataResetService {

    private final JdbcTemplate jdbcTemplate;

    public DemoDataResetService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void resetAll() {
        jdbcTemplate.update("delete from consumer_record");
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from demo_order");
        jdbcTemplate.update("delete from payment_instruction");
        jdbcTemplate.update("delete from tcc_reservation");
        jdbcTemplate.update("delete from wallet_account");

        jdbcTemplate.update("insert into wallet_account(account_no, balance, frozen_amount, version) values ('ALICE', 1000.00, 0.00, 0)");
        jdbcTemplate.update("insert into wallet_account(account_no, balance, frozen_amount, version) values ('BOB', 500.00, 0.00, 0)");
    }
}
