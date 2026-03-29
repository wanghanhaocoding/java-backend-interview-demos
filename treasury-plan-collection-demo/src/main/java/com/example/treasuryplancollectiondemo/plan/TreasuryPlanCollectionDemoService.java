package com.example.treasuryplancollectiondemo.plan;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TreasuryPlanCollectionDemoService {

    public PlanCollectionResult dailyPlanCollectionDemo() {
        List<String> steps = new ArrayList<>();
        Map<String, Integer> budgetByCorp = new LinkedHashMap<>();
        budgetByCorp.put("G1", 700);
        budgetByCorp.put("G2", 700);
        budgetByCorp.put("G3", 300);

        List<DailyPlan> planRequests = List.of(
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
                acceptedPlans.stream().map(DailyPlan::planId).toList(),
                rejectedPlanReasons,
                windowAssignments,
                executionOrder
        );
    }

    private record DailyPlan(String planId,
                             String corpCode,
                             int amount,
                             int priority,
                             long orderTime,
                             String fixedWindow,
                             int shardId) {
    }

    public record PlanCollectionResult(
            List<String> steps,
            List<String> acceptedPlanIds,
            Map<String, String> rejectedPlanReasons,
            Map<String, List<String>> windowAssignments,
            List<String> executionOrder
    ) {
    }
}
