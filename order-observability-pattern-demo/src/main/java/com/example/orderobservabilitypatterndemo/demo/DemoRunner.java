package com.example.orderobservabilitypatterndemo.demo;

import com.example.orderobservabilitypatterndemo.pattern.DesignPatternDemoService;
import com.example.orderobservabilitypatterndemo.treasury.TreasuryFlowDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final TreasuryFlowDemoService treasuryFlowDemoService;
    private final DesignPatternDemoService designPatternDemoService;

    public DemoRunner(TreasuryFlowDemoService treasuryFlowDemoService,
                      DesignPatternDemoService designPatternDemoService) {
        this.treasuryFlowDemoService = treasuryFlowDemoService;
        this.designPatternDemoService = designPatternDemoService;
    }

    @Override
    public void run(String... args) {
        printTitle("1. 司库指令成功流转");
        TreasuryFlowDemoService.TreasuryFlowResult successResult =
                treasuryFlowDemoService.treasuryFlowDemo("TXN-1001", "DIRECT_BANK", 200, 500);
        successResult.steps().forEach(System.out::println);

        printTitle("2. 渠道失败补偿");
        TreasuryFlowDemoService.TreasuryFlowResult failedResult =
                treasuryFlowDemoService.treasuryFlowDemo("TXN-1002", "DIRECT_BANK_FAIL", 200, 500);
        failedResult.steps().forEach(System.out::println);
        failedResult.compensationSteps().forEach(step -> System.out.println("compensation -> " + step));

        printTitle("3. 设计模式总结");
        DesignPatternDemoService.PatternSummary summary = designPatternDemoService.patternSummaryDemo();
        System.out.println("template = " + summary.templateName());
        System.out.println("strategy = " + summary.strategyName());
        System.out.println("validators = " + summary.validatorNames());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
