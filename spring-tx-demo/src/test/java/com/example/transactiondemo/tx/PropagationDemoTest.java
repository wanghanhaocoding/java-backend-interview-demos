package com.example.transactiondemo.tx;

import com.example.transactiondemo.account.AccountService;
import com.example.transactiondemo.audit.AuditService;
import com.example.transactiondemo.tx.exception.BusinessFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PropagationDemoTest {

    @Autowired
    private PropagationDemoService propagationDemoService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuditService auditService;

    @Test
    void requiresNewCommitsEvenIfOuterTransactionFails() {
        assertThatThrownBy(() -> propagationDemoService.outerRequiredThenRequiresNewAndFail(1L, 2L, new BigDecimal("60.00")))
                .isInstanceOf(BusinessFailureException.class);

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1000.00");
        assertThat(auditService.count()).isEqualTo(1);
    }

    @Test
    void mandatoryFailsWithoutExistingTransaction() {
        assertThatThrownBy(() -> propagationDemoService.callMandatoryWithoutTransaction())
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void mandatorySucceedsWithinExistingTransaction() {
        assertThatCode(() -> propagationDemoService.callMandatoryWithinTransaction())
                .doesNotThrowAnyException();

        assertThat(auditService.count()).isEqualTo(1);
    }

    @Test
    void supportsRunsWithoutTransaction() {
        propagationDemoService.callSupportsWithoutTransaction();

        assertThat(auditService.count()).isEqualTo(1);
    }

    @Test
    void supportsJoinsOuterTransactionAndRollsBackWithIt() {
        assertThatThrownBy(() -> propagationDemoService.outerRequiredThenSupportsAndFail(1L, 2L, new BigDecimal("60.00")))
                .isInstanceOf(BusinessFailureException.class);

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1000.00");
        assertThat(auditService.count()).isEqualTo(0);
    }

    @Test
    void notSupportedExecutesOutsideOuterTransactionAndSurvivesOuterRollback() {
        assertThatThrownBy(() -> propagationDemoService.outerRequiredThenNotSupportedAndFail(1L, 2L, new BigDecimal("60.00")))
                .isInstanceOf(BusinessFailureException.class);

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1000.00");
        assertThat(auditService.count()).isEqualTo(1);
    }

    @Test
    void neverSucceedsWithoutTransaction() {
        propagationDemoService.callNeverWithoutTransaction();

        assertThat(auditService.count()).isEqualTo(1);
    }

    @Test
    void neverFailsInsideTransaction() {
        assertThatThrownBy(() -> propagationDemoService.callNeverWithinTransaction())
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void nestedRollsBackInnerWorkButCommitsOuterWork() {
        propagationDemoService.outerRequiredThenNestedFailButOuterCommit(1L, 2L, new BigDecimal("60.00"));

        assertThat(accountService.balanceOf(1L)).isEqualByComparingTo("940.00");
        assertThat(accountService.balanceOf(2L)).isEqualByComparingTo("1060.00");
        assertThat(auditService.count()).isEqualTo(0);
    }
}
