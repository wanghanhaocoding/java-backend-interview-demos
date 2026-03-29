package com.example.mysqllockmvccdemo.lock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class LockingDemoTest {

    @Autowired
    private LockingDemoService lockingDemoService;

    @Test
    void gapLockShouldOnlyBlockValuesInsideRange() {
        LockingDemoService.GapLockResult result = lockingDemoService.gapLockDemo();

        assertThat(result.insertInRangeBlocked()).isTrue();
        assertThat(result.insertOutOfRangeBlocked()).isFalse();
    }

    @Test
    void deadlockCycleShouldBeDetected() {
        LockingDemoService.DeadlockResult result = lockingDemoService.deadlockDemo();

        assertThat(result.deadlockDetected()).isTrue();
    }
}
