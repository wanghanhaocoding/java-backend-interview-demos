package com.example.transactiondemo.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Test
    void transferCommitsOnSuccess() {
        accountService.transfer(1L, 2L, new BigDecimal("100.00"));

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("900.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1100.00");
    }

    @Test
    void transferRollsBackOnRuntimeException() {
        try {
            accountService.transferAndFail(1L, 2L, new BigDecimal("100.00"));
            fail("应该抛出 RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e).isInstanceOf(RuntimeException.class);
        }

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1000.00");
    }
}
