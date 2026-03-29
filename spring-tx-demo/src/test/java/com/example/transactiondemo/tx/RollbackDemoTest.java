package com.example.transactiondemo.tx;

import com.example.transactiondemo.account.AccountService;
import com.example.transactiondemo.tx.exception.BusinessFailureException;
import com.example.transactiondemo.tx.exception.CheckedTransferException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.UnexpectedRollbackException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RollbackDemoTest {

    @Autowired
    private RollbackDemoService rollbackDemoService;

    @Autowired
    private AccountService accountService;

    @Test
    void runtimeExceptionTriggersRollback() {
        assertThatThrownBy(() -> rollbackDemoService.runtimeExceptionRollback(1L, 2L, new BigDecimal("50.00")))
                .isInstanceOf(BusinessFailureException.class);

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1000.00");
    }

    @Test
    void checkedExceptionDoesNotRollbackByDefault() {
        assertThatThrownBy(() -> rollbackDemoService.checkedExceptionNoRollback(1L, 2L, new BigDecimal("50.00")))
                .isInstanceOf(CheckedTransferException.class);

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("950.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1050.00");
    }

    @Test
    void rollbackForMakesCheckedExceptionRollback() {
        assertThatThrownBy(() -> rollbackDemoService.checkedExceptionWithRollback(1L, 2L, new BigDecimal("50.00")))
                .isInstanceOf(CheckedTransferException.class);

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1000.00");
    }

    @Test
    void swallowedRuntimeExceptionMakesTransactionCommit() {
        rollbackDemoService.swallowRuntimeExceptionAndCommit(1L, 2L, new BigDecimal("50.00"));

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("950.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1050.00");
    }

    @Test
    void rollbackOnlyCausesUnexpectedRollbackExceptionAtCommitTime() {
        assertThatThrownBy(() -> rollbackDemoService.markRollbackOnlyThenFailOnCommit(1L, 2L, new BigDecimal("50.00")))
                .isInstanceOf(UnexpectedRollbackException.class);

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1000.00");
    }
}
