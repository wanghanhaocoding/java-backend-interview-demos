package com.example.distributedtxdemo.tcc;

import com.example.distributedtxdemo.common.DemoDataResetService;
import com.example.distributedtxdemo.common.WalletAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TccDemoTest {

    @Autowired
    private TccDemoService tccDemoService;

    @Autowired
    private WalletAccountRepository walletAccountRepository;

    @Autowired
    private DemoDataResetService demoDataResetService;

    @BeforeEach
    void setUp() {
        demoDataResetService.resetAll();
    }

    @Test
    void confirmConsumesFrozenAmount() {
        TccDemoService.TccFlowResult result = tccDemoService.successfulConfirmDemo("TCC-TEST-01", BigDecimal.valueOf(120));

        assertThat(result.finalReservationStatus()).isEqualTo("CONFIRMED");
        assertThat(walletAccountRepository.findByAccountNo("ALICE").balance()).isEqualByComparingTo("880.00");
        assertThat(walletAccountRepository.findByAccountNo("ALICE").frozenAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void emptyCancelBlocksLateTryAndNormalCancelReleasesFrozenAmount() {
        TccDemoService.TccFlowResult result = tccDemoService.cancelAndEmptyCancelDemo("TCC-EMPTY-TEST", "TCC-TEST-02", BigDecimal.valueOf(90));

        assertThat(result.finalReservationStatus()).isEqualTo("CANCELED");
        assertThat(walletAccountRepository.findByAccountNo("ALICE").balance()).isEqualByComparingTo("1000.00");
        assertThat(walletAccountRepository.findByAccountNo("ALICE").frozenAmount()).isEqualByComparingTo("0.00");
        assertThat(tccDemoService.reservationStatus("TCC-EMPTY-TEST")).isEqualTo("CANCELED");
    }
}
