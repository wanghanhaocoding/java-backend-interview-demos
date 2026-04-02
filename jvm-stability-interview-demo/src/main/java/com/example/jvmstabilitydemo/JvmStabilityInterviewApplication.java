package com.example.jvmstabilitydemo;

import com.example.jvmstabilitydemo.support.CaseStoryLibrary;

public class JvmStabilityInterviewApplication {

    public static void main(String[] args) {
        System.out.println("=== JVM Stability Interview Demo ===");
        System.out.println();
        System.out.println("这个项目用于准备 4 类面试高频问题：OOM / Full GC / Deadlock / 线程定位");
        System.out.println("并且优先贴合你简历第一个项目：ScheduleCenter / bitstorm-svr-xtimer");
        System.out.println("主链路会反复围绕：MigratorWorker -> SchedulerWorker -> SchedulerTask.asyncHandleSlice -> TriggerWorker.work -> TriggerTimerTask -> TriggerPoolTask");
        System.out.println();
        System.out.println("--- OOM 案例摘要 ---");
        System.out.println(CaseStoryLibrary.oomSummary());
        System.out.println();
        System.out.println("--- Full GC 案例摘要 ---");
        System.out.println(CaseStoryLibrary.fullGcSummary());
        System.out.println();
        System.out.println("--- 死锁案例摘要 ---");
        System.out.println(CaseStoryLibrary.deadlockSummary());
        System.out.println();
        System.out.println("--- 线程定位案例摘要 ---");
        System.out.println(CaseStoryLibrary.threadTroubleshootingSummary());
        System.out.println();
        System.out.println("建议面试表达顺序：先 Full GC，再讲它继续恶化后的积压型 OOM。");
        System.out.println("详细讲稿请看 docs 目录。代码演示请看 oom/fullgc/deadlock/thread 包。\n");
    }
}
