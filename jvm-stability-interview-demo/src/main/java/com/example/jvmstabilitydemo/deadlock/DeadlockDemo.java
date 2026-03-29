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
 * 演示两条线程因为锁顺序相反而形成死锁。
 */
public class DeadlockDemo {

    private final Lock taskLock = new ReentrantLock();
    private final Lock receiptLock = new ReentrantLock();

    public static void main(String[] args) throws Exception {
        DeadlockDemo demo = new DeadlockDemo();
        demo.runDemo();
    }

    public void runDemo() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);

        Thread taskWorker = new Thread(() -> lockTaskThenReceipt(ready), "task-worker-thread");
        Thread compensationWorker = new Thread(() -> lockReceiptThenTask(ready), "compensation-worker-thread");
        taskWorker.setDaemon(true);
        compensationWorker.setDaemon(true);

        taskWorker.start();
        compensationWorker.start();

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

    private void lockTaskThenReceipt(CountDownLatch ready) {
        taskLock.lock();
        try {
            ready.countDown();
            sleepSilently(200);
            receiptLock.lock();
            try {
                System.out.println("task worker acquired both locks");
            } finally {
                receiptLock.unlock();
            }
        } finally {
            taskLock.unlock();
        }
    }

    private void lockReceiptThenTask(CountDownLatch ready) {
        receiptLock.lock();
        try {
            ready.countDown();
            sleepSilently(200);
            taskLock.lock();
            try {
                System.out.println("compensation worker acquired both locks");
            } finally {
                taskLock.unlock();
            }
        } finally {
            receiptLock.unlock();
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
}
