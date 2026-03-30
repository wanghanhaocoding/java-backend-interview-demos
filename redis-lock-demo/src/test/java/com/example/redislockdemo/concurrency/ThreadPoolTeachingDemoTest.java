package com.example.redislockdemo.concurrency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadPoolTeachingDemoTest {

    private final ThreadPoolTeachingDemoService threadPoolTeachingDemoService = new ThreadPoolTeachingDemoService();

    @Test
    void commonPoolTypesOverviewContainsPracticalRecommendations() {
        // 这个测试对应“工作里常见线程池怎么选”的总览表。
        assertThat(threadPoolTeachingDemoService.commonPoolTypesOverview())
                .extracting(ThreadPoolTeachingDemoService.PoolTypeNote::poolName)
                .contains("fixed-business-pool", "single-thread-executor", "scheduled-thread-pool", "executors-factory-warning");
    }

    @Test
    void taskFlowDemoShowsQueueExpansionAndRejection() throws Exception {
        ThreadPoolTeachingDemoService.TaskFlowDemoResult result = threadPoolTeachingDemoService.taskFlowAndAbortPolicyDemo();

        // 核心观察点：线程池会先吃满 core，再进入队列，再扩到 max，最后触发拒绝。
        assertThat(result.largestPoolSize()).isEqualTo(4);
        assertThat(result.queuedPeak()).isEqualTo(2);
        assertThat(result.rejectedTasks()).isEqualTo(1);
        assertThat(result.startedTasks()).isEqualTo(6);
        assertThat(result.submissionFlow()).anyMatch(line -> line.contains("rejected"));
    }

    @Test
    void callerRunsPolicyExecutesOverflowTaskInSubmittingThread() throws Exception {
        ThreadPoolTeachingDemoService.CallerRunsDemoResult result = threadPoolTeachingDemoService.callerRunsPolicyDemo();

        // 第三个任务会落到提交线程执行，用来体现 CallerRunsPolicy 的“回压”效果。
        assertThat(result.callerRunsCount()).isEqualTo(1);
        assertThat(result.executionThreads()).anyMatch(name -> name.contains("main"));
    }

    @Test
    void shutdownLifecycleRejectsNewTasksAndTerminatesPool() throws Exception {
        ThreadPoolTeachingDemoService.ShutdownDemoResult result = threadPoolTeachingDemoService.shutdownLifecycleDemo();

        // 关闭流程要点：shutdown 后拒绝新任务，必要时 shutdownNow 打断并收尾。
        assertThat(result.shutdownCalled()).isTrue();
        assertThat(result.rejectedAfterShutdown()).isTrue();
        assertThat(result.terminatedFinally()).isTrue();
        assertThat(result.neverStartedTasks()).isGreaterThanOrEqualTo(1);
        assertThat(result.interruptedTasks()).isGreaterThanOrEqualTo(1);
    }
}
