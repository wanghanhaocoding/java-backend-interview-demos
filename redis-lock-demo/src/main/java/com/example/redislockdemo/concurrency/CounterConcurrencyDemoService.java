package com.example.redislockdemo.concurrency;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Service
public class CounterConcurrencyDemoService {

    public CounterDemoResult unsafeCountPlusPlus(int threadCount, int incrementsPerThread) throws InterruptedException {
        // 故意保留 count++，用于展示“读-改-写”不是原子操作。
        PlainCounter counter = new PlainCounter();
        runConcurrent(threadCount, () -> {
            for (int i = 0; i < incrementsPerThread; i++) {
                counter.increment();
            }
        });
        return CounterDemoResult.of("count++", threadCount, incrementsPerThread, counter.get());
    }

    public CounterDemoResult atomicInteger(int threadCount, int incrementsPerThread) throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        runConcurrent(threadCount, () -> {
            for (int i = 0; i < incrementsPerThread; i++) {
                counter.incrementAndGet();
            }
        });
        return CounterDemoResult.of("AtomicInteger", threadCount, incrementsPerThread, counter.get());
    }

    public CounterDemoResult longAdder(int threadCount, int incrementsPerThread) throws InterruptedException {
        // LongAdder 也是线程安全的，适合高并发热点计数场景。
        LongAdder counter = new LongAdder();
        runConcurrent(threadCount, () -> {
            for (int i = 0; i < incrementsPerThread; i++) {
                counter.increment();
            }
        });
        return CounterDemoResult.of("LongAdder", threadCount, incrementsPerThread, counter.intValue());
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
        // 统一放行，尽量让线程同时开始，方便把竞态放大出来。
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Counter demo execution failed", failures.get(0));
        }
    }

    public record CounterDemoResult(String strategy, int threadCount, int incrementsPerThread, int expected, int actual, int lostUpdates) {

        static CounterDemoResult of(String strategy, int threadCount, int incrementsPerThread, int actual) {
            int expected = threadCount * incrementsPerThread;
            return new CounterDemoResult(strategy, threadCount, incrementsPerThread, expected, actual, expected - actual);
        }
    }

    private static final class PlainCounter {
        private int value;

        void increment() {
            value++;
        }

        int get() {
            return value;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
