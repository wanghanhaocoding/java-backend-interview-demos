package com.example.cpuhightroubleshootingdemo.cpu;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 模拟 AsyncJobCenter 失败后立刻重试，导致少量任务反复空转、持续占用 CPU。
 */
public class AsyncRetryStormCpuDemo {

    public static final String HOT_THREAD_NAME = "async-retry-dispatcher";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printInstruction();
            return;
        }

        switch (args[0]) {
            case "--preview" -> System.out.println(previewScenario());
            case "--run" -> runStorm(resolveSeconds(args));
            default -> printInstruction();
        }
    }

    public static RetryStormPreview previewScenario() {
        ArrayDeque<RetryJob> retryQueue = seedQueue();
        Map<String, Integer> jobAttempts = new LinkedHashMap<>();
        long checksum = 0L;
        int attempts = 0;

        while (attempts < 9_000) {
            RetryJob job = retryQueue.removeFirst();
            attempts++;
            jobAttempts.merge(job.jobId(), 1, Integer::sum);
            checksum += burnRetryCpu(job);
            retryQueue.addLast(job.immediateRetry());
        }

        int hottestJobAttempts = jobAttempts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new RetryStormPreview(HOT_THREAD_NAME, attempts, jobAttempts, hottestJobAttempts, checksum);
    }

    public static void runStorm(int seconds) throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(true);
        LongAdder attempts = new LongAdder();
        Map<String, LongAdder> jobAttempts = new LinkedHashMap<>();
        for (RetryJob job : seedQueue()) {
            jobAttempts.put(job.jobId(), new LongAdder());
        }

        Thread dispatcher = new Thread(() -> {
            ArrayDeque<RetryJob> retryQueue = seedQueue();
            long checksum = 0L;
            while (running.get()) {
                RetryJob job = retryQueue.removeFirst();
                attempts.increment();
                jobAttempts.get(job.jobId()).increment();
                checksum += burnRetryCpu(job);
                retryQueue.addLast(job.immediateRetry());
            }
            System.out.println("retry checksum=" + checksum);
        }, HOT_THREAD_NAME);
        dispatcher.setDaemon(true);
        dispatcher.start();

        long pid = ProcessHandle.current().pid();
        System.out.println("pid=" + pid + ", hotThread=" + HOT_THREAD_NAME);
        System.out.println("建议另开终端执行：top -Hp " + pid);
        System.out.println("观察同一个 jobId 的重试次数是否在几秒内暴涨。");

        for (int second = 1; second <= seconds; second++) {
            Thread.sleep(1_000L);
            System.out.printf("%s second=%d attempts=%d hottestJob=%s%n",
                    LocalDateTime.now(), second, attempts.sum(), hottestJob(jobAttempts));
        }

        running.set(false);
        dispatcher.join(1_000L);
    }

    private static int resolveSeconds(String[] args) {
        if (args.length < 2) {
            return 20;
        }
        return Math.max(5, Integer.parseInt(args[1]));
    }

    private static String hottestJob(Map<String, LongAdder> jobAttempts) {
        String hottestJob = null;
        long hottestCount = Long.MIN_VALUE;
        for (Map.Entry<String, LongAdder> entry : jobAttempts.entrySet()) {
            long current = entry.getValue().sum();
            if (current > hottestCount) {
                hottestJob = entry.getKey() + ':' + current;
                hottestCount = current;
            }
        }
        return hottestJob;
    }

    private static long burnRetryCpu(RetryJob job) {
        long value = job.jobId().hashCode() ^ job.retryRound();
        String payload = job.scene() + "|bank=timeout|jobId=" + job.jobId() + "|round=" + job.retryRound();
        for (int i = 0; i < payload.length(); i++) {
            value = Long.rotateLeft(value * 33 + payload.charAt(i), 7);
        }
        for (int i = 0; i < 64; i++) {
            value = Long.rotateLeft(value ^ (i * 17L), 3);
        }
        return value;
    }

    private static ArrayDeque<RetryJob> seedQueue() {
        return new ArrayDeque<>(List.of(
                new RetryJob("AJC-RETRY-1001", "receipt_make", 0),
                new RetryJob("AJC-RETRY-1002", "budget_analysis", 0),
                new RetryJob("AJC-RETRY-1003", "receipt_make", 0)
        ));
    }

    private static void printInstruction() {
        System.out.println("AsyncJobCenter CPU 重试风暴示例默认不执行。");
        System.out.println("预览模式：java -cp target/classes "
                + "com.example.cpuhightroubleshootingdemo.cpu.AsyncRetryStormCpuDemo --preview");
        System.out.println("真实复现：java -cp target/classes "
                + "com.example.cpuhightroubleshootingdemo.cpu.AsyncRetryStormCpuDemo --run 20");
    }

    private record RetryJob(String jobId, String scene, int retryRound) {

        private RetryJob immediateRetry() {
            return new RetryJob(jobId, scene, retryRound + 1);
        }
    }

    public record RetryStormPreview(
            String hotThreadName,
            int totalAttempts,
            Map<String, Integer> jobAttempts,
            int hottestJobAttempts,
            long checksum
    ) {
    }
}
