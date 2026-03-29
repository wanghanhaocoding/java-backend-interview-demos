package com.example.mqcacheidempotencydemo.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class CacheConsistencyDemoTest {

    @Autowired
    private CacheConsistencyDemoService cacheConsistencyDemoService;

    @Test
    void staleReadShouldAppearWhenCacheIsNotDeleted() {
        CacheConsistencyDemoService.CacheReadResult result =
                cacheConsistencyDemoService.staleReadDemo("product:1001", 100, 80);

        assertThat(result.databaseValue()).isEqualTo(80);
        assertThat(result.staleCacheValue()).isEqualTo(100);
        assertThat(result.cacheValue()).isEqualTo(100);
    }

    @Test
    void delayedDoubleDeleteShouldReloadFreshValue() {
        CacheConsistencyDemoService.CacheRepairResult result =
                cacheConsistencyDemoService.delayedDoubleDeleteDemo("product:1001", 100, 80);

        assertThat(result.finalReadValue()).isEqualTo(80);
        assertThat(result.finalCacheValue()).isEqualTo(80);
    }

    @Test
    void mutexProtectionShouldReduceLoaders() {
        CacheConsistencyDemoService.BreakdownProtectionResult result =
                cacheConsistencyDemoService.breakdownProtectionDemo("product:hot", 6);

        assertThat(result.loadersWithoutMutex()).isEqualTo(6);
        assertThat(result.loadersWithMutex()).isEqualTo(1);
    }
}
