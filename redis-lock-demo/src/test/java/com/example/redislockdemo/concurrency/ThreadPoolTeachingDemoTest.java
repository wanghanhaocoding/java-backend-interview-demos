package com.example.redislockdemo.concurrency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadPoolTeachingDemoTest {

    private final ThreadPoolTeachingDemoService threadPoolTeachingDemoService = new ThreadPoolTeachingDemoService();

    @Test
    void commonPoolTypesOverviewContainsPracticalRecommendations() {
        assertThat(threadPoolTeachingDemoService.commonPoolTypesOverview())
                .extracting(ThreadPoolTeachingDemoService.PoolTypeNote::poolName)
                .contains("fixed-business-pool", "single-thread-executor", "scheduled-thread-pool", "executors-factory-warning");
    }

    @Test
    void taskFlowDemoShowsQueueExpansionAndRejection() throws Exception {
        ThreadPoolTeachingDemoService.TaskFlowDemoResult result = threadPoolTeachingDemoService.taskFlowAndAbortPolicyDemo();

        assertThat(result.largestPoolSize()).isEqualTo(4);
        assertThat(result.queuedPeak()).isEqualTo(2);
        assertThat(result.rejectedTasks()).isEqualTo(1);
        assertThat(result.startedTasks()).isEqualTo(6);
        assertThat(result.submissionFlow()).anyMatch(line -> line.contains("rejected"));
    }

    @Test
    void callerRunsPolicyExecutesOverflowTaskInSubmittingThread() throws Exception {
        ThreadPoolTeachingDemoService.CallerRunsDemoResult result = threadPoolTeachingDemoService.callerRunsPolicyDemo();

        assertThat(result.callerRunsCount()).isEqualTo(1);
        assertThat(result.executionThreads()).anyMatch(name -> name.contains("main"));
    }

    @Test
    void shutdownLifecycleRejectsNewTasksAndTerminatesPool() throws Exception {
        ThreadPoolTeachingDemoService.ShutdownDemoResult result = threadPoolTeachingDemoService.shutdownLifecycleDemo();

        assertThat(result.shutdownCalled()).isTrue();
        assertThat(result.rejectedAfterShutdown()).isTrue();
        assertThat(result.terminatedFinally()).isTrue();
        assertThat(result.neverStartedTasks()).isGreaterThanOrEqualTo(1);
        assertThat(result.interruptedTasks()).isGreaterThanOrEqualTo(1);
    }
}
