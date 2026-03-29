package com.example.orderobservabilitypatterndemo.pattern;

import com.example.orderobservabilitypatterndemo.order.OrderFulfillmentDemoService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DesignPatternDemoService {

    private final OrderFulfillmentDemoService orderFulfillmentDemoService;

    public DesignPatternDemoService(OrderFulfillmentDemoService orderFulfillmentDemoService) {
        this.orderFulfillmentDemoService = orderFulfillmentDemoService;
    }

    public PatternSummary patternSummaryDemo() {
        OrderFulfillmentDemoService.OrderFlowResult result =
                orderFulfillmentDemoService.checkoutDemo("ORD-PATTERN-01", "WALLET", 1, 5);

        return new PatternSummary(
                result.templateName(),
                result.strategyName(),
                result.validatorNames()
        );
    }

    public record PatternSummary(
            String templateName,
            String strategyName,
            List<String> validatorNames
    ) {
    }
}
