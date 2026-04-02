package com.example.redislockdemo.concurrency;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        return Arrays.asList(
                new PoolTypeNote("fixed-business-pool", "接口聚合、批量异步处理", "显式 new ThreadPoolExecutor + 有界队列", "生产里更常见的默认选择"),
                new PoolTypeNote("single-thread-executor", "严格顺序执行的本地任务", "单线程池", "适合串行消费，但也要关注队列堆积"),
                new PoolTypeNote("scheduled-thread-pool", "延迟任务、定时任务", "ScheduledExecutorService", "适合定时调度，不适合替代通用业务池"),
                new PoolTypeNote("executors-factory-warning", "旧代码里常见", "Executors.newFixedThreadPool / newCachedThreadPool", "教学可看，生产不要无脑直接用")
        );
    }

    public TaskFlowDemoResult taskFlowAndAbortPolicyDemo() throws InterruptedException {
        int corePoolSize = 2;
        int maxPoolSize = 4;
        int queueCapacity = 2;
        int submittedTasks = 7;
        AtomicInteger threadIndex = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        // 模拟“银行回执/异步回调消费池”在瞬时洪峰下被打满的场景。
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                30,
                TimeUnit.SECONDS,
                // 生产里更常见的是有界队列：先限流，再暴露过载，而不是无限堆任务。
                new ArrayBlockingQueue<>(queueCapacity),
                namedThreadFactory("receipt-callback-worker", threadIndex),
                new ThreadPoolExecutor.AbortPolicy()
        );

        List<String> submissionFlow = new ArrayList<>();
        AtomicInteger startedTasks = new AtomicInteger();
        AtomicInteger rejectedTasks = new AtomicInteger();
        AtomicInteger queuedPeak = new AtomicInteger();
        List<String> executingThreads = new ArrayList<>();

        try {
            for (int i = 1; i <= submittedTasks; i++) {
                int taskNo = i;
                String taskName = "receipt-callback-batch-" + (taskNo < 10 ? "0" + taskNo : taskNo);
                int poolSizeBefore = executor.getPoolSize();
                int queueSizeBefore = executor.getQueue().size();
                try {
                    executor.execute(() -> {
                        // 故意让任务慢一点，模拟第三方回调处理变慢，方便观察池子如何扩容和排队。
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
                    int poolSizeAfter = executor.getPoolSize();
                    int queueSizeAfter = executor.getQueue().size();
                    queuedPeak.updateAndGet(current -> Math.max(current, queueSizeAfter));
                    submissionFlow.add(taskName + " accepted, route="
                            + detectSubmissionRoute(corePoolSize, queueCapacity, poolSizeBefore, poolSizeAfter, queueSizeBefore, queueSizeAfter)
                            + ", poolSize=" + poolSizeAfter
                            + ", active=" + executor.getActiveCount()
                            + ", queue=" + queueSizeAfter);
                } catch (RejectedExecutionException ex) {
                    // AbortPolicy 的特点就是快速失败，便于上游立即感知“池子已经满了”。
                    rejectedTasks.incrementAndGet();
                    submissionFlow.add(taskName + " rejected, route=abort-policy, poolSize=" + executor.getPoolSize()
                            + ", active=" + executor.getActiveCount()
                            + ", queue=" + executor.getQueue().size());
                }
            }
        } finally {
            release.countDown();
            shutdownGracefully(executor);
        }

        return new TaskFlowDemoResult(
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                submittedTasks,
                startedTasks.get(),
                queuedPeak.get(),
                executor.getLargestPoolSize(),
                rejectedTasks.get(),
                submissionFlow,
                immutableListCopy(executingThreads)
        );
    }

    private String detectSubmissionRoute(int corePoolSize,
                                         int queueCapacity,
                                         int poolSizeBefore,
                                         int poolSizeAfter,
                                         int queueSizeBefore,
                                         int queueSizeAfter) {
        if (poolSizeBefore < corePoolSize && poolSizeAfter > poolSizeBefore) {
            return "core-thread";
        }
        if (queueSizeBefore < queueCapacity && queueSizeAfter > queueSizeBefore) {
            return "queue";
        }
        if (poolSizeAfter > poolSizeBefore) {
            return "max-thread";
        }
        return "accepted";
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

        return new CallerRunsDemoResult(callerRunsCount.get(), immutableListCopy(executionThreads));
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

    private List<String> immutableListCopy(List<String> source) {
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    public static final class PoolTypeNote {
        private final String poolName;
        private final String useCase;
        private final String creationStyle;
        private final String note;

        public PoolTypeNote(String poolName, String useCase, String creationStyle, String note) {
            this.poolName = poolName;
            this.useCase = useCase;
            this.creationStyle = creationStyle;
            this.note = note;
        }

        public String poolName() {
            return poolName;
        }

        public String useCase() {
            return useCase;
        }

        public String creationStyle() {
            return creationStyle;
        }

        public String note() {
            return note;
        }
    }

    public static final class TaskFlowDemoResult {
        private final int corePoolSize;
        private final int maxPoolSize;
        private final int queueCapacity;
        private final int submittedTasks;
        private final int startedTasks;
        private final int queuedPeak;
        private final int largestPoolSize;
        private final int rejectedTasks;
        private final List<String> submissionFlow;
        private final List<String> executingThreads;

        public TaskFlowDemoResult(int corePoolSize,
                                  int maxPoolSize,
                                  int queueCapacity,
                                  int submittedTasks,
                                  int startedTasks,
                                  int queuedPeak,
                                  int largestPoolSize,
                                  int rejectedTasks,
                                  List<String> submissionFlow,
                                  List<String> executingThreads) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
            this.submittedTasks = submittedTasks;
            this.startedTasks = startedTasks;
            this.queuedPeak = queuedPeak;
            this.largestPoolSize = largestPoolSize;
            this.rejectedTasks = rejectedTasks;
            this.submissionFlow = submissionFlow;
            this.executingThreads = executingThreads;
        }

        public int corePoolSize() {
            return corePoolSize;
        }

        public int maxPoolSize() {
            return maxPoolSize;
        }

        public int queueCapacity() {
            return queueCapacity;
        }

        public int submittedTasks() {
            return submittedTasks;
        }

        public int startedTasks() {
            return startedTasks;
        }

        public int queuedPeak() {
            return queuedPeak;
        }

        public int largestPoolSize() {
            return largestPoolSize;
        }

        public int rejectedTasks() {
            return rejectedTasks;
        }

        public List<String> submissionFlow() {
            return submissionFlow;
        }

        public List<String> executingThreads() {
            return executingThreads;
        }
    }

    public static final class CallerRunsDemoResult {
        private final int callerRunsCount;
        private final List<String> executionThreads;

        public CallerRunsDemoResult(int callerRunsCount, List<String> executionThreads) {
            this.callerRunsCount = callerRunsCount;
            this.executionThreads = executionThreads;
        }

        public int callerRunsCount() {
            return callerRunsCount;
        }

        public List<String> executionThreads() {
            return executionThreads;
        }
    }

    public static final class ShutdownDemoResult {
        private final boolean shutdownCalled;
        private final boolean rejectedAfterShutdown;
        private final boolean terminatedGracefully;
        private final int neverStartedTasks;
        private final int interruptedTasks;
        private final boolean terminatedFinally;

        public ShutdownDemoResult(boolean shutdownCalled,
                                  boolean rejectedAfterShutdown,
                                  boolean terminatedGracefully,
                                  int neverStartedTasks,
                                  int interruptedTasks,
                                  boolean terminatedFinally) {
            this.shutdownCalled = shutdownCalled;
            this.rejectedAfterShutdown = rejectedAfterShutdown;
            this.terminatedGracefully = terminatedGracefully;
            this.neverStartedTasks = neverStartedTasks;
            this.interruptedTasks = interruptedTasks;
            this.terminatedFinally = terminatedFinally;
        }

        public boolean shutdownCalled() {
            return shutdownCalled;
        }

        public boolean rejectedAfterShutdown() {
            return rejectedAfterShutdown;
        }

        public boolean terminatedGracefully() {
            return terminatedGracefully;
        }

        public int neverStartedTasks() {
            return neverStartedTasks;
        }

        public int interruptedTasks() {
            return interruptedTasks;
        }

        public boolean terminatedFinally() {
            return terminatedFinally;
        }
    }
}
