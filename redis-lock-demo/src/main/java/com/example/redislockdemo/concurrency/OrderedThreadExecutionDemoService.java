package com.example.redislockdemo.concurrency;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class OrderedThreadExecutionDemoService {

    private static final List<String> EXPECTED_ORDER = Collections.unmodifiableList(Arrays.asList("T1", "T2", "T3"));

    public OrderedExecutionDemoResult joinChain() throws InterruptedException {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        Thread t1 = scenarioThread("T1", failures, () -> recordStep("T1", executionOrder));
        Thread t2 = scenarioThread("T2", failures, () -> recordStep("T2", executionOrder));
        Thread t3 = scenarioThread("T3", failures, () -> recordStep("T3", executionOrder));

        t1.start();
        t1.join();
        t2.start();
        t2.join();
        t3.start();
        t3.join();

        ensureNoFailures(failures, "join");
        return OrderedExecutionDemoResult.of(
                "join",
                executionOrder,
                "由外层主线程串联控制，前一个线程结束后再启动下一个线程"
        );
    }

    public OrderedExecutionDemoResult countDownLatchChain() throws InterruptedException {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        CountDownLatch t1Done = new CountDownLatch(1);
        CountDownLatch t2Done = new CountDownLatch(1);

        Thread t1 = coordinatedThread("T1", ready, start, done, failures, () -> {
            recordStep("T1", executionOrder);
            t1Done.countDown();
        });
        Thread t2 = coordinatedThread("T2", ready, start, done, failures, () -> {
            awaitLatch(t1Done, "T2 等待 T1");
            recordStep("T2", executionOrder);
            t2Done.countDown();
        });
        Thread t3 = coordinatedThread("T3", ready, start, done, failures, () -> {
            awaitLatch(t2Done, "T3 等待 T2");
            recordStep("T3", executionOrder);
        });

        runCoordinatedScenario("CountDownLatch", ready, start, done, failures, t3, t2, t1);
        return OrderedExecutionDemoResult.of(
                "CountDownLatch",
                executionOrder,
                "给后续线程一个完成信号，适合把执行阶段拆成前后依赖"
        );
    }

    public OrderedExecutionDemoResult semaphoreChain() throws InterruptedException {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        Semaphore t2Permit = new Semaphore(0);
        Semaphore t3Permit = new Semaphore(0);

        Thread t1 = coordinatedThread("T1", ready, start, done, failures, () -> {
            recordStep("T1", executionOrder);
            t2Permit.release();
        });
        Thread t2 = coordinatedThread("T2", ready, start, done, failures, () -> {
            acquirePermit(t2Permit, "T2 等待 T1 的 permit");
            recordStep("T2", executionOrder);
            t3Permit.release();
        });
        Thread t3 = coordinatedThread("T3", ready, start, done, failures, () -> {
            acquirePermit(t3Permit, "T3 等待 T2 的 permit");
            recordStep("T3", executionOrder);
        });

        runCoordinatedScenario("Semaphore", ready, start, done, failures, t3, t2, t1);
        return OrderedExecutionDemoResult.of(
                "Semaphore",
                executionOrder,
                "把执行资格当成 permit 往后传，谁拿到 permit 谁继续执行"
        );
    }

    public OrderedExecutionDemoResult conditionChain() throws InterruptedException {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        ReentrantLock lock = new ReentrantLock();
        Condition phaseChanged = lock.newCondition();
        int[] nextStep = {1};

        Thread t1 = coordinatedThread("T1", ready, start, done, failures, () -> {
            lock.lock();
            try {
                recordStep("T1", executionOrder);
                nextStep[0] = 2;
                phaseChanged.signalAll();
            } finally {
                lock.unlock();
            }
        });
        Thread t2 = coordinatedThread("T2", ready, start, done, failures, () -> {
            lock.lock();
            try {
                while (nextStep[0] != 2) {
                    if (!phaseChanged.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("T2 等待 T1 超时");
                    }
                }
                recordStep("T2", executionOrder);
                nextStep[0] = 3;
                phaseChanged.signalAll();
            } finally {
                lock.unlock();
            }
        });
        Thread t3 = coordinatedThread("T3", ready, start, done, failures, () -> {
            lock.lock();
            try {
                while (nextStep[0] != 3) {
                    if (!phaseChanged.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("T3 等待 T2 超时");
                    }
                }
                recordStep("T3", executionOrder);
            } finally {
                lock.unlock();
            }
        });

        runCoordinatedScenario("ReentrantLock + Condition", ready, start, done, failures, t3, t2, t1);
        return OrderedExecutionDemoResult.of(
                "ReentrantLock + Condition",
                executionOrder,
                "更贴近底层条件队列写法，适合多阶段状态流转"
        );
    }

    private Thread scenarioThread(String threadName, List<Throwable> failures, ThrowingRunnable action) {
        return new Thread(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                synchronized (failures) {
                    failures.add(throwable);
                }
            }
        }, threadName);
    }

    private Thread coordinatedThread(String threadName,
                                     CountDownLatch ready,
                                     CountDownLatch start,
                                     CountDownLatch done,
                                     List<Throwable> failures,
                                     ThrowingRunnable action) {
        return new Thread(() -> {
            ready.countDown();
            try {
                if (!start.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(threadName + " 等待统一放行超时");
                }
                action.run();
            } catch (Throwable throwable) {
                synchronized (failures) {
                    failures.add(throwable);
                }
            } finally {
                done.countDown();
            }
        }, threadName);
    }

    private void runCoordinatedScenario(String strategy,
                                        CountDownLatch ready,
                                        CountDownLatch start,
                                        CountDownLatch done,
                                        List<Throwable> failures,
                                        Thread... threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.start();
        }

        if (!ready.await(3, TimeUnit.SECONDS)) {
            throw new IllegalStateException(strategy + " demo threads were not ready in time");
        }
        // 故意按 T3、T2、T1 的顺序启动，强调“最终顺序靠协调原语，而不是靠 start 顺序”。
        start.countDown();
        if (!done.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(strategy + " demo threads did not finish in time");
        }
        ensureNoFailures(failures, strategy);
    }

    private void awaitLatch(CountDownLatch latch, String step) throws InterruptedException {
        if (!latch.await(3, TimeUnit.SECONDS)) {
            throw new IllegalStateException(step + " 超时");
        }
    }

    private void acquirePermit(Semaphore semaphore, String step) throws InterruptedException {
        if (!semaphore.tryAcquire(3, TimeUnit.SECONDS)) {
            throw new IllegalStateException(step + " 超时");
        }
    }

    private void ensureNoFailures(List<Throwable> failures, String strategy) {
        if (!failures.isEmpty()) {
            throw new IllegalStateException(strategy + " demo execution failed", failures.get(0));
        }
    }

    private void recordStep(String step, List<String> executionOrder) {
        executionOrder.add(step);
    }

    public static final class OrderedExecutionDemoResult {
        private final String strategy;
        private final List<String> executionOrder;
        private final boolean ordered;
        private final String note;

        public OrderedExecutionDemoResult(String strategy, List<String> executionOrder, boolean ordered, String note) {
            this.strategy = strategy;
            this.executionOrder = executionOrder;
            this.ordered = ordered;
            this.note = note;
        }

        static OrderedExecutionDemoResult of(String strategy, List<String> executionOrder, String note) {
            List<String> snapshot = Collections.unmodifiableList(new ArrayList<String>(executionOrder));
            return new OrderedExecutionDemoResult(strategy, snapshot, EXPECTED_ORDER.equals(snapshot), note);
        }

        public String strategy() {
            return strategy;
        }

        public List<String> executionOrder() {
            return executionOrder;
        }

        public boolean ordered() {
            return ordered;
        }

        public String note() {
            return note;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
