package com.example.orderobservabilitypatterndemo.treasury;

import com.example.orderobservabilitypatterndemo.observability.ObservabilityDemoService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TreasuryFlowDemoService {

    private final ObservabilityDemoService observabilityDemoService;

    public TreasuryFlowDemoService(ObservabilityDemoService observabilityDemoService) {
        this.observabilityDemoService = observabilityDemoService;
    }

    public void reset() {
        observabilityDemoService.reset();
    }

    public TreasuryFlowResult treasuryFlowDemo(String instructionId, String dispatchChannel, int amount, int availableQuota) {
        reset();
        TreasuryRequest request = new TreasuryRequest(instructionId, dispatchChannel, amount, availableQuota);
        return new TreasuryFlowTemplate().execute(request);
    }

    private final class TreasuryFlowTemplate extends AbstractTreasuryFlowTemplate {

        @Override
        protected ValidationSummary validate(TreasuryRequest request, String traceId, List<String> steps) {
            List<String> validatorNames = new ArrayList<>();
            for (TreasuryValidator validator : validators()) {
                validatorNames.add(validator.name());
                ValidationResult result = validator.validate(request);
                observabilityDemoService.log(traceId, "validate", validator.name() + "=" + result.message());
                if (!result.passed()) {
                    steps.add("1. 校验失败：" + result.message());
                    observabilityDemoService.increment("treasury.flow.reject");
                    return new ValidationSummary(false, validatorNames);
                }
            }
            steps.add("1. 所有校验通过，进入预占归集额度");
            return new ValidationSummary(true, validatorNames);
        }

        @Override
        protected void reserveQuota(TreasuryRequest request, String traceId, List<String> steps) {
            steps.add("2. 预占归集额度 amount=" + request.amount());
            observabilityDemoService.log(traceId, "quota", "reserve amount=" + request.amount());
        }

        @Override
        protected DispatchSummary dispatch(TreasuryRequest request, String traceId, List<String> steps) {
            DispatchStrategy strategy = strategies().getOrDefault(request.dispatchChannel(), new HostToHostDispatchStrategy());
            DispatchResult result = strategy.dispatch(request);
            observabilityDemoService.log(traceId, "dispatch", strategy.name() + "=" + result.status());
            steps.add("3. 渠道策略 " + strategy.name() + " 执行结果 = " + result.status());
            return new DispatchSummary(result.success(), strategy.name());
        }

        @Override
        protected void complete(TreasuryRequest request, String traceId, List<String> steps) {
            steps.add("4. 指令进入 DISPATCHED，等待后续回执链路");
            observabilityDemoService.log(traceId, "instruction", "status=DISPATCHED");
            observabilityDemoService.increment("treasury.flow.success");
            observabilityDemoService.recordLatency("treasury.flow.dispatch", 35L);
        }

        @Override
        protected void compensate(TreasuryRequest request, String traceId, List<String> compensationSteps) {
            compensationSteps.add("release reserved quota for " + request.instructionId());
            observabilityDemoService.log(traceId, "compensation", "release reserved quota");
            observabilityDemoService.increment("treasury.flow.compensation");
            observabilityDemoService.recordLatency("treasury.flow.dispatch", 42L);
        }
    }

    private abstract class AbstractTreasuryFlowTemplate {

        public TreasuryFlowResult execute(TreasuryRequest request) {
            List<String> steps = new ArrayList<>();
            List<String> compensationSteps = new ArrayList<>();
            String traceId = observabilityDemoService.startTrace("treasury-flow");

            ValidationSummary validationSummary = validate(request, traceId, steps);
            if (!validationSummary.passed()) {
                observabilityDemoService.log(traceId, "instruction", "status=REJECTED");
                return new TreasuryFlowResult(
                        traceId,
                        "REJECTED",
                        "N/A",
                        "TreasuryFlowTemplate",
                        validationSummary.validatorNames(),
                        steps,
                        compensationSteps
                );
            }

            reserveQuota(request, traceId, steps);
            DispatchSummary dispatchSummary = dispatch(request, traceId, steps);

            if (!dispatchSummary.success()) {
                steps.add("4. 渠道执行失败，指令进入 COMPENSATED，开始补偿");
                compensate(request, traceId, compensationSteps);
                return new TreasuryFlowResult(
                        traceId,
                        "COMPENSATED",
                        dispatchSummary.strategyName(),
                        "TreasuryFlowTemplate",
                        validationSummary.validatorNames(),
                        steps,
                        compensationSteps
                );
            }

            complete(request, traceId, steps);
            return new TreasuryFlowResult(
                    traceId,
                    "DISPATCHED",
                    dispatchSummary.strategyName(),
                    "TreasuryFlowTemplate",
                    validationSummary.validatorNames(),
                    steps,
                    compensationSteps
            );
        }

        protected abstract ValidationSummary validate(TreasuryRequest request, String traceId, List<String> steps);

        protected abstract void reserveQuota(TreasuryRequest request, String traceId, List<String> steps);

        protected abstract DispatchSummary dispatch(TreasuryRequest request, String traceId, List<String> steps);

        protected abstract void complete(TreasuryRequest request, String traceId, List<String> steps);

        protected abstract void compensate(TreasuryRequest request, String traceId, List<String> compensationSteps);
    }

    private List<TreasuryValidator> validators() {
        return List.of(new PositiveAmountValidator(), new BudgetQuotaValidator());
    }

    private Map<String, DispatchStrategy> strategies() {
        return Map.of(
                "DIRECT_BANK", new DirectBankDispatchStrategy(),
                "HOST_TO_HOST", new HostToHostDispatchStrategy(),
                "DIRECT_BANK_FAIL", new FailBankDispatchStrategy()
        );
    }

    public record TreasuryFlowResult(
            String traceId,
            String finalStatus,
            String strategyName,
            String templateName,
            List<String> validatorNames,
            List<String> steps,
            List<String> compensationSteps
    ) {
    }

    private record TreasuryRequest(
            String instructionId,
            String dispatchChannel,
            int amount,
            int availableQuota
    ) {
    }

    private record ValidationSummary(
            boolean passed,
            List<String> validatorNames
    ) {
    }

    private record DispatchSummary(
            boolean success,
            String strategyName
    ) {
    }

    private interface TreasuryValidator {
        ValidationResult validate(TreasuryRequest request);

        String name();
    }

    private record ValidationResult(
            boolean passed,
            String message
    ) {
    }

    private interface DispatchStrategy {
        DispatchResult dispatch(TreasuryRequest request);

        String name();
    }

    private record DispatchResult(
            boolean success,
            String status
    ) {
    }

    private static final class PositiveAmountValidator implements TreasuryValidator {

        @Override
        public ValidationResult validate(TreasuryRequest request) {
            return request.amount() > 0
                    ? new ValidationResult(true, "amount ok")
                    : new ValidationResult(false, "amount must be positive");
        }

        @Override
        public String name() {
            return "PositiveAmountValidator";
        }
    }

    private static final class BudgetQuotaValidator implements TreasuryValidator {

        @Override
        public ValidationResult validate(TreasuryRequest request) {
            return request.availableQuota() >= request.amount()
                    ? new ValidationResult(true, "quota ok")
                    : new ValidationResult(false, "budget quota not enough");
        }

        @Override
        public String name() {
            return "BudgetQuotaValidator";
        }
    }

    private static final class DirectBankDispatchStrategy implements DispatchStrategy {

        @Override
        public DispatchResult dispatch(TreasuryRequest request) {
            return new DispatchResult(true, "DISPATCHED");
        }

        @Override
        public String name() {
            return "DirectBankDispatchStrategy";
        }
    }

    private static final class HostToHostDispatchStrategy implements DispatchStrategy {

        @Override
        public DispatchResult dispatch(TreasuryRequest request) {
            return new DispatchResult(true, "DISPATCHED");
        }

        @Override
        public String name() {
            return "HostToHostDispatchStrategy";
        }
    }

    private static final class FailBankDispatchStrategy implements DispatchStrategy {

        @Override
        public DispatchResult dispatch(TreasuryRequest request) {
            return new DispatchResult(false, "FAILED");
        }

        @Override
        public String name() {
            return "FailBankDispatchStrategy";
        }
    }
}
