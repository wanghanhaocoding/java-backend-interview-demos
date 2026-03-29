package com.example.mysqllockmvccdemo.mvcc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class MvccDemoTest {

    @Autowired
    private MvccDemoService mvccDemoService;

    @Test
    void snapshotReadShouldSeeOlderVersionWhileCurrentReadSeesLatest() {
        MvccDemoService.MvccSnapshotResult result = mvccDemoService.versionChainDemo();

        assertThat(result.versionCount()).isEqualTo(2);
        assertThat(result.snapshotReadValue()).isEqualTo(100);
        assertThat(result.currentReadValue()).isEqualTo(120);
    }
}
