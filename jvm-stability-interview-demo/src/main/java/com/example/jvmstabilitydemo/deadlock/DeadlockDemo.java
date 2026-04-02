package com.example.jvmstabilitydemo.deadlock;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 演示 xtimer 执行回调线程与停用定时器线程因为锁顺序相反而形成死锁。
 */
public class DeadlockDemo {

    private final Lock timerRowLock = new ReentrantLock();
    private final Lock taskRowLock = new ReentrantLock();

    public static void main(String[] args) throws Exception {
        int holdSeconds = parseHoldSeconds(args);
        DeadlockDemo demo = new DeadlockDemo();
        demo.runDemo();
        if (holdSeconds > 0) {
            System.out.println("holding process for " + holdSeconds
                + " seconds so jstack/jcmd can attach");
            TimeUnit.SECONDS.sleep(holdSeconds);
        }
    }

    public void runDemo() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);

        Thread executorWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                lockTaskThenTimer(ready);
            }
        }, "xtimer-executor-worker");
        Thread disableTimerWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                lockTimerThenTask(ready);
            }
        }, "xtimer-disable-timer-worker");
        executorWorker.setDaemon(true);
        disableTimerWorker.setDaemon(true);

        executorWorker.start();
        disableTimerWorker.start();

        ready.await(2, TimeUnit.SECONDS);
        Thread.sleep(300);

        long[] deadlockedThreads = detectDeadlockedThreads();
        System.out.println(LocalDateTime.now() + " detected deadlocked thread count="
            + (deadlockedThreads == null ? 0 : deadlockedThreads.length));

        if (deadlockedThreads != null) {
            printThreadInfo(deadlockedThreads);
        } else {
            System.out.println("当前机器上没有检测到死锁，请重试。\n");
        }
    }

    public long[] detectDeadlockedThreads() {
        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        return mxBean.findDeadlockedThreads();
    }

    private void lockTaskThenTimer(CountDownLatch ready) {
        taskRowLock.lock();
        try {
            ready.countDown();
            sleepSilently(200);
            timerRowLock.lock();
            try {
                System.out.println("executor worker acquired timer_task and xtimer locks");
            } finally {
                timerRowLock.unlock();
            }
        } finally {
            taskRowLock.unlock();
        }
    }

    private void lockTimerThenTask(CountDownLatch ready) {
        timerRowLock.lock();
        try {
            ready.countDown();
            sleepSilently(200);
            taskRowLock.lock();
            try {
                System.out.println("disable timer worker acquired xtimer and timer_task locks");
            } finally {
                taskRowLock.unlock();
            }
        } finally {
            timerRowLock.unlock();
        }
    }

    private void printThreadInfo(long[] threadIds) {
        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = mxBean.getThreadInfo(threadIds, true, true);
        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo == null) {
                continue;
            }

            System.out.printf("deadlock thread=%s state=%s waitingLock=%s owner=%s%n",
                threadInfo.getThreadName(),
                threadInfo.getThreadState(),
                threadInfo.getLockName(),
                threadInfo.getLockOwnerName());
        }
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int parseHoldSeconds(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--hold-seconds=")) {
                String value = arg.substring("--hold-seconds=".length());
                try {
                    int holdSeconds = Integer.parseInt(value);
                    if (holdSeconds < 0) {
                        throw new IllegalArgumentException("hold-seconds must be >= 0");
                    }
                    return holdSeconds;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("hold-seconds must be an integer", e);
                }
            }
        }
        return 0;
    }
}
