package com.example.jvmstabilitydemo.fullgc;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * 用来模拟“批量预取过大 + 本地缓冲积压”带来的老年代压力。
 *
 * 这个示例不保证每台机器都出现完全一致的 Full GC 次数，
 * 但在小堆 + 打开 GC 日志的情况下，通常能观察到明显的 GC 压力。
 */
public class FullGcPressureDemo {

    private static final Queue<JobEnvelope> LOCAL_TRIGGER_QUEUE = new ArrayDeque<>();
    private static final List<byte[]> SURVIVOR_BUFFER = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !"--run".equals(args[0])) {
            printInstruction();
            return;
        }

        int round = 0;
        while (round++ < 200) {
            preloadWindow(round, 1_200);
            consumeSlowly(round);
            System.out.printf("%s round=%d queue=%d survivor=%d%n",
                LocalDateTime.now(), round, LOCAL_TRIGGER_QUEUE.size(), SURVIVOR_BUFFER.size());
            Thread.sleep(150);
        }

        System.out.println("演示结束。你可以结合 GC 日志观察 Old 区和 Full GC 情况。");
    }

    public static void preloadWindow(int round, int batchSize) {
        for (int i = 0; i < batchSize; i++) {
            LOCAL_TRIGGER_QUEUE.add(new JobEnvelope("job-" + round + '-' + i, new byte[64 * 1024], new byte[48 * 1024]));
        }
    }

    public static void consumeSlowly(int round) {
        int consumeCount = Math.min(280, LOCAL_TRIGGER_QUEUE.size());
        for (int i = 0; i < consumeCount; i++) {
            JobEnvelope envelope = LOCAL_TRIGGER_QUEUE.poll();
            if (envelope != null && i % 3 == 0) {
                SURVIVOR_BUFFER.add(envelope.callbackBody());
            }
        }

        if (SURVIVOR_BUFFER.size() > 4_000) {
            SURVIVOR_BUFFER.subList(0, 1_500).clear();
        }
    }

    public static int queueSize() {
        return LOCAL_TRIGGER_QUEUE.size();
    }

    private static void printInstruction() {
        System.out.println("Full GC 压力示例默认不执行。建议：");
        System.out.println("1) 先编译：mvn -q -DskipTests compile");
        System.out.println("2) 再执行：java -Xms256m -Xmx256m -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log -cp target/classes com.example.jvmstabilitydemo.fullgc.FullGcPressureDemo --run");
    }

    public static final class JobEnvelope {

        private final String jobId;
        private final byte[] triggerBody;
        private final byte[] callbackBody;

        public JobEnvelope(String jobId, byte[] triggerBody, byte[] callbackBody) {
            this.jobId = jobId;
            this.triggerBody = triggerBody;
            this.callbackBody = callbackBody;
        }

        public String jobId() {
            return jobId;
        }

        public byte[] triggerBody() {
            return triggerBody;
        }

        public byte[] callbackBody() {
            return callbackBody;
        }
    }
}
