package com.example.redislockdemo.api;

import com.example.redislockdemo.support.LockKeyBuilder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RedissonApiDemoService {

    private final RedissonClient redissonClient;

    public RedissonApiDemoService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public Map<String, Object> demonstrateApis(String name) throws InterruptedException {
        RLock lock = redissonClient.getLock(LockKeyBuilder.apiKey(name));
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("getLock", lock.getName());
        result.put("tryLockImmediate", lock.tryLock());
        result.put("heldByCurrentThreadAfterTry", lock.isHeldByCurrentThread());

        if (lock.isHeldByCurrentThread()) {
            result.put("ttlAfterTry", lock.remainTimeToLive());
            lock.unlock();
        }

        boolean acquiredWithWait = lock.tryLock(1, 5, TimeUnit.SECONDS);
        result.put("tryLockWithWaitAndLease", acquiredWithWait);
        result.put("heldByCurrentThreadAfterWait", lock.isHeldByCurrentThread());
        result.put("ttlAfterLeaseLock", lock.remainTimeToLive());

        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }

        result.put("forceUnlockMeaning", "仅用于异常恢复，不应作为正常业务解锁方式");
        return result;
    }
}
