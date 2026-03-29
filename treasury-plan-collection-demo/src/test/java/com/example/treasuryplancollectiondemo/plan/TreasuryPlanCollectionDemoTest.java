package com.example.treasuryplancollectiondemo.plan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class TreasuryPlanCollectionDemoTest {

    @Autowired
    private TreasuryPlanCollectionDemoService treasuryPlanCollectionDemoService;

    @Test
    void shouldRejectOverBudgetPlanAndKeepDeterministicExecutionOrder() {
        TreasuryPlanCollectionDemoService.PlanCollectionResult result =
                treasuryPlanCollectionDemoService.dailyPlanCollectionDemo();

        assertThat(result.acceptedPlanIds()).containsExactly("PLAN-1001", "PLAN-1002", "PLAN-1003", "PLAN-1005");
        assertThat(result.rejectedPlanReasons()).containsEntry("PLAN-1004", "budget-exceeded");
        assertThat(result.executionOrder()).containsExactly(
                "09:00#shard-1:PLAN-1002",
                "09:00#shard-0:PLAN-1003",
                "09:00#shard-0:PLAN-1001",
                "09:05#shard-1:PLAN-1005"
        );
    }
}
