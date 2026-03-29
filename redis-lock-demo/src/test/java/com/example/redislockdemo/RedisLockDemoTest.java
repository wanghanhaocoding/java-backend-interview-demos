package com.example.redislockdemo;

import com.example.redislockdemo.nativeapi.NativeRedisLockService;
import com.example.redislockdemo.redisson.RedissonLockService;
import com.example.redislockdemo.support.ExecutionTracker;
import com.example.redislockdemo.watchdog.WatchdogDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.RedisConnectionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RedisLockDemoTest {

    @Autowired
    private NativeRedisLockService nativeRedisLockService;

    @Autowired
    private RedissonLockService redissonLockService;

    @Autowired
    private WatchdogDemoService watchdogDemoService;

    @Autowired
    private ExecutionTracker executionTracker;

    @BeforeEach
    void setUp() {
        assumeTrue(redisAvailable());
        executionTracker.reset();
    }

    @Test
    void nativeLockPreventsDuplicateExecution() {
        String token = nativeRedisLockService.tryAcquireOrderLock("T100", Duration.ofSeconds(5));
        assertThat(token).isNotNull();

        String secondToken = nativeRedisLockService.tryAcquireOrderLock("T100", Duration.ofSeconds(5));
        assertThat(secondToken).isNull();

        assertThat(nativeRedisLockService.releaseOrderLock("T100", token)).isTrue();
    }

    @Test
    void nativeUnlockRequiresMatchingOwnerToken() {
        String token = nativeRedisLockService.tryAcquireOrderLock("T101", Duration.ofSeconds(5));
        assertThat(token).isNotNull();

        assertThat(nativeRedisLockService.releaseOrderLock("T101", "wrong-token")).isFalse();
        assertThat(nativeRedisLockService.readCurrentToken("T101")).isEqualTo(token);
        assertThat(nativeRedisLockService.releaseOrderLock("T101", token)).isTrue();
    }

    @Test
    void redissonTryLockFailsWhenAnotherThreadHoldsLock() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockReady = new CountDownLatch(1);
        redissonLockService.lockOrder("T102", 10, TimeUnit.SECONDS);
        try {
            Future<Boolean> future = executor.submit(() -> {
                lockReady.await(3, TimeUnit.SECONDS);
                boolean acquired = redissonLockService.tryLockOrder("T102", 0, 5, TimeUnit.SECONDS);
                if (acquired && redissonLockService.isHeldByCurrentThread("T102")) {
                    redissonLockService.unlockOrder("T102");
                }
                return acquired;
            });

            lockReady.countDown();
            assertThat(future.get(3, TimeUnit.SECONDS)).isFalse();
        } finally {
            if (redissonLockService.isHeldByCurrentThread("T102")) {
                redissonLockService.unlockOrder("T102");
            }
            executor.shutdownNow();
        }
    }

    @Test
    void redissonLockIsReentrantForSameThread() {
        assertThat(redissonLockService.reentrantDemo("T103")).isEqualTo(1);
    }

    @Test
    void watchdogExtendsLeaseWhenLeaseTimeNotSpecified() throws Exception {
        long ttl = watchdogDemoService.lockWithoutLeaseAndCheckTtl("job-watchdog", 1500);
        assertThat(ttl).isGreaterThan(0);
    }

    @Test
    void explicitLeaseTimeDoesNotUseWatchdog() throws Exception {
        long ttl = watchdogDemoService.lockWithLeaseAndCheckTtl("job-lease", 5, 1500);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(5000);
    }

    private boolean redisAvailable() {
        try {
            nativeRedisLockService.clearLock("health-check");
            redissonLockService.forceUnlockOrder("health-check");
            return true;
        } catch (RedisConnectionException ex) {
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
}
