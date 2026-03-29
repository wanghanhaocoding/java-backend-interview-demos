package com.example.redislockdemo.watchdog;

import com.example.redislockdemo.support.LockKeyBuilder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class WatchdogDemoService {

    private final RedissonClient redissonClient;

    public WatchdogDemoService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public long lockWithoutLeaseAndCheckTtl(String jobName, long sleepMillis) throws InterruptedException {
        RLock lock = redissonClient.getLock(LockKeyBuilder.jobKey(jobName));
        lock.lock();
        try {
            Thread.sleep(sleepMillis);
            return lock.remainTimeToLive();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public long lockWithLeaseAndCheckTtl(String jobName, long leaseSeconds, long sleepMillis) throws InterruptedException {
        RLock lock = redissonClient.getLock(LockKeyBuilder.jobKey(jobName));
        lock.lock(leaseSeconds, TimeUnit.SECONDS);
        try {
            Thread.sleep(sleepMillis);
            return lock.remainTimeToLive();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
