package com.example.asyncjobcentercoredemo.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class AsyncJobCenterCoreDemoTest {

    @Autowired
    private AsyncJobCenterCoreDemoService asyncJobCenterCoreDemoService;

    @Test
    void shouldRunCreateHoldSetLifecycle() {
        AsyncJobCenterCoreDemoService.CoreFlowResult result =
                asyncJobCenterCoreDemoService.serverWorkerLifecycleDemo();

        assertThat(result.statusTimeline()).containsExactly(
                "PENDING",
                "PROCESSING",
                "PENDING",
                "PROCESSING",
                "SUCCESS"
        );
        assertThat(result.stageTimeline()).containsExactly(
                "prepareReceipt",
                "prepareReceipt",
                "dispatchReceipt",
                "dispatchReceipt",
                "finished"
        );
        assertThat(result.tableTasks()).containsKey("t_treasury_receipt_task_0");
    }
}
