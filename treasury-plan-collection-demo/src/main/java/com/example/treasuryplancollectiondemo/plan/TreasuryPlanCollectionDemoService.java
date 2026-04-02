package com.example.treasuryplancollectiondemo.plan;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TreasuryPlanCollectionDemoService {

    public PlanCollectionResult dailyPlanCollectionDemo() {
        List<String> steps = new ArrayList<>();
        Map<String, Integer> budgetByCorp = new LinkedHashMap<>();
        budgetByCorp.put("G1", 700);
        budgetByCorp.put("G2", 700);
        budgetByCorp.put("G3", 300);

        List<DailyPlan> planRequests = Arrays.asList(
                new DailyPlan("PLAN-1001", "G1", 300, 90, 110, "09:00", 0),
                new DailyPlan("PLAN-1002", "G2", 450, 95, 120, "09:00", 1),
                new DailyPlan("PLAN-1003", "G3", 260, 90, 90, "09:00", 0),
                new DailyPlan("PLAN-1004", "G1", 500, 88, 130, "09:00", 1),
                new DailyPlan("PLAN-1005", "G2", 120, 70, 140, "09:05", 1)
        );

        steps.add("1. 生成 5 笔待处理日计划，覆盖 2 个归集时间窗口");

        Map<String, Integer> usedBudget = new LinkedHashMap<>();
        List<DailyPlan> acceptedPlans = new ArrayList<>();
        Map<String, String> rejectedPlanReasons = new LinkedHashMap<>();

        for (DailyPlan plan : planRequests) {
            int used = usedBudget.getOrDefault(plan.corpCode(), 0);
            int budget = budgetByCorp.get(plan.corpCode());
            if (used + plan.amount() > budget) {
                rejectedPlanReasons.put(plan.planId(), "budget-exceeded");
                steps.add("2. 预算校验拒绝 " + plan.planId() + "，主体 " + plan.corpCode()
                        + " 剩余额度不足");
                continue;
            }
            acceptedPlans.add(plan);
            usedBudget.put(plan.corpCode(), used + plan.amount());
            steps.add("2. 预算校验通过 " + plan.planId() + "，累计占用 " + plan.corpCode()
                    + "=" + usedBudget.get(plan.corpCode()));
        }

        Map<String, List<String>> windowAssignments = new LinkedHashMap<>();
        for (DailyPlan plan : acceptedPlans) {
            String assignment = "shard-" + plan.shardId() + ":" + plan.planId();
            windowAssignments.computeIfAbsent(plan.fixedWindow(), key -> new ArrayList<>()).add(assignment);
        }
        steps.add("3. 按固定窗口把通过预算校验的计划编排到 09:00 和 09:05 两个归集批次");

        List<DailyPlan> executionPlans = new ArrayList<>(acceptedPlans);
        executionPlans.sort(Comparator
                .comparing(DailyPlan::fixedWindow)
                .thenComparing(DailyPlan::priority, Comparator.reverseOrder())
                .thenComparing(DailyPlan::orderTime));

        List<String> executionOrder = new ArrayList<>();
        for (DailyPlan plan : executionPlans) {
            executionOrder.add(plan.fixedWindow() + "#shard-" + plan.shardId() + ":" + plan.planId());
        }
        steps.add("4. 同一窗口内先按 priority 高低排序；priority 相同再按 order_time 保证公平");
        steps.add("5. 调度器按 shard 并行下发归集任务，最终执行顺序为 " + executionOrder);

        return new PlanCollectionResult(
                steps,
                acceptedPlans.stream().map(DailyPlan::planId).collect(Collectors.toList()),
                rejectedPlanReasons,
                windowAssignments,
                executionOrder
        );
    }

    private static final class DailyPlan {

        private final String planId;
        private final String corpCode;
        private final int amount;
        private final int priority;
        private final long orderTime;
        private final String fixedWindow;
        private final int shardId;

        private DailyPlan(String planId,
                          String corpCode,
                          int amount,
                          int priority,
                          long orderTime,
                          String fixedWindow,
                          int shardId) {
            this.planId = planId;
            this.corpCode = corpCode;
            this.amount = amount;
            this.priority = priority;
            this.orderTime = orderTime;
            this.fixedWindow = fixedWindow;
            this.shardId = shardId;
        }

        private String planId() {
            return planId;
        }

        private String corpCode() {
            return corpCode;
        }

        private int amount() {
            return amount;
        }

        private int priority() {
            return priority;
        }

        private long orderTime() {
            return orderTime;
        }

        private String fixedWindow() {
            return fixedWindow;
        }

        private int shardId() {
            return shardId;
        }
    }

    public static final class PlanCollectionResult {

        private final List<String> steps;
        private final List<String> acceptedPlanIds;
        private final Map<String, String> rejectedPlanReasons;
        private final Map<String, List<String>> windowAssignments;
        private final List<String> executionOrder;

        public PlanCollectionResult(List<String> steps,
                                    List<String> acceptedPlanIds,
                                    Map<String, String> rejectedPlanReasons,
                                    Map<String, List<String>> windowAssignments,
                                    List<String> executionOrder) {
            this.steps = steps;
            this.acceptedPlanIds = acceptedPlanIds;
            this.rejectedPlanReasons = rejectedPlanReasons;
            this.windowAssignments = windowAssignments;
            this.executionOrder = executionOrder;
        }

        public List<String> steps() {
            return steps;
        }

        public List<String> acceptedPlanIds() {
            return acceptedPlanIds;
        }

        public Map<String, String> rejectedPlanReasons() {
            return rejectedPlanReasons;
        }

        public Map<String, List<String>> windowAssignments() {
            return windowAssignments;
        }

        public List<String> executionOrder() {
            return executionOrder;
        }
    }
}
