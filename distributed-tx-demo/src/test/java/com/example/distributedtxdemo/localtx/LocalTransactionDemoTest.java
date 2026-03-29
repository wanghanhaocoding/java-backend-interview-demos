package com.example.distributedtxdemo.localtx;

import com.example.distributedtxdemo.common.DemoDataResetService;
import com.example.distributedtxdemo.common.WalletAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LocalTransactionDemoTest {

    @Autowired
    private LocalTransactionDemoService localTransactionDemoService;

    @Autowired
    private WalletAccountRepository walletAccountRepository;

    @Autowired
    private DemoDataResetService demoDataResetService;

    @BeforeEach
    void setUp() {
        demoDataResetService.resetAll();
    }

    @Test
    void transferCommitsSuccessfully() {
        localTransactionDemoService.transfer("ALICE", "BOB", BigDecimal.valueOf(100));

        assertThat(walletAccountRepository.findByAccountNo("ALICE").balance()).isEqualByComparingTo("900.00");
        assertThat(walletAccountRepository.findByAccountNo("BOB").balance()).isEqualByComparingTo("600.00");
    }

    @Test
    void transferAndFailRollsBack() {
        assertThatThrownBy(() -> localTransactionDemoService.transferAndFail("ALICE", "BOB", BigDecimal.valueOf(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("本地事务回滚");

        assertThat(walletAccountRepository.findByAccountNo("ALICE").balance()).isEqualByComparingTo("1000.00");
        assertThat(walletAccountRepository.findByAccountNo("BOB").balance()).isEqualByComparingTo("500.00");
    }
}
