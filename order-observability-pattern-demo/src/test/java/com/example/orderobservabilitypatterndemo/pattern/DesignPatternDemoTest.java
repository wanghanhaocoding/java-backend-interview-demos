package com.example.orderobservabilitypatterndemo.pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class DesignPatternDemoTest {

    @Autowired
    private DesignPatternDemoService designPatternDemoService;

    @Test
    void summaryShouldExposeTemplateStrategyAndValidatorChain() {
        DesignPatternDemoService.PatternSummary summary = designPatternDemoService.patternSummaryDemo();

        assertThat(summary.templateName()).isEqualTo("TreasuryFlowTemplate");
        assertThat(summary.strategyName()).isEqualTo("DirectBankDispatchStrategy");
        assertThat(summary.validatorNames()).containsExactly("PositiveAmountValidator", "BudgetQuotaValidator");
    }
}
