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

    // 先给出“项目里常见怎么选”的全景图，再往下看具体行为实验。
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
        // 参数故意压小，这样一次提交 7 个任务时能稳定看到 core -> queue -> max -> reject。
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
                        // 任务先阻塞住，避免前面的任务太快执行完，导致观测不到扩容和排队过程。
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
                    // 每次提交后立刻抓一份现场，用来回放线程池状态变化。
                    int queueSize = executor.getQueue().size();
                    queuedPeak.updateAndGet(current -> Math.max(current, queueSize));
                    submissionFlow.add("task-" + taskNo + " accepted, poolSize=" + executor.getPoolSize()
                            + ", active=" + executor.getActiveCount()
                            + ", queue=" + queueSize);
                } catch (RejectedExecutionException ex) {
                    // AbortPolicy 的特点就是快速失败，便于上游立即感知“池子已经满了”。
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
        // 1 个工作线程 + 1 个队列槽位，第三个任务只能退回提交线程自己执行。
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
                    // 这里通常会命中 main 线程，说明 CallerRuns 把压力回推给了调用方。
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
        // shutdown 之后不再接收新任务，但已经进入线程池的任务还会继续跑。
        executor.shutdown();

        boolean rejectedAfterShutdown = false;
        try {
            executor.execute(() -> {
            });
        } catch (RejectedExecutionException ex) {
            rejectedAfterShutdown = true;
        }

        // 这里故意只等很短时间，演示“优雅关闭等不到时，再进入 shutdownNow 兜底”。
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
            // 给线程命名是生产里的基本动作，不然日志和线程 dump 很难排查。
            thread.setName(prefix + "-" + threadIndex.incrementAndGet());
            return thread;
        };
    }

    private void shutdownGracefully(ThreadPoolExecutor executor) throws InterruptedException {
        // 这就是最常见的关闭套路：先温和停止，再限时等待，最后才强制中断。
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
