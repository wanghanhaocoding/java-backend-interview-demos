package com.example.cpuhightroubleshootingdemo.cpu;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 模拟 ScheduleCenter 在“本地队列为空但 scanner 线程仍然持续空扫 bucket”时的热点线程。
 */
public class BusySpinScheduleScannerDemo {

    public static final String HOT_THREAD_NAME = "schedule-scanner-busy-spin";
    private static final List<String> BUCKETS = buildBuckets();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printInstruction();
            return;
        }

        switch (args[0]) {
            case "--preview" -> System.out.println(previewScenario());
            case "--run" -> runHotLoop(resolveSeconds(args));
            default -> printInstruction();
        }
    }

    public static ScanPreview previewScenario() {
        long checksum = 0L;
        long emptyScanIterations = 0L;
        long payloadChecks = 0L;
        long startedAt = System.nanoTime();

        for (int round = 0; round < 180; round++) {
            for (String bucket : BUCKETS) {
                emptyScanIterations++;
                checksum += mix(bucket.hashCode() ^ round);
                if ((emptyScanIterations & 7) == 0) {
                    payloadChecks++;
                    checksum ^= decodeBudgetPayload(bucket, round);
                }
            }
        }

        long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedAt);
        return new ScanPreview(HOT_THREAD_NAME, emptyScanIterations, BUCKETS.size(), payloadChecks, elapsedMicros, checksum);
    }

    public static void runHotLoop(int seconds) throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(true);
        LongAdder scans = new LongAdder();
        LongAdder payloadChecks = new LongAdder();

        Thread scannerThread = new Thread(() -> {
            long checksum = 0L;
            while (running.get()) {
                for (String bucket : BUCKETS) {
                    scans.increment();
                    checksum += mix(bucket.hashCode() ^ (int) scans.sum());
                    if ((checksum & 15) == 0) {
                        payloadChecks.increment();
                        checksum ^= decodeBudgetPayload(bucket, (int) (checksum & 127));
                    }
                }
            }
            System.out.println("scanner checksum=" + checksum);
        }, HOT_THREAD_NAME);
        scannerThread.setDaemon(true);
        scannerThread.start();

        long pid = ProcessHandle.current().pid();
        System.out.println("pid=" + pid + ", hotThread=" + HOT_THREAD_NAME);
        System.out.println("建议另开终端执行：top -Hp " + pid);
        System.out.println("然后把热点 tid 转成 16 进制，再用 jstack 定位到 Java 栈。");

        for (int second = 1; second <= seconds; second++) {
            Thread.sleep(1_000L);
            System.out.printf("%s second=%d scans=%d payloadChecks=%d%n",
                    LocalDateTime.now(), second, scans.sum(), payloadChecks.sum());
        }

        running.set(false);
        scannerThread.join(1_000L);
    }

    private static int resolveSeconds(String[] args) {
        if (args.length < 2) {
            return 20;
        }
        return Math.max(5, Integer.parseInt(args[1]));
    }

    private static long decodeBudgetPayload(String bucket, int round) {
        long checksum = round;
        String payload = bucket + "|tenant=treasury-a|scene=plan_collect|round=" + round;
        for (int i = 0; i < payload.length(); i++) {
            checksum = Long.rotateLeft(checksum * 131 + payload.charAt(i), 5);
        }
        return checksum;
    }

    private static long mix(int seed) {
        long value = seed;
        for (int i = 0; i < 32; i++) {
            value = Long.rotateLeft(value * 31 + i, 3);
        }
        return value;
    }

    private static List<String> buildBuckets() {
        List<String> buckets = new ArrayList<>();
        for (int minute = 0; minute < 12; minute++) {
            for (int shard = 0; shard < 8; shard++) {
                buckets.add("2026-03-30 09:" + String.format("%02d", minute) + "_" + shard);
            }
        }
        return List.copyOf(buckets);
    }

    private static void printInstruction() {
        System.out.println("ScheduleCenter CPU 热点线程示例默认不执行。");
        System.out.println("预览模式：java -cp target/classes "
                + "com.example.cpuhightroubleshootingdemo.cpu.BusySpinScheduleScannerDemo --preview");
        System.out.println("真实复现：java -cp target/classes "
                + "com.example.cpuhightroubleshootingdemo.cpu.BusySpinScheduleScannerDemo --run 20");
    }

    public record ScanPreview(
            String hotThreadName,
            long emptyScanIterations,
            int bucketCount,
            long payloadChecks,
            long elapsedMicros,
            long checksum
    ) {
    }
}
