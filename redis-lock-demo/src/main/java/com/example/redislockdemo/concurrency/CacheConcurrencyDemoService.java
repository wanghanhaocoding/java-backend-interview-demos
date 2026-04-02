package com.example.redislockdemo.concurrency;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CacheConcurrencyDemoService {

    public CacheDemoResult unsafeCheckThenPut(String key, int threadCount) throws InterruptedException {
        // 故意拆成“先判断再放入”，用于展示 check-then-act 竞态。
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger loaderCalls = new AtomicInteger();
        Set<String> createdValues = ConcurrentHashMap.newKeySet();

        runConcurrent(threadCount, () -> {
            if (cache.get(key) == null) {
                String value = loadValue(key, loaderCalls);
                createdValues.add(value);
                cache.put(key, value);
            }
        });
        return CacheDemoResult.of("check-then-put", key, threadCount, loaderCalls.get(), createdValues.size(), cache.get(key));
    }

    public CacheDemoResult putIfAbsent(String key, int threadCount) throws InterruptedException {
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger loaderCalls = new AtomicInteger();
        Set<String> createdValues = ConcurrentHashMap.newKeySet();

        runConcurrent(threadCount, () -> {
            // putIfAbsent 只能保证放入原子，但 candidate 仍可能被多个线程重复创建。
            String candidate = loadValue(key, loaderCalls);
            createdValues.add(candidate);
            cache.putIfAbsent(key, candidate);
        });
        return CacheDemoResult.of("putIfAbsent", key, threadCount, loaderCalls.get(), createdValues.size(), cache.get(key));
    }

    public CacheDemoResult computeIfAbsent(String key, int threadCount) throws InterruptedException {
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger loaderCalls = new AtomicInteger();
        Set<String> createdValues = ConcurrentHashMap.newKeySet();

        runConcurrent(threadCount, () -> {
            // computeIfAbsent 更接近生产里的“没有就初始化一次”。
            String value = cache.computeIfAbsent(key, currentKey -> {
                String created = loadValue(currentKey, loaderCalls);
                createdValues.add(created);
                return created;
            });
            createdValues.add(value);
        });
        return CacheDemoResult.of("computeIfAbsent", key, threadCount, loaderCalls.get(), createdValues.size(), cache.get(key));
    }

    private void runConcurrent(int threadCount, ThrowingRunnable action) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(3, TimeUnit.SECONDS);
                    action.run();
                } catch (Throwable throwable) {
                    synchronized (failures) {
                        failures.add(throwable);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);
        // 统一放行，尽量让多个线程在同一时刻争抢同一个 key。
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Cache demo execution failed", failures.get(0));
        }
    }

    private String loadValue(String key, AtomicInteger loaderCalls) {
        int callNo = loaderCalls.incrementAndGet();
        try {
            Thread.sleep(20);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return key + "-value-call-" + callNo;
    }

    public static final class CacheDemoResult {
        private final String strategy;
        private final String key;
        private final int threadCount;
        private final int loaderCalls;
        private final int distinctCreatedValues;
        private final String finalValue;

        public CacheDemoResult(String strategy,
                               String key,
                               int threadCount,
                               int loaderCalls,
                               int distinctCreatedValues,
                               String finalValue) {
            this.strategy = strategy;
            this.key = key;
            this.threadCount = threadCount;
            this.loaderCalls = loaderCalls;
            this.distinctCreatedValues = distinctCreatedValues;
            this.finalValue = finalValue;
        }

        static CacheDemoResult of(String strategy, String key, int threadCount, int loaderCalls, int distinctCreatedValues, String finalValue) {
            return new CacheDemoResult(strategy, key, threadCount, loaderCalls, distinctCreatedValues, finalValue);
        }

        public String strategy() {
            return strategy;
        }

        public String key() {
            return key;
        }

        public int threadCount() {
            return threadCount;
        }

        public int loaderCalls() {
            return loaderCalls;
        }

        public int distinctCreatedValues() {
            return distinctCreatedValues;
        }

        public String finalValue() {
            return finalValue;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
