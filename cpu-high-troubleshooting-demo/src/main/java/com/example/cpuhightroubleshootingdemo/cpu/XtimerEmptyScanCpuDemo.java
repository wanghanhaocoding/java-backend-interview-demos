package com.example.cpuhightroubleshootingdemo.cpu;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 模拟 xtimer 里 TriggerWorker / TriggerTimerTask 对空 minuteBucketKey 持续做 rangeByScore 检查，
 * 导致 Timer 线程在没有真实回调量的情况下仍然把 CPU 打高。
 */
public class XtimerEmptyScanCpuDemo {

    public static final String HOT_THREAD_NAME = "xtimer-trigger-empty-scan";
    private static final List<String> MINUTE_BUCKET_KEYS = buildMinuteBucketKeys();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printInstruction();
            return;
        }

        String command = args[0];
        if ("--preview".equals(command)) {
            System.out.println(previewScenario());
        } else if ("--run".equals(command)) {
            runHotLoop(resolveSeconds(args));
        } else {
            printInstruction();
        }
    }

    public static ScanPreview previewScenario() {
        long checksum = 0L;
        long emptyScanCalls = 0L;
        long timerTaskWakeUps = 0L;
        long fallbackChecks = 0L;
        long startedAt = System.nanoTime();

        for (int round = 0; round < 2400; round++) {
            for (String minuteBucketKey : MINUTE_BUCKET_KEYS) {
                timerTaskWakeUps++;
                emptyScanCalls++;
                checksum += scanMinuteBucket(minuteBucketKey, round);
                if ((emptyScanCalls & 7) == 0) {
                    fallbackChecks++;
                    checksum ^= simulateFallbackGuard(minuteBucketKey, round);
                }
            }
        }

        long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedAt);
        return new ScanPreview(HOT_THREAD_NAME, MINUTE_BUCKET_KEYS.size(), emptyScanCalls,
                timerTaskWakeUps, fallbackChecks, elapsedMicros, checksum);
    }

    public static void runHotLoop(int seconds) throws InterruptedException {
        final AtomicBoolean running = new AtomicBoolean(true);
        final LongAdder emptyScans = new LongAdder();
        final LongAdder wakeUps = new LongAdder();
        final LongAdder fallbackChecks = new LongAdder();

        Thread timerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long checksum = 0L;
                int round = 0;
                while (running.get()) {
                    for (String minuteBucketKey : MINUTE_BUCKET_KEYS) {
                        wakeUps.increment();
                        emptyScans.increment();
                        checksum += scanMinuteBucket(minuteBucketKey, round);
                        if ((emptyScans.sum() & 7) == 0) {
                            fallbackChecks.increment();
                            checksum ^= simulateFallbackGuard(minuteBucketKey, round);
                        }
                    }
                    round++;
                }
                System.out.println("xtimer empty-scan checksum=" + checksum);
            }
        }, HOT_THREAD_NAME);
        timerThread.setDaemon(true);
        timerThread.start();

        long pid = resolvePid();
        System.out.println("pid=" + pid + ", hotThread=" + HOT_THREAD_NAME);
        System.out.println("建议另开终端执行：top -Hp " + pid);
        System.out.println("重点观察 TriggerWorker / TriggerTimerTask 对空 minuteBucketKey 的热点线程。");

        for (int second = 1; second <= seconds; second++) {
            Thread.sleep(1000L);
            System.out.printf("%s second=%d emptyScans=%d timerWakeUps=%d fallbackChecks=%d%n",
                    LocalDateTime.now(), second, emptyScans.sum(), wakeUps.sum(), fallbackChecks.sum());
        }

        running.set(false);
        timerThread.join(1000L);
    }

    private static int resolveSeconds(String[] args) {
        if (args.length < 2) {
            return 20;
        }
        return Math.max(5, Integer.parseInt(args[1]));
    }

    static long scanMinuteBucket(String minuteBucketKey, int round) {
        long checksum = minuteBucketKey.hashCode() ^ round;
        String request = "rangeByScore key=" + minuteBucketKey
                + " window=[" + (1740820800000L + round * 1000L)
                + "," + (1740820801000L + round * 1000L) + ")";
        for (int i = 0; i < request.length(); i++) {
            checksum = Long.rotateLeft(checksum * 31 + request.charAt(i), 5);
        }
        return checksum;
    }

    static long simulateFallbackGuard(String minuteBucketKey, int round) {
        long checksum = round;
        String payload = minuteBucketKey + "|redisMissCheck|taskMapper.getTasksByTimeRange|status=0";
        for (int i = 0; i < payload.length(); i++) {
            checksum = Long.rotateLeft(checksum * 17 + payload.charAt(i), 3);
        }
        return checksum;
    }

    static List<String> buildMinuteBucketKeys() {
        List<String> keys = new ArrayList<String>();
        String previousMinute = "2026-03-30 10:14";
        String currentMinute = "2026-03-30 10:15";
        for (int bucket = 0; bucket < 5; bucket++) {
            keys.add(previousMinute + "_" + bucket);
            keys.add(currentMinute + "_" + bucket);
        }
        return keys;
    }

    static long resolvePid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int atIndex = runtimeName.indexOf('@');
        if (atIndex <= 0) {
            return -1L;
        }
        return Long.parseLong(runtimeName.substring(0, atIndex));
    }

    private static void printInstruction() {
        System.out.println("xtimer 空 minuteBucketKey 扫描热点示例默认不执行。");
        System.out.println("预览模式：java -cp target/classes "
                + "com.example.cpuhightroubleshootingdemo.cpu.XtimerEmptyScanCpuDemo --preview");
        System.out.println("真实复现：java -cp target/classes "
                + "com.example.cpuhightroubleshootingdemo.cpu.XtimerEmptyScanCpuDemo --run 20");
    }

    public static final class ScanPreview {

        private final String hotThreadName;
        private final int sliceKeyCount;
        private final long emptyScanCalls;
        private final long timerTaskWakeUps;
        private final long fallbackChecks;
        private final long elapsedMicros;
        private final long checksum;

        public ScanPreview(String hotThreadName,
                           int sliceKeyCount,
                           long emptyScanCalls,
                           long timerTaskWakeUps,
                           long fallbackChecks,
                           long elapsedMicros,
                           long checksum) {
            this.hotThreadName = hotThreadName;
            this.sliceKeyCount = sliceKeyCount;
            this.emptyScanCalls = emptyScanCalls;
            this.timerTaskWakeUps = timerTaskWakeUps;
            this.fallbackChecks = fallbackChecks;
            this.elapsedMicros = elapsedMicros;
            this.checksum = checksum;
        }

        public String hotThreadName() {
            return hotThreadName;
        }

        public int sliceKeyCount() {
            return sliceKeyCount;
        }

        public long emptyScanCalls() {
            return emptyScanCalls;
        }

        public long timerTaskWakeUps() {
            return timerTaskWakeUps;
        }

        public long fallbackChecks() {
            return fallbackChecks;
        }

        public long elapsedMicros() {
            return elapsedMicros;
        }

        public long checksum() {
            return checksum;
        }

        @Override
        public String toString() {
            return "ScanPreview{" +
                    "hotThreadName='" + hotThreadName + '\'' +
                    ", sliceKeyCount=" + sliceKeyCount +
                    ", emptyScanCalls=" + emptyScanCalls +
                    ", timerTaskWakeUps=" + timerTaskWakeUps +
                    ", fallbackChecks=" + fallbackChecks +
                    ", elapsedMicros=" + elapsedMicros +
                    ", checksum=" + checksum +
                    '}';
        }
    }
}
