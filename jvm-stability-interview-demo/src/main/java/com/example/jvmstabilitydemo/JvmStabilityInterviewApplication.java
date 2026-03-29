package com.example.jvmstabilitydemo;

import com.example.jvmstabilitydemo.support.CaseStoryLibrary;

public class JvmStabilityInterviewApplication {

    public static void main(String[] args) {
        System.out.println("=== JVM Stability Interview Demo ===");
        System.out.println();
        System.out.println("这个项目用于准备 3 类面试高频问题：OOM / Full GC / Deadlock");
        System.out.println("并且都尽量贴合你的履历场景：AsyncJobCenter、ScheduleCenter、司库信息系统");
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
        System.out.println("详细讲稿请看 docs 目录。代码演示请看 oom/fullgc/deadlock 包。\n");
    }
}
