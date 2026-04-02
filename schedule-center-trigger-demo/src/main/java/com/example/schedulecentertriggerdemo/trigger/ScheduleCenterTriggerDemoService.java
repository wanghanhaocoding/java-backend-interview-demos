package com.example.schedulecentertriggerdemo.trigger;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class ScheduleCenterTriggerDemoService {

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int BUCKET_COUNT = 4;
    private final BucketIndex bucketIndex = new BucketIndex(BUCKET_COUNT);

    public TriggerPlanResult minuteBucketTriggerDemo() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 3, 30, 9, 0, 0);
        List<TriggerTask> tasks = Arrays.asList(
                new TriggerTask("PLAN-090001", baseTime.plusSeconds(5), "生成日计划"),
                new TriggerTask("PLAN-090002", baseTime.plusSeconds(17), "预算预校验"),
                new TriggerTask("PLAN-090003", baseTime.plusSeconds(25), "归集前置检查"),
                new TriggerTask("PLAN-090004", baseTime.plusSeconds(42), "发送归集指令")
        );

        Map<String, NavigableMap<Long, List<String>>> zsetIndex = new LinkedHashMap<>();
        Map<String, List<String>> bucketTasks = new LinkedHashMap<>();
        List<String> steps = new ArrayList<>();

        int registerStep = 1;
        for (TriggerTask task : tasks) {
            String minuteBucketKey = bucketIndex.buildMinuteBucketKey(task.taskId(), task.triggerTime());
            long triggerEpochMilli = toEpochMilli(task.triggerTime());
            zsetIndex.computeIfAbsent(minuteBucketKey, key -> new TreeMap<>())
                    .computeIfAbsent(triggerEpochMilli, key -> new ArrayList<>())
                    .add(task.taskId());
            bucketTasks.computeIfAbsent(minuteBucketKey, key -> new ArrayList<>()).add(task.taskId());
            steps.add(registerStep++ + ". 注册任务 " + task.taskId()
                    + " -> key=" + minuteBucketKey
                    + ", score=" + triggerEpochMilli
                    + ", scene=" + task.scene());
        }

        List<String> firedTaskIds = new ArrayList<>();
        int windowStep = registerStep;
        for (int cursorSeconds = 0; cursorSeconds < 60; cursorSeconds += 5) {
            LocalDateTime windowStart = baseTime.plusSeconds(cursorSeconds);
            LocalDateTime windowEnd = windowStart.plusSeconds(5);
            List<String> dueTasks = pollDue(zsetIndex, toEpochMilli(windowStart), toEpochMilli(windowEnd));
            if (dueTasks.isEmpty()) {
                steps.add(windowStep++ + ". 扫描窗口 [" + windowStart.toLocalTime() + ", "
                        + windowEnd.toLocalTime() + ") 没有到期任务");
                continue;
            }
            firedTaskIds.addAll(dueTasks);
            steps.add(windowStep++ + ". 扫描窗口 [" + windowStart.toLocalTime() + ", "
                    + windowEnd.toLocalTime() + ") 命中任务 " + dueTasks);
        }

        return new TriggerPlanResult(steps, bucketTasks, firedTaskIds);
    }

    private List<String> pollDue(Map<String, NavigableMap<Long, List<String>>> zsetIndex,
                                 long windowStart,
                                 long windowEndExclusive) {
        List<String> dueTasks = new ArrayList<>();
        for (NavigableMap<Long, List<String>> minuteBucket : zsetIndex.values()) {
            List<Long> dueScores = new ArrayList<>(minuteBucket.subMap(windowStart, true, windowEndExclusive, false).keySet());
            for (Long dueScore : dueScores) {
                dueTasks.addAll(minuteBucket.remove(dueScore));
            }
        }
        return dueTasks;
    }

    private long toEpochMilli(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static final class BucketIndex {

        private final int bucketCount;

        private BucketIndex(int bucketCount) {
            this.bucketCount = bucketCount;
        }

        private String buildMinuteBucketKey(String taskId, LocalDateTime triggerTime) {
            int bucket = Math.floorMod(taskId.hashCode(), bucketCount);
            LocalDateTime minute = triggerTime.truncatedTo(ChronoUnit.MINUTES);
            return MINUTE_FORMATTER.format(minute) + "_" + bucket;
        }
    }

    private static final class TriggerTask {

        private final String taskId;
        private final LocalDateTime triggerTime;
        private final String scene;

        private TriggerTask(String taskId, LocalDateTime triggerTime, String scene) {
            this.taskId = taskId;
            this.triggerTime = triggerTime;
            this.scene = scene;
        }

        private String taskId() {
            return taskId;
        }

        private LocalDateTime triggerTime() {
            return triggerTime;
        }

        private String scene() {
            return scene;
        }
    }

    public static final class TriggerPlanResult {

        private final List<String> steps;
        private final Map<String, List<String>> bucketTasks;
        private final List<String> firedTaskIds;

        public TriggerPlanResult(List<String> steps,
                                 Map<String, List<String>> bucketTasks,
                                 List<String> firedTaskIds) {
            this.steps = steps;
            this.bucketTasks = bucketTasks;
            this.firedTaskIds = firedTaskIds;
        }

        public List<String> steps() {
            return steps;
        }

        public Map<String, List<String>> bucketTasks() {
            return bucketTasks;
        }

        public List<String> firedTaskIds() {
            return firedTaskIds;
        }
    }
}
