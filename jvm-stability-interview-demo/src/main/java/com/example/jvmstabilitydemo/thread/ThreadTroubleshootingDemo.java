package com.example.jvmstabilitydemo.thread;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 用来演示“线程出问题后，怎么从线程 dump 反查到代码”。
 *
 * <p>这个示例不会默认执行。真正运行时，会启动几条有明确线程名和方法栈的线程，
 * 让你可以用 jps / jstack / jcmd 在本地直接练习定位。</p>
 */
public class ThreadTroubleshootingDemo {

    private final Object receiptStateMonitor = new Object();
    private final BlockingQueue<String> callbackQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        ThreadTroubleshootingDemo demo = new ThreadTroubleshootingDemo();
        if (args.length == 0 || !"--run".equals(args[0])) {
            printInstruction();
            return;
        }
        demo.runForManualDiagnosis(TimeUnit.MINUTES.toMillis(3));
    }

    public ThreadTroubleshootingSnapshot previewSnapshot() throws Exception {
        ScenarioHandle handle = startScenario(1_500);
        try {
            return captureSnapshot(handle);
        } finally {
            stopScenario(handle);
        }
    }

    public void runForManualDiagnosis(long holdMillis) throws Exception {
        ScenarioHandle handle = startScenario(holdMillis);
        try {
            ThreadTroubleshootingSnapshot snapshot = captureSnapshot(handle);
            printManualDiagnosisGuide(snapshot, holdMillis);
            sleepSilently(holdMillis);
        } finally {
            stopScenario(handle);
        }
    }

    private ScenarioHandle startScenario(long holdMillis) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(holdMillis);
        CountDownLatch started = new CountDownLatch(5);
        CountDownLatch lockHolderReady = new CountDownLatch(1);

        Thread busyThread = new Thread(
                () -> callbackRetryBusyLoop(started, deadlineNanos),
                "receipt-callback-busy-thread"
        );
        Thread lockHolderThread = new Thread(
                () -> holdReceiptStateMonitor(started, lockHolderReady, deadlineNanos),
                "receipt-lock-holder-thread"
        );
        Thread blockedThread = new Thread(
                () -> rebuildReceiptProjectionAfterLock(started, lockHolderReady),
                "receipt-lock-blocked-thread"
        );
        Thread waitingThread = new Thread(
                () -> waitForNextCallbackBatch(started),
                "callback-queue-waiting-thread"
        );
        Thread sleepingThread = new Thread(
                () -> sleepBetweenScheduleRounds(started, deadlineNanos),
                "schedule-poller-sleeping-thread"
        );

        busyThread.start();
        lockHolderThread.start();
        blockedThread.start();
        waitingThread.start();
        sleepingThread.start();

        started.await(2, TimeUnit.SECONDS);
        awaitExpectedState(blockedThread, Thread.State.BLOCKED, 1_000);
        awaitStateSet(waitingThread, Arrays.asList(Thread.State.WAITING, Thread.State.TIMED_WAITING), 1_000);
        awaitExpectedState(sleepingThread, Thread.State.TIMED_WAITING, 1_000);

        return new ScenarioHandle(busyThread, lockHolderThread, blockedThread, waitingThread, sleepingThread);
    }

    private ThreadTroubleshootingSnapshot captureSnapshot(ScenarioHandle handle) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long processId = currentProcessId();
        long[] threadIds = {
                handle.busyThread().getId(),
                handle.lockHolderThread().getId(),
                handle.blockedThread().getId(),
                handle.waitingThread().getId(),
                handle.sleepingThread().getId()
        };

        ThreadInfo[] infos = threadMXBean.getThreadInfo(threadIds, 8);
        List<ThreadSignal> signals = new ArrayList<>();
        for (ThreadInfo info : infos) {
            if (info == null) {
                continue;
            }
            List<String> stackPreview = new ArrayList<>();
            for (StackTraceElement stackTraceElement : info.getStackTrace()) {
                stackPreview.add(stackTraceElement.toString());
            }
            signals.add(new ThreadSignal(info.getThreadName(), info.getThreadState(), stackPreview));
        }
        return new ThreadTroubleshootingSnapshot(processId, signals);
    }

    private void stopScenario(ScenarioHandle handle) throws InterruptedException {
        running = false;
        callbackQueue.offer("stop");
        handle.waitingThread().interrupt();
        handle.sleepingThread().interrupt();
        handle.busyThread().interrupt();

        handle.busyThread().join(2_000);
        handle.lockHolderThread().join(2_000);
        handle.blockedThread().join(2_000);
        handle.waitingThread().join(2_000);
        handle.sleepingThread().join(2_000);
    }

    private void callbackRetryBusyLoop(CountDownLatch started, long deadlineNanos) {
        started.countDown();
        while (running && System.nanoTime() < deadlineNanos) {
            scanRetryWindow();
        }
    }

    private void scanRetryWindow() {
        for (int i = 0; i < 5_000; i++) {
            decodeReceiptPayload(i);
        }
    }

    private int decodeReceiptPayload(int seed) {
        return busySpinChecksum(seed);
    }

    private int busySpinChecksum(int seed) {
        int checksum = seed;
        for (int i = 0; i < 500; i++) {
            checksum = checksum * 31 + i;
        }
        return checksum;
    }

    private void holdReceiptStateMonitor(CountDownLatch started,
                                         CountDownLatch lockHolderReady,
                                         long deadlineNanos) {
        synchronized (receiptStateMonitor) {
            lockHolderReady.countDown();
            started.countDown();
            while (running && System.nanoTime() < deadlineNanos) {
                sleepSilently(200);
            }
        }
    }

    private void rebuildReceiptProjectionAfterLock(CountDownLatch started, CountDownLatch lockHolderReady) {
        try {
            lockHolderReady.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }

        started.countDown();
        synchronized (receiptStateMonitor) {
            reloadReceiptProjection();
        }
    }

    private void reloadReceiptProjection() {
        sleepSilently(50);
    }

    private void waitForNextCallbackBatch(CountDownLatch started) {
        started.countDown();
        try {
            callbackQueue.take();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepBetweenScheduleRounds(CountDownLatch started, long deadlineNanos) {
        started.countDown();
        while (running && System.nanoTime() < deadlineNanos) {
            sleepSilently(1_000);
        }
    }

    private void awaitExpectedState(Thread thread, Thread.State expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) {
                return;
            }
            Thread.sleep(20);
        }
    }

    private void awaitStateSet(Thread thread, List<Thread.State> expectedStates, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (expectedStates.contains(thread.getState())) {
                return;
            }
            Thread.sleep(20);
        }
    }

    private void printManualDiagnosisGuide(ThreadTroubleshootingSnapshot snapshot, long holdMillis) {
        System.out.println("=== Thread Troubleshooting Demo ===");
        System.out.println("现在已经构造出几条典型问题线程，方便你用线程 dump 反查代码。");
        System.out.println("pid = " + snapshot.processId());
        System.out.println("诊断窗口(ms) = " + holdMillis);
        System.out.println();
        System.out.println("建议命令：");
        System.out.println("1. jps -l");
        System.out.println("2. jcmd " + snapshot.processId() + " Thread.print");
        System.out.println("3. jstack " + snapshot.processId());
        System.out.println();
        System.out.println("重点线程与代码入口：");
        System.out.println("- receipt-callback-busy-thread -> callbackRetryBusyLoop -> scanRetryWindow -> decodeReceiptPayload");
        System.out.println("- receipt-lock-holder-thread -> holdReceiptStateMonitor");
        System.out.println("- receipt-lock-blocked-thread -> rebuildReceiptProjectionAfterLock");
        System.out.println("- callback-queue-waiting-thread -> waitForNextCallbackBatch");
        System.out.println("- schedule-poller-sleeping-thread -> sleepBetweenScheduleRounds");
        System.out.println();

        for (ThreadSignal signal : snapshot.threads()) {
            System.out.println(LocalDateTime.now() + " thread=" + signal.threadName() + " state=" + signal.state());
            if (!signal.stackPreview().isEmpty()) {
                System.out.println("  topFrame=" + signal.stackPreview().get(0));
            }
        }
        System.out.println();
        System.out.println("诊断窗口结束后进程会自动退出，也可以手动 Ctrl+C 结束。");
    }

    private static void printInstruction() {
        System.out.println("线程定位教学示例不会默认执行。建议先编译：");
        System.out.println("mvn -q -DskipTests compile");
        System.out.println();
        System.out.println("再执行：");
        System.out.println("java -cp target/classes com.example.jvmstabilitydemo.thread.ThreadTroubleshootingDemo --run");
        System.out.println();
        System.out.println("然后另开一个终端，使用 jps / jcmd / jstack 对照线程名和方法栈定位代码。");
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private long currentProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separatorIndex = runtimeName.indexOf('@');
        if (separatorIndex <= 0) {
            return -1L;
        }
        try {
            return Long.parseLong(runtimeName.substring(0, separatorIndex));
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    public static final class ThreadTroubleshootingSnapshot {

        private final long processId;
        private final List<ThreadSignal> threads;

        public ThreadTroubleshootingSnapshot(long processId, List<ThreadSignal> threads) {
            this.processId = processId;
            this.threads = threads;
        }

        public long processId() {
            return processId;
        }

        public List<ThreadSignal> threads() {
            return threads;
        }
    }

    public static final class ThreadSignal {

        private final String threadName;
        private final Thread.State state;
        private final List<String> stackPreview;

        public ThreadSignal(String threadName, Thread.State state, List<String> stackPreview) {
            this.threadName = threadName;
            this.state = state;
            this.stackPreview = stackPreview;
        }

        public String threadName() {
            return threadName;
        }

        public Thread.State state() {
            return state;
        }

        public List<String> stackPreview() {
            return stackPreview;
        }
    }

    private static final class ScenarioHandle {

        private final Thread busyThread;
        private final Thread lockHolderThread;
        private final Thread blockedThread;
        private final Thread waitingThread;
        private final Thread sleepingThread;

        private ScenarioHandle(Thread busyThread,
                               Thread lockHolderThread,
                               Thread blockedThread,
                               Thread waitingThread,
                               Thread sleepingThread) {
            this.busyThread = busyThread;
            this.lockHolderThread = lockHolderThread;
            this.blockedThread = blockedThread;
            this.waitingThread = waitingThread;
            this.sleepingThread = sleepingThread;
        }

        public Thread busyThread() {
            return busyThread;
        }

        public Thread lockHolderThread() {
            return lockHolderThread;
        }

        public Thread blockedThread() {
            return blockedThread;
        }

        public Thread waitingThread() {
            return waitingThread;
        }

        public Thread sleepingThread() {
            return sleepingThread;
        }
    }
}
