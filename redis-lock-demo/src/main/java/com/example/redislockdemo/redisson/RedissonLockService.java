package com.example.redislockdemo.redisson;

import com.example.redislockdemo.support.ExecutionTracker;
import com.example.redislockdemo.support.LockKeyBuilder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedissonLockService {

    private final RedissonClient redissonClient;
    private final ExecutionTracker executionTracker;

    public RedissonLockService(RedissonClient redissonClient, ExecutionTracker executionTracker) {
        this.redissonClient = redissonClient;
        this.executionTracker = executionTracker;
    }

    public boolean tryLockOrder(String orderNo, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return getOrderLock(orderNo).tryLock(waitTime, leaseTime, unit);
    }

    public void lockOrder(String orderNo) {
        getOrderLock(orderNo).lock();
    }

    public void lockOrder(String orderNo, long leaseTime, TimeUnit unit) {
        getOrderLock(orderNo).lock(leaseTime, unit);
    }

    public void unlockOrder(String orderNo) {
        getOrderLock(orderNo).unlock();
    }

    public boolean isHeldByCurrentThread(String orderNo) {
        return getOrderLock(orderNo).isHeldByCurrentThread();
    }

    public boolean forceUnlockOrder(String orderNo) {
        return getOrderLock(orderNo).forceUnlock();
    }

    public long remainTimeToLive(String orderNo) {
        return getOrderLock(orderNo).remainTimeToLive();
    }

    public int executeWithTryLock(String orderNo, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        RLock lock = getOrderLock(orderNo);
        if (!lock.tryLock(waitTime, leaseTime, unit)) {
            return executionTracker.get();
        }
        try {
            return executionTracker.incrementAndGet();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public int reentrantDemo(String orderNo) {
        RLock lock = getOrderLock(orderNo);
        lock.lock();
        try {
            return innerReentrant(lock);
        } finally {
            lock.unlock();
        }
    }

    private int innerReentrant(RLock lock) {
        lock.lock();
        try {
            return executionTracker.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    public RLock getOrderLock(String orderNo) {
        return redissonClient.getLock(LockKeyBuilder.orderKey(orderNo));
    }
}
