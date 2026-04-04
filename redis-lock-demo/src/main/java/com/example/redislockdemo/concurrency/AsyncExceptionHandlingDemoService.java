package com.example.redislockdemo.concurrency;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AsyncExceptionHandlingDemoService {

    public ChildTaskIsolationDemoResult childTaskIsolationDemo() {
        AtomicInteger threadIndex = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(3, namedThreadFactory("async-child-", threadIndex));
        try {
            List<CompletableFuture<ChildTaskOutcome>> futures = Arrays.asList(
                    submitWithBoundaryCatch(executor, "load-ledger-snapshot", false),
                    submitWithBoundaryCatch(executor, "push-bank-callback", true),
                    submitWithBoundaryCatch(executor, "write-audit-log", false)
            );

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ChildTaskOutcome> outcomes = new ArrayList<ChildTaskOutcome>();
            int successCount = 0;
            int failureCount = 0;
            for (CompletableFuture<ChildTaskOutcome> future : futures) {
                ChildTaskOutcome outcome = future.join();
                outcomes.add(outcome);
                if (outcome.success()) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }

            List<String> boundaryNotes = new ArrayList<String>();
            boundaryNotes.add("子任务边界要 try/catch，补齐 taskName、requestNo、线程名等上下文，并释放本线程资源。");
            boundaryNotes.add("父线程边界要统一 join/get 收敛所有子任务结果，再决定重试、降级还是补偿。");
            boundaryNotes.add("不要只在父线程外层写 try/catch，因为异常已经异步飞到子线程里了。");

            return new ChildTaskIsolationDemoResult(
                    Thread.currentThread().getName(),
                    true,
                    successCount,
                    failureCount,
                    immutableListCopy(outcomes),
                    immutableListCopy(boundaryNotes)
            );
        } finally {
            shutdownGracefully(executor);
        }
    }

    public FutureGetCaptureDemoResult futureGetCaptureDemo() {
        AtomicInteger threadIndex = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory("future-child-", threadIndex));
        try {
            Future<String> future = executor.submit(() -> {
                throw new IllegalStateException("bank callback worker crashed");
            });
            try {
                future.get(3, TimeUnit.SECONDS);
                return new FutureGetCaptureDemoResult(false, null, null, "unexpected-success");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                return new FutureGetCaptureDemoResult(true, cause.getClass().getSimpleName(), cause.getMessage(), "Future.get");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return new FutureGetCaptureDemoResult(true, ex.getClass().getSimpleName(), ex.getMessage(), "Future.get-interrupted");
            } catch (TimeoutException ex) {
                return new FutureGetCaptureDemoResult(false, ex.getClass().getSimpleName(), ex.getMessage(), "Future.get-timeout");
            }
        } finally {
            shutdownGracefully(executor);
        }
    }

    public UncaughtExceptionHandlerDemoResult uncaughtExceptionHandlerDemo() throws InterruptedException {
        AtomicReference<Throwable> captured = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            throw new IllegalArgumentException("fire-and-forget child crashed");
        }, "raw-child-1");
        worker.setUncaughtExceptionHandler((thread, throwable) -> captured.set(throwable));
        worker.start();
        worker.join(3000);

        Throwable throwable = captured.get();
        return new UncaughtExceptionHandlerDemoResult(
                throwable != null,
                worker.getName(),
                throwable == null ? null : throwable.getClass().getSimpleName(),
                throwable == null ? null : throwable.getMessage()
        );
    }

    private CompletableFuture<ChildTaskOutcome> submitWithBoundaryCatch(ExecutorService executor,
                                                                        String taskName,
                                                                        boolean shouldFail) {
        return CompletableFuture.supplyAsync(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                simulateBusiness(taskName, shouldFail);
                return ChildTaskOutcome.success(taskName, threadName, "done");
            } catch (Throwable throwable) {
                return ChildTaskOutcome.failure(taskName, threadName, throwable.getClass().getSimpleName(), throwable.getMessage());
            }
        }, executor);
    }

    private void simulateBusiness(String taskName, boolean shouldFail) throws InterruptedException {
        Thread.sleep(60);
        if (shouldFail) {
            throw new IllegalStateException(taskName + " failed");
        }
    }

    private void shutdownGracefully(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private ThreadFactory namedThreadFactory(String prefix, AtomicInteger threadIndex) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + threadIndex.incrementAndGet());
            return thread;
        };
    }

    private <T> List<T> immutableListCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public static final class ChildTaskIsolationDemoResult {
        private final String parentThreadName;
        private final boolean parentContinued;
        private final int successCount;
        private final int failureCount;
        private final List<ChildTaskOutcome> outcomes;
        private final List<String> boundaryNotes;

        public ChildTaskIsolationDemoResult(String parentThreadName,
                                            boolean parentContinued,
                                            int successCount,
                                            int failureCount,
                                            List<ChildTaskOutcome> outcomes,
                                            List<String> boundaryNotes) {
            this.parentThreadName = parentThreadName;
            this.parentContinued = parentContinued;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.outcomes = outcomes;
            this.boundaryNotes = boundaryNotes;
        }

        public String parentThreadName() {
            return parentThreadName;
        }

        public boolean parentContinued() {
            return parentContinued;
        }

        public int successCount() {
            return successCount;
        }

        public int failureCount() {
            return failureCount;
        }

        public List<ChildTaskOutcome> outcomes() {
            return outcomes;
        }

        public List<String> boundaryNotes() {
            return boundaryNotes;
        }
    }

    public static final class ChildTaskOutcome {
        private final String taskName;
        private final String threadName;
        private final boolean success;
        private final String result;
        private final String errorType;
        private final String errorMessage;

        private ChildTaskOutcome(String taskName,
                                 String threadName,
                                 boolean success,
                                 String result,
                                 String errorType,
                                 String errorMessage) {
            this.taskName = taskName;
            this.threadName = threadName;
            this.success = success;
            this.result = result;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        public static ChildTaskOutcome success(String taskName, String threadName, String result) {
            return new ChildTaskOutcome(taskName, threadName, true, result, null, null);
        }

        public static ChildTaskOutcome failure(String taskName,
                                               String threadName,
                                               String errorType,
                                               String errorMessage) {
            return new ChildTaskOutcome(taskName, threadName, false, null, errorType, errorMessage);
        }

        public String taskName() {
            return taskName;
        }

        public String threadName() {
            return threadName;
        }

        public boolean success() {
            return success;
        }

        public String result() {
            return result;
        }

        public String errorType() {
            return errorType;
        }

        public String errorMessage() {
            return errorMessage;
        }
    }

    public static final class FutureGetCaptureDemoResult {
        private final boolean captured;
        private final String errorType;
        private final String errorMessage;
        private final String captureApi;

        public FutureGetCaptureDemoResult(boolean captured,
                                          String errorType,
                                          String errorMessage,
                                          String captureApi) {
            this.captured = captured;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.captureApi = captureApi;
        }

        public boolean captured() {
            return captured;
        }

        public String errorType() {
            return errorType;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public String captureApi() {
            return captureApi;
        }
    }

    public static final class UncaughtExceptionHandlerDemoResult {
        private final boolean captured;
        private final String threadName;
        private final String errorType;
        private final String errorMessage;

        public UncaughtExceptionHandlerDemoResult(boolean captured,
                                                  String threadName,
                                                  String errorType,
                                                  String errorMessage) {
            this.captured = captured;
            this.threadName = threadName;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        public boolean captured() {
            return captured;
        }

        public String threadName() {
            return threadName;
        }

        public String errorType() {
            return errorType;
        }

        public String errorMessage() {
            return errorMessage;
        }
    }
}
