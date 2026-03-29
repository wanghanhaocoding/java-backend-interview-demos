package com.example.redislockdemo.nativeapi;

import com.example.redislockdemo.support.ExecutionTracker;
import com.example.redislockdemo.support.LockKeyBuilder;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
public class NativeRedisLockService {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "end "
                    + "return 0";

    private final StringRedisTemplate stringRedisTemplate;
    private final ExecutionTracker executionTracker;
    private final DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    public NativeRedisLockService(StringRedisTemplate stringRedisTemplate, ExecutionTracker executionTracker) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.executionTracker = executionTracker;
    }

    public String tryAcquireOrderLock(String orderNo, Duration leaseTime) {
        String key = LockKeyBuilder.orderKey(orderNo);
        String token = UUID.randomUUID() + ":" + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, token, leaseTime);
        return Boolean.TRUE.equals(success) ? token : null;
    }

    public boolean releaseOrderLock(String orderNo, String token) {
        Long result = stringRedisTemplate.execute(unlockScript, Collections.singletonList(LockKeyBuilder.orderKey(orderNo)), token);
        return result != null && result > 0;
    }

    public boolean directDeleteForTeaching(String orderNo) {
        Boolean result = stringRedisTemplate.delete(LockKeyBuilder.orderKey(orderNo));
        return Boolean.TRUE.equals(result);
    }

    public long remainTimeToLive(String orderNo) {
        Long ttl = stringRedisTemplate.getExpire(LockKeyBuilder.orderKey(orderNo));
        return ttl == null ? -2 : ttl;
    }

    public int executeProtectedBusiness(String orderNo, Duration leaseTime) {
        String token = tryAcquireOrderLock(orderNo, leaseTime);
        if (token == null) {
            return executionTracker.get();
        }
        try {
            return executionTracker.incrementAndGet();
        } finally {
            releaseOrderLock(orderNo, token);
        }
    }

    public void clearLock(String orderNo) {
        stringRedisTemplate.delete(LockKeyBuilder.orderKey(orderNo));
    }

    public String readCurrentToken(String orderNo) {
        return stringRedisTemplate.opsForValue().get(LockKeyBuilder.orderKey(orderNo));
    }
}
