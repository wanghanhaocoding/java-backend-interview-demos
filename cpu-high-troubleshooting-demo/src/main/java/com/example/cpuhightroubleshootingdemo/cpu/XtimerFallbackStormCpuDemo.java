package com.example.cpuhightroubleshootingdemo.cpu;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 模拟 xtimer 在 Redis 取数异常时，TriggerTimerTask 持续走 DB fallback，
 * 反复执行 taskMapper.getTasksByTimeRange 和回调上下文构造，造成 CPU 热点。
 */
public class XtimerFallbackStormCpuDemo {

    public static final String HOT_THREAD_NAME = "xtimer-fallback-query-storm";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printInstruction();
            return;
        }

        String command = args[0];
        if ("--preview".equals(command)) {
            System.out.println(previewScenario());
        } else if ("--run".equals(command)) {
            runStorm(resolveSeconds(args));
        } else {
            printInstruction();
        }
    }

    public static FallbackStormPreview previewScenario() {
        ArrayDeque<MinuteBucketFallbackTask> queue = seedQueue();
        Map<String, Integer> bucketAttempts = new LinkedHashMap<String, Integer>();
        long checksum = 0L;
        int totalAttempts = 0;

        while (totalAttempts < 9000) {
            MinuteBucketFallbackTask task = queue.removeFirst();
            totalAttempts++;
            Integer current = bucketAttempts.get(task.minuteBucketKey());
            bucketAttempts.put(task.minuteBucketKey(), current == null ? 1 : current + 1);
            checksum += simulateFallbackQuery(task);
            queue.addLast(task.nextRound());
        }

        int hottestBucketAttempts = 0;
        for (Integer count : bucketAttempts.values()) {
            hottestBucketAttempts = Math.max(hottestBucketAttempts, count.intValue());
        }

        return new FallbackStormPreview(HOT_THREAD_NAME, totalAttempts, bucketAttempts,
                hottestBucketAttempts, checksum);
    }

    public static void runStorm(int seconds) throws InterruptedException {
        final AtomicBoolean running = new AtomicBoolean(true);
        final LongAdder attempts = new LongAdder();
        final Map<String, LongAdder> bucketAttempts = new LinkedHashMap<String, LongAdder>();
        for (MinuteBucketFallbackTask task : seedQueue()) {
            bucketAttempts.put(task.minuteBucketKey(), new LongAdder());
        }

        Thread dispatcher = new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayDeque<MinuteBucketFallbackTask> queue = seedQueue();
                long checksum = 0L;
                while (running.get()) {
                    MinuteBucketFallbackTask task = queue.removeFirst();
                    attempts.increment();
                    bucketAttempts.get(task.minuteBucketKey()).increment();
                    checksum += simulateFallbackQuery(task);
                    queue.addLast(task.nextRound());
                }
                System.out.println("xtimer fallback checksum=" + checksum);
            }
        }, HOT_THREAD_NAME);
        dispatcher.setDaemon(true);
        dispatcher.start();

        long pid = resolvePid();
        System.out.println("pid=" + pid + ", hotThread=" + HOT_THREAD_NAME);
        System.out.println("建议另开终端执行：top -Hp " + pid);
        System.out.println("重点观察 taskMapper.getTasksByTimeRange 和回调上下文重建带来的热点线程。");

        for (int second = 1; second <= seconds; second++) {
            Thread.sleep(1000L);
            System.out.printf("%s second=%d attempts=%d hottestBucket=%s%n",
                    LocalDateTime.now(), second, attempts.sum(), hottestBucket(bucketAttempts));
        }

        running.set(false);
        dispatcher.join(1000L);
    }

    private static int resolveSeconds(String[] args) {
        if (args.length < 2) {
            return 20;
        }
        return Math.max(5, Integer.parseInt(args[1]));
    }

    static String hottestBucket(Map<String, LongAdder> bucketAttempts) {
        String hottest = null;
        long hottestCount = Long.MIN_VALUE;
        for (Map.Entry<String, LongAdder> entry : bucketAttempts.entrySet()) {
            long current = entry.getValue().sum();
            if (current > hottestCount) {
                hottest = entry.getKey() + ":" + current;
                hottestCount = current;
            }
        }
        return hottest;
    }

    static long simulateFallbackQuery(MinuteBucketFallbackTask task) {
        long checksum = task.minuteBucketKey().hashCode() ^ task.round();
        String sql = "SELECT * FROM timer_task WHERE run_timer >= " + task.windowStartMillis()
                + " AND run_timer <= " + task.windowEndMillis()
                + " AND status = 0";
        String callbackContext = task.minuteBucketKey() + "|notify_http_param={url:http://callback/xtimer}"
                + "|timerId=" + task.timerId() + "|status=0|round=" + task.round();
        for (int i = 0; i < sql.length(); i++) {
            checksum = Long.rotateLeft(checksum * 29 + sql.charAt(i), 3);
        }
        for (int i = 0; i < callbackContext.length(); i++) {
            checksum = Long.rotateLeft(checksum * 33 + callbackContext.charAt(i), 5);
        }
        return checksum;
    }

    static ArrayDeque<MinuteBucketFallbackTask> seedQueue() {
        return new ArrayDeque<MinuteBucketFallbackTask>(Arrays.asList(
                new MinuteBucketFallbackTask("2026-03-30 10:14_0", 1743300840000L, 1743300840999L, 10001L, 0),
                new MinuteBucketFallbackTask("2026-03-30 10:14_1", 1743300841000L, 1743300841999L, 10002L, 0),
                new MinuteBucketFallbackTask("2026-03-30 10:15_0", 1743300900000L, 1743300900999L, 10003L, 0)
        ));
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
        System.out.println("xtimer DB fallback 查询风暴示例默认不执行。");
        System.out.println("预览模式：java -cp target/classes "
                + "com.example.cpuhightroubleshootingdemo.cpu.XtimerFallbackStormCpuDemo --preview");
        System.out.println("真实复现：java -cp target/classes "
                + "com.example.cpuhightroubleshootingdemo.cpu.XtimerFallbackStormCpuDemo --run 20");
    }

    public static final class MinuteBucketFallbackTask {

        private final String minuteBucketKey;
        private final long windowStartMillis;
        private final long windowEndMillis;
        private final long timerId;
        private final int round;

        public MinuteBucketFallbackTask(String minuteBucketKey,
                                        long windowStartMillis,
                                        long windowEndMillis,
                                        long timerId,
                                        int round) {
            this.minuteBucketKey = minuteBucketKey;
            this.windowStartMillis = windowStartMillis;
            this.windowEndMillis = windowEndMillis;
            this.timerId = timerId;
            this.round = round;
        }

        public String minuteBucketKey() {
            return minuteBucketKey;
        }

        public long windowStartMillis() {
            return windowStartMillis;
        }

        public long windowEndMillis() {
            return windowEndMillis;
        }

        public long timerId() {
            return timerId;
        }

        public int round() {
            return round;
        }

        public MinuteBucketFallbackTask nextRound() {
            return new MinuteBucketFallbackTask(minuteBucketKey, windowStartMillis, windowEndMillis, timerId, round + 1);
        }
    }

    public static final class FallbackStormPreview {

        private final String hotThreadName;
        private final int totalAttempts;
        private final Map<String, Integer> bucketAttempts;
        private final int hottestBucketAttempts;
        private final long checksum;

        public FallbackStormPreview(String hotThreadName,
                                    int totalAttempts,
                                    Map<String, Integer> bucketAttempts,
                                    int hottestBucketAttempts,
                                    long checksum) {
            this.hotThreadName = hotThreadName;
            this.totalAttempts = totalAttempts;
            this.bucketAttempts = bucketAttempts;
            this.hottestBucketAttempts = hottestBucketAttempts;
            this.checksum = checksum;
        }

        public String hotThreadName() {
            return hotThreadName;
        }

        public int totalAttempts() {
            return totalAttempts;
        }

        public Map<String, Integer> bucketAttempts() {
            return bucketAttempts;
        }

        public int hottestBucketAttempts() {
            return hottestBucketAttempts;
        }

        public long checksum() {
            return checksum;
        }

        @Override
        public String toString() {
            return "FallbackStormPreview{" +
                    "hotThreadName='" + hotThreadName + '\'' +
                    ", totalAttempts=" + totalAttempts +
                    ", bucketAttempts=" + bucketAttempts +
                    ", hottestBucketAttempts=" + hottestBucketAttempts +
                    ", checksum=" + checksum +
                    '}';
        }
    }
}
