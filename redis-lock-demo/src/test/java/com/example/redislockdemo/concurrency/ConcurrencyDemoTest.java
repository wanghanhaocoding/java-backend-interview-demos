package com.example.redislockdemo.concurrency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyDemoTest {

    private final CounterConcurrencyDemoService counterConcurrencyDemoService = new CounterConcurrencyDemoService();
    private final CacheConcurrencyDemoService cacheConcurrencyDemoService = new CacheConcurrencyDemoService();

    @Test
    void unsafeCountPlusPlusLosesUpdates() throws Exception {
        CounterConcurrencyDemoService.CounterDemoResult result = counterConcurrencyDemoService.unsafeCountPlusPlus(12, 100_000);

        assertThat(result.actual()).isLessThan(result.expected());
        assertThat(result.lostUpdates()).isGreaterThan(0);
    }

    @Test
    void atomicIntegerKeepsExpectedCount() throws Exception {
        CounterConcurrencyDemoService.CounterDemoResult result = counterConcurrencyDemoService.atomicInteger(12, 100_000);

        assertThat(result.actual()).isEqualTo(result.expected());
        assertThat(result.lostUpdates()).isZero();
    }

    @Test
    void longAdderKeepsExpectedCount() throws Exception {
        CounterConcurrencyDemoService.CounterDemoResult result = counterConcurrencyDemoService.longAdder(12, 100_000);

        assertThat(result.actual()).isEqualTo(result.expected());
        assertThat(result.lostUpdates()).isZero();
    }

    @Test
    void unsafeCheckThenPutTriggersDuplicateLoads() throws Exception {
        CacheConcurrencyDemoService.CacheDemoResult result = cacheConcurrencyDemoService.unsafeCheckThenPut("user:42", 12);

        assertThat(result.loaderCalls()).isGreaterThan(1);
        assertThat(result.distinctCreatedValues()).isGreaterThan(1);
    }

    @Test
    void putIfAbsentStillMayDuplicateLoads() throws Exception {
        CacheConcurrencyDemoService.CacheDemoResult result = cacheConcurrencyDemoService.putIfAbsent("user:42", 12);

        assertThat(result.loaderCalls()).isGreaterThan(1);
        assertThat(result.finalValue()).isNotNull();
    }

    @Test
    void computeIfAbsentInitializesOnlyOnce() throws Exception {
        CacheConcurrencyDemoService.CacheDemoResult result = cacheConcurrencyDemoService.computeIfAbsent("user:42", 12);

        assertThat(result.loaderCalls()).isEqualTo(1);
        assertThat(result.distinctCreatedValues()).isEqualTo(1);
        assertThat(result.finalValue()).isEqualTo("user:42-value-call-1");
    }
}
