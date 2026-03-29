package com.example.orderobservabilitypatterndemo.order;

import com.example.orderobservabilitypatterndemo.observability.ObservabilityDemoService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OrderFulfillmentDemoService {

    private final ObservabilityDemoService observabilityDemoService;

    public OrderFulfillmentDemoService(ObservabilityDemoService observabilityDemoService) {
        this.observabilityDemoService = observabilityDemoService;
    }

    public void reset() {
        observabilityDemoService.reset();
    }

    public OrderFlowResult checkoutDemo(String orderId, String paymentMethod, int quantity, int stockAvailable) {
        reset();
        OrderRequest request = new OrderRequest(orderId, paymentMethod, quantity, stockAvailable);
        return new CheckoutTemplate().execute(request);
    }

    private final class CheckoutTemplate extends AbstractCheckoutTemplate {

        @Override
        protected ValidationSummary validate(OrderRequest request, String traceId, List<String> steps) {
            List<String> validatorNames = new ArrayList<>();
            for (OrderValidator validator : validators()) {
                validatorNames.add(validator.name());
                ValidationResult result = validator.validate(request);
                observabilityDemoService.log(traceId, "validate", validator.name() + "=" + result.message());
                if (!result.passed()) {
                    steps.add("1. 校验失败：" + result.message());
                    observabilityDemoService.increment("order.reject");
                    return new ValidationSummary(false, validatorNames);
                }
            }
            steps.add("1. 所有校验通过，进入预占库存");
            return new ValidationSummary(true, validatorNames);
        }

        @Override
        protected void reserveInventory(OrderRequest request, String traceId, List<String> steps) {
            steps.add("2. 预占库存 quantity=" + request.quantity());
            observabilityDemoService.log(traceId, "inventory", "reserve quantity=" + request.quantity());
        }

        @Override
        protected PaymentSummary pay(OrderRequest request, String traceId, List<String> steps) {
            PaymentStrategy strategy = strategies().getOrDefault(request.paymentMethod(), new CardPaymentStrategy());
            PaymentResult result = strategy.pay(request);
            observabilityDemoService.log(traceId, "payment", strategy.name() + "=" + result.status());
            steps.add("3. 支付策略 " + strategy.name() + " 执行结果 = " + result.status());
            return new PaymentSummary(result.success(), strategy.name());
        }

        @Override
        protected void complete(OrderRequest request, String traceId, List<String> steps) {
            steps.add("4. 订单进入 COMPLETED");
            observabilityDemoService.log(traceId, "order", "status=COMPLETED");
            observabilityDemoService.increment("order.success");
            observabilityDemoService.recordLatency("order.checkout", 35L);
        }

        @Override
        protected void compensate(OrderRequest request, String traceId, List<String> compensationSteps) {
            compensationSteps.add("release inventory for " + request.orderId());
            observabilityDemoService.log(traceId, "compensation", "release inventory");
            observabilityDemoService.increment("order.compensation");
            observabilityDemoService.recordLatency("order.checkout", 42L);
        }
    }

    private abstract class AbstractCheckoutTemplate {

        public OrderFlowResult execute(OrderRequest request) {
            List<String> steps = new ArrayList<>();
            List<String> compensationSteps = new ArrayList<>();
            String traceId = observabilityDemoService.startTrace("checkout");

            ValidationSummary validationSummary = validate(request, traceId, steps);
            if (!validationSummary.passed()) {
                observabilityDemoService.log(traceId, "order", "status=REJECTED");
                return new OrderFlowResult(
                        traceId,
                        "REJECTED",
                        "N/A",
                        "CheckoutTemplate",
                        validationSummary.validatorNames(),
                        steps,
                        compensationSteps
                );
            }

            reserveInventory(request, traceId, steps);
            PaymentSummary paymentSummary = pay(request, traceId, steps);

            if (!paymentSummary.success()) {
                steps.add("4. 支付失败，订单进入 CANCELLED，开始补偿");
                compensate(request, traceId, compensationSteps);
                return new OrderFlowResult(
                        traceId,
                        "CANCELLED",
                        paymentSummary.strategyName(),
                        "CheckoutTemplate",
                        validationSummary.validatorNames(),
                        steps,
                        compensationSteps
                );
            }

            complete(request, traceId, steps);
            return new OrderFlowResult(
                    traceId,
                    "COMPLETED",
                    paymentSummary.strategyName(),
                    "CheckoutTemplate",
                    validationSummary.validatorNames(),
                    steps,
                    compensationSteps
            );
        }

        protected abstract ValidationSummary validate(OrderRequest request, String traceId, List<String> steps);

        protected abstract void reserveInventory(OrderRequest request, String traceId, List<String> steps);

        protected abstract PaymentSummary pay(OrderRequest request, String traceId, List<String> steps);

        protected abstract void complete(OrderRequest request, String traceId, List<String> steps);

        protected abstract void compensate(OrderRequest request, String traceId, List<String> compensationSteps);
    }

    private List<OrderValidator> validators() {
        return List.of(new PositiveQuantityValidator(), new StockValidator());
    }

    private Map<String, PaymentStrategy> strategies() {
        return Map.of(
                "WALLET", new WalletPaymentStrategy(),
                "CARD", new CardPaymentStrategy(),
                "CARD_FAIL", new FailCardPaymentStrategy()
        );
    }

    public record OrderFlowResult(
            String traceId,
            String finalStatus,
            String strategyName,
            String templateName,
            List<String> validatorNames,
            List<String> steps,
            List<String> compensationSteps
    ) {
    }

    private record OrderRequest(
            String orderId,
            String paymentMethod,
            int quantity,
            int stockAvailable
    ) {
    }

    private record ValidationSummary(
            boolean passed,
            List<String> validatorNames
    ) {
    }

    private record PaymentSummary(
            boolean success,
            String strategyName
    ) {
    }

    private interface OrderValidator {
        ValidationResult validate(OrderRequest request);

        String name();
    }

    private record ValidationResult(
            boolean passed,
            String message
    ) {
    }

    private interface PaymentStrategy {
        PaymentResult pay(OrderRequest request);

        String name();
    }

    private record PaymentResult(
            boolean success,
            String status
    ) {
    }

    private static final class PositiveQuantityValidator implements OrderValidator {

        @Override
        public ValidationResult validate(OrderRequest request) {
            return request.quantity() > 0
                    ? new ValidationResult(true, "quantity ok")
                    : new ValidationResult(false, "quantity must be positive");
        }

        @Override
        public String name() {
            return "PositiveQuantityValidator";
        }
    }

    private static final class StockValidator implements OrderValidator {

        @Override
        public ValidationResult validate(OrderRequest request) {
            return request.stockAvailable() >= request.quantity()
                    ? new ValidationResult(true, "stock ok")
                    : new ValidationResult(false, "stock not enough");
        }

        @Override
        public String name() {
            return "StockValidator";
        }
    }

    private static final class WalletPaymentStrategy implements PaymentStrategy {

        @Override
        public PaymentResult pay(OrderRequest request) {
            return new PaymentResult(true, "PAID");
        }

        @Override
        public String name() {
            return "WalletPaymentStrategy";
        }
    }

    private static final class CardPaymentStrategy implements PaymentStrategy {

        @Override
        public PaymentResult pay(OrderRequest request) {
            return new PaymentResult(true, "PAID");
        }

        @Override
        public String name() {
            return "CardPaymentStrategy";
        }
    }

    private static final class FailCardPaymentStrategy implements PaymentStrategy {

        @Override
        public PaymentResult pay(OrderRequest request) {
            return new PaymentResult(false, "FAILED");
        }

        @Override
        public String name() {
            return "FailCardPaymentStrategy";
        }
    }
}
