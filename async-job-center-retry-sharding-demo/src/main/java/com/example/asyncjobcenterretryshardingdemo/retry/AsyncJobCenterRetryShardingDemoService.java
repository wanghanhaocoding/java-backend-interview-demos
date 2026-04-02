package com.example.asyncjobcenterretryshardingdemo.retry;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AsyncJobCenterRetryShardingDemoService {

    private static final int TABLE_CAPACITY = 2;
    private final AtomicInteger sequence = new AtomicInteger();

    public RetryShardingResult retryAndRollingShardDemo() {
        sequence.set(0);
        List<String> steps = new ArrayList<>();
        Map<String, List<String>> tableRoutes = new LinkedHashMap<>();

        for (int index = 0; index < 5; index++) {
            String taskId = nextTaskId();
            int tableIndex = index / TABLE_CAPACITY;
            String tableName = getTableName(tableIndex);
            tableRoutes.computeIfAbsent(tableName, key -> new ArrayList<>()).add(taskId);
            steps.add((index + 1) + ". 新任务 " + taskId + " 路由到 " + tableName
                    + "，因为前面热表容量阈值是 " + TABLE_CAPACITY);
        }

        steps.add("6. 旧方案用 MySQL 行锁抢同批任务，worker-A 持锁时 worker-B 只能等待，"
                + "热点批次越集中，DB 锁冲突越明显");
        steps.add("7. 改造后改用 Redis 锁占有 taskType+bucket，先把竞争从数据库挪出去");

        List<String> claimOwners = new ArrayList<>();
        List<Long> retryOrderTimes = new ArrayList<>();
        long baseOrderTime = 1_711_000_000L;
        RetryableTask task = new RetryableTask("AJOB-RETRY-0001", "makeReceiptPdf", TaskStatus.PENDING, baseOrderTime, 0);

        claimOwners.add("worker-A");
        task = task.claim();
        steps.add("8. 第一次由 worker-A 抢到 Redis 锁并占有任务，状态变为 PROCESSING");
        task = task.failAndReschedule(baseOrderTime + 60);
        retryOrderTimes.add(task.orderTime());
        steps.add("9. 第一次执行失败，crtRetryNum=1，order_time 回推到 " + task.orderTime());

        claimOwners.add("worker-A");
        task = task.claim();
        steps.add("10. 第二次还是 worker-A 领到任务，继续执行第二轮重试");
        task = task.failAndReschedule(baseOrderTime + 180);
        retryOrderTimes.add(task.orderTime());
        steps.add("11. 第二次执行失败，crtRetryNum=2，order_time 再回推到 " + task.orderTime());

        claimOwners.add("worker-B");
        task = task.claim();
        steps.add("12. 第三次由 worker-B 领到任务，实现多 worker 平滑接手");
        task = task.succeed();
        steps.add("13. 第三次执行成功，任务进入终态 SUCCESS");

        return new RetryShardingResult(steps, tableRoutes, claimOwners, retryOrderTimes, task.status().name());
    }

    private String nextTaskId() {
        return String.format("AJOB-%04d_statement-make_%d", sequence.incrementAndGet(), sequence.get() / 3);
    }

    private String getTableName(int tableIndex) {
        return "t_statement_task_" + tableIndex;
    }

    private static final class RetryableTask {

        private final String taskId;
        private final String stage;
        private final TaskStatus status;
        private final long orderTime;
        private final int crtRetryNum;

        private RetryableTask(String taskId,
                              String stage,
                              TaskStatus status,
                              long orderTime,
                              int crtRetryNum) {
            this.taskId = taskId;
            this.stage = stage;
            this.status = status;
            this.orderTime = orderTime;
            this.crtRetryNum = crtRetryNum;
        }

        private RetryableTask claim() {
            return new RetryableTask(taskId, stage, TaskStatus.PROCESSING, orderTime, crtRetryNum);
        }

        private RetryableTask failAndReschedule(long nextOrderTime) {
            return new RetryableTask(taskId, stage, TaskStatus.PENDING, nextOrderTime, crtRetryNum + 1);
        }

        private RetryableTask succeed() {
            return new RetryableTask(taskId, stage, TaskStatus.SUCCESS, orderTime, crtRetryNum);
        }

        private String taskId() {
            return taskId;
        }

        private String stage() {
            return stage;
        }

        private TaskStatus status() {
            return status;
        }

        private long orderTime() {
            return orderTime;
        }

        private int crtRetryNum() {
            return crtRetryNum;
        }
    }

    private enum TaskStatus {
        PENDING,
        PROCESSING,
        SUCCESS
    }

    public static final class RetryShardingResult {

        private final List<String> steps;
        private final Map<String, List<String>> tableRoutes;
        private final List<String> claimOwners;
        private final List<Long> retryOrderTimes;
        private final String finalStatus;

        public RetryShardingResult(List<String> steps,
                                   Map<String, List<String>> tableRoutes,
                                   List<String> claimOwners,
                                   List<Long> retryOrderTimes,
                                   String finalStatus) {
            this.steps = steps;
            this.tableRoutes = tableRoutes;
            this.claimOwners = claimOwners;
            this.retryOrderTimes = retryOrderTimes;
            this.finalStatus = finalStatus;
        }

        public List<String> steps() {
            return steps;
        }

        public Map<String, List<String>> tableRoutes() {
            return tableRoutes;
        }

        public List<String> claimOwners() {
            return claimOwners;
        }

        public List<Long> retryOrderTimes() {
            return retryOrderTimes;
        }

        public String finalStatus() {
            return finalStatus;
        }
    }
}
