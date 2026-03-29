package com.example.schedulecenterscalingdemo.scaling;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@Service
public class ScheduleCenterScalingDemoService {

    private static final int LOCAL_QUEUE_LIMIT = 2;

    public ScalingResult clusterCoordinationDemo() {
        List<String> steps = new ArrayList<>();
        Map<Integer, String> bucketClaims = new LinkedHashMap<>();
        Map<String, List<String>> workerExecutions = new LinkedHashMap<>();

        List<ScheduledWork> works = List.of(
                new ScheduledWork("COLLECT-1001", 0),
                new ScheduledWork("COLLECT-1002", 0),
                new ScheduledWork("COLLECT-1003", 0),
                new ScheduledWork("PLAN-2001", 1),
                new ScheduledWork("PLAN-2002", 1),
                new ScheduledWork("BILL-3001", 2)
        );

        Map<Integer, List<ScheduledWork>> worksByBucket = new LinkedHashMap<>();
        for (ScheduledWork work : works) {
            worksByBucket.computeIfAbsent(work.bucketId(), key -> new ArrayList<>()).add(work);
        }

        for (Map.Entry<Integer, List<ScheduledWork>> entry : worksByBucket.entrySet()) {
            int bucketId = entry.getKey();
            String primaryNode = bucketId % 2 == 0 ? "node-A" : "node-B";
            String secondaryNode = "node-A".equals(primaryNode) ? "node-B" : "node-A";

            steps.add("bucket-" + bucketId + " 由 schedulerPool 发起竞争，"
                    + primaryNode + " 先尝试抢锁");
            bucketClaims.put(bucketId, primaryNode);
            steps.add(secondaryNode + " 也发起扫描，但因为 bucket-" + bucketId
                    + " 已被 " + primaryNode + " 占有，所以直接跳过");

            Queue<ScheduledWork> localQueue = new ArrayDeque<>();
            for (ScheduledWork work : entry.getValue()) {
                if (localQueue.size() >= LOCAL_QUEUE_LIMIT) {
                    steps.add(primaryNode + " 的 schedulerPool 发现本地队列达到上限，触发背压，"
                            + "等待 workerPool 先消费一个任务");
                    drainOne(primaryNode, localQueue, workerExecutions, steps);
                }
                localQueue.offer(work);
                steps.add(primaryNode + " 的 schedulerPool 预取任务 " + work.taskId() + " 到本地队列");
            }

            while (!localQueue.isEmpty()) {
                drainOne(primaryNode, localQueue, workerExecutions, steps);
            }
        }

        return new ScalingResult(steps, bucketClaims, workerExecutions);
    }

    private void drainOne(String node,
                          Queue<ScheduledWork> localQueue,
                          Map<String, List<String>> workerExecutions,
                          List<String> steps) {
        ScheduledWork next = localQueue.poll();
        if (next == null) {
            return;
        }
        workerExecutions.computeIfAbsent(node, key -> new ArrayList<>()).add(next.taskId());
        steps.add(node + " 的 workerPool 执行任务 " + next.taskId()
                + "，当前本地队列剩余 " + localQueue.size());
    }

    private record ScheduledWork(String taskId, int bucketId) {
    }

    public record ScalingResult(
            List<String> steps,
            Map<Integer, String> bucketClaims,
            Map<String, List<String>> workerExecutions
    ) {
    }
}
