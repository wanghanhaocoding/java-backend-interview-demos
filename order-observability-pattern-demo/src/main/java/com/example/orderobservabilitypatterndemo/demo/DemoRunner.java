package com.example.orderobservabilitypatterndemo.demo;

import com.example.orderobservabilitypatterndemo.order.OrderFulfillmentDemoService;
import com.example.orderobservabilitypatterndemo.pattern.DesignPatternDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final OrderFulfillmentDemoService orderFulfillmentDemoService;
    private final DesignPatternDemoService designPatternDemoService;

    public DemoRunner(OrderFulfillmentDemoService orderFulfillmentDemoService,
                      DesignPatternDemoService designPatternDemoService) {
        this.orderFulfillmentDemoService = orderFulfillmentDemoService;
        this.designPatternDemoService = designPatternDemoService;
    }

    @Override
    public void run(String... args) {
        printTitle("1. 订单成功流转");
        OrderFulfillmentDemoService.OrderFlowResult successResult =
                orderFulfillmentDemoService.checkoutDemo("ORD-1001", "WALLET", 2, 5);
        successResult.steps().forEach(System.out::println);

        printTitle("2. 支付失败补偿");
        OrderFulfillmentDemoService.OrderFlowResult failedResult =
                orderFulfillmentDemoService.checkoutDemo("ORD-1002", "CARD_FAIL", 2, 5);
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
