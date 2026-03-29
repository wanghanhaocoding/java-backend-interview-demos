package com.example.asyncjobcenterretryshardingdemo.retry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class AsyncJobCenterRetryShardingDemoTest {

    @Autowired
    private AsyncJobCenterRetryShardingDemoService asyncJobCenterRetryShardingDemoService;

    @Test
    void shouldRouteTasksAcrossRollingTablesAndFinishAfterRetries() {
        AsyncJobCenterRetryShardingDemoService.RetryShardingResult result =
                asyncJobCenterRetryShardingDemoService.retryAndRollingShardDemo();

        assertThat(result.tableRoutes().keySet()).containsExactly(
                "t_statement_task_0",
                "t_statement_task_1",
                "t_statement_task_2"
        );
        assertThat(result.retryOrderTimes()).containsExactly(1_711_000_060L, 1_711_000_180L);
        assertThat(result.claimOwners()).containsExactly("worker-A", "worker-A", "worker-B");
        assertThat(result.finalStatus()).isEqualTo("SUCCESS");
    }
}
