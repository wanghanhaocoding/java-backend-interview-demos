package com.example.redislockdemo.concurrency;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ThreadPoolTeachingDemoService {

    public List<PoolTypeNote> commonPoolTypesOverview() {
        return List.of(
                new PoolTypeNote("fixed-business-pool", "接口聚合、批量异步处理", "显式 new ThreadPoolExecutor + 有界队列", "生产里更常见的默认选择"),
                new PoolTypeNote("single-thread-executor", "严格顺序执行的本地任务", "单线程池", "适合串行消费，但也要关注队列堆积"),
                new PoolTypeNote("scheduled-thread-pool", "延迟任务、定时任务", "ScheduledExecutorService", "适合定时调度，不适合替代通用业务池"),
                new PoolTypeNote("executors-factory-warning", "旧代码里常见", "Executors.newFixedThreadPool / newCachedThreadPool", "教学可看，生产不要无脑直接用")
        );
    }

    public TaskFlowDemoResult taskFlowAndAbortPolicyDemo() throws InterruptedException {
        AtomicInteger threadIndex = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,
                4,
                30,
                TimeUnit.SECONDS,
                // 有界队列更符合生产里的容量控制思路。
                new ArrayBlockingQueue<>(2),
                namedThreadFactory("teaching-pool", threadIndex),
                new ThreadPoolExecutor.AbortPolicy()
        );

        List<String> submissionFlow = new ArrayList<>();
        AtomicInteger startedTasks = new AtomicInteger();
        AtomicInteger rejectedTasks = new AtomicInteger();
        AtomicInteger queuedPeak = new AtomicInteger();
        List<String> executingThreads = new ArrayList<>();

        try {
            for (int i = 1; i <= 7; i++) {
                int taskNo = i;
                try {
                    executor.execute(() -> {
                        startedTasks.incrementAndGet();
                        synchronized (executingThreads) {
                            executingThreads.add(Thread.currentThread().getName());
                        }
                        try {
                            release.await(3, TimeUnit.SECONDS);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    int queueSize = executor.getQueue().size();
                    queuedPeak.updateAndGet(current -> Math.max(current, queueSize));
                    submissionFlow.add("task-" + taskNo + " accepted, poolSize=" + executor.getPoolSize()
                            + ", active=" + executor.getActiveCount()
                            + ", queue=" + queueSize);
                } catch (RejectedExecutionException ex) {
                    rejectedTasks.incrementAndGet();
                    submissionFlow.add("task-" + taskNo + " rejected");
                }
            }
        } finally {
            release.countDown();
            shutdownGracefully(executor);
        }

        return new TaskFlowDemoResult(
                2,
                4,
                2,
                7,
                startedTasks.get(),
                queuedPeak.get(),
                executor.getLargestPoolSize(),
                rejectedTasks.get(),
                submissionFlow,
                List.copyOf(executingThreads)
        );
    }

    public CallerRunsDemoResult callerRunsPolicyDemo() throws InterruptedException {
        AtomicInteger threadIndex = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                namedThreadFactory("caller-runs-pool", threadIndex),
                // 满载后把压力回推给调用方线程。
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        AtomicInteger callerRunsCount = new AtomicInteger();
        List<String> executionThreads = new ArrayList<>();

        try {
            for (int i = 1; i <= 3; i++) {
                executor.execute(() -> {
                    String threadName = Thread.currentThread().getName();
                    synchronized (executionThreads) {
                        executionThreads.add(threadName);
                    }
                    if (threadName.contains("main")) {
                        callerRunsCount.incrementAndGet();
                    }
                    try {
                        release.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        } finally {
            release.countDown();
            shutdownGracefully(executor);
        }

        return new CallerRunsDemoResult(callerRunsCount.get(), List.copyOf(executionThreads));
    }

    public ShutdownDemoResult shutdownLifecycleDemo() throws InterruptedException {
        AtomicInteger threadIndex = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                namedThreadFactory("shutdown-pool", threadIndex),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger interruptedTasks = new AtomicInteger();

        executor.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                interruptedTasks.incrementAndGet();
                Thread.currentThread().interrupt();
            }
        });
        executor.execute(() -> {
        });
        executor.execute(() -> {
        });

        started.await(3, TimeUnit.SECONDS);
        executor.shutdown();

        boolean rejectedAfterShutdown = false;
        try {
            executor.execute(() -> {
            });
        } catch (RejectedExecutionException ex) {
            rejectedAfterShutdown = true;
        }

        boolean terminatedGracefully = executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        List<Runnable> neverStarted = new ArrayList<>();
        if (!terminatedGracefully) {
            neverStarted = executor.shutdownNow();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }

        return new ShutdownDemoResult(
                true,
                rejectedAfterShutdown,
                terminatedGracefully,
                neverStarted.size(),
                interruptedTasks.get(),
                executor.isTerminated()
        );
    }

    private ThreadFactory namedThreadFactory(String prefix, AtomicInteger threadIndex) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + threadIndex.incrementAndGet());
            return thread;
        };
    }

    private void shutdownGracefully(ThreadPoolExecutor executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    public record PoolTypeNote(String poolName, String useCase, String creationStyle, String note) {
    }

    public record TaskFlowDemoResult(int corePoolSize,
                                     int maxPoolSize,
                                     int queueCapacity,
                                     int submittedTasks,
                                     int startedTasks,
                                     int queuedPeak,
                                     int largestPoolSize,
                                     int rejectedTasks,
                                     List<String> submissionFlow,
                                     List<String> executingThreads) {
    }

    public record CallerRunsDemoResult(int callerRunsCount, List<String> executionThreads) {
    }

    public record ShutdownDemoResult(boolean shutdownCalled,
                                     boolean rejectedAfterShutdown,
                                     boolean terminatedGracefully,
                                     int neverStartedTasks,
                                     int interruptedTasks,
                                     boolean terminatedFinally) {
    }
}
