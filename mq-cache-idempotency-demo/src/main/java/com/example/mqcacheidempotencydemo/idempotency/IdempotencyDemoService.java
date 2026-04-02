package com.example.mqcacheidempotencydemo.idempotency;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IdempotencyDemoService {

    private final Set<String> requestTokens = new LinkedHashSet<>();
    private final Map<String, String> callbackStatusStore = new LinkedHashMap<>();

    public void reset() {
        requestTokens.clear();
        callbackStatusStore.clear();
    }

    public SubmissionResult duplicateSubmissionDemo(String token, BigDecimal amount) {
        reset();
        List<String> steps = new ArrayList<>();

        String first = submit(token, amount, steps);
        String second = submit(token, amount, steps);
        return new SubmissionResult(steps, first, second);
    }

    public CallbackResult paymentCallbackDemo(String businessKey) {
        reset();
        List<String> steps = new ArrayList<>();

        applyCallback(businessKey, "SUCCESS", steps);
        applyCallback(businessKey, "SUCCESS", steps);
        applyCallback(businessKey, "PROCESSING", steps);

        return new CallbackResult(steps, callbackStatusStore.get(businessKey));
    }

    private String submit(String token, BigDecimal amount, List<String> steps) {
        if (requestTokens.add(token)) {
            steps.add("1. 首次提交金额 " + amount + "，幂等 token 首次写入成功，业务继续执行");
            return "ACCEPTED";
        }
        steps.add("2. 第二次提交命中相同 token，直接拒绝，避免重复扣款");
        return "DUPLICATE";
    }

    private void applyCallback(String businessKey, String incomingStatus, List<String> steps) {
        String currentStatus = callbackStatusStore.get(businessKey);
        if ("SUCCESS".equals(currentStatus) || "FAILED".equals(currentStatus)) {
            steps.add("3. 当前订单已经进入终态 " + currentStatus + "，忽略迟到或重复回调 " + incomingStatus);
            return;
        }
        callbackStatusStore.put(businessKey, incomingStatus);
        steps.add("3. 首次回调把订单状态更新成 " + incomingStatus);
    }

    public static final class SubmissionResult {

        private final List<String> steps;
        private final String firstSubmissionStatus;
        private final String secondSubmissionStatus;

        public SubmissionResult(List<String> steps, String firstSubmissionStatus, String secondSubmissionStatus) {
            this.steps = steps;
            this.firstSubmissionStatus = firstSubmissionStatus;
            this.secondSubmissionStatus = secondSubmissionStatus;
        }

        public List<String> steps() {
            return steps;
        }

        public String firstSubmissionStatus() {
            return firstSubmissionStatus;
        }

        public String secondSubmissionStatus() {
            return secondSubmissionStatus;
        }
    }

    public static final class CallbackResult {

        private final List<String> steps;
        private final String finalStatus;

        public CallbackResult(List<String> steps, String finalStatus) {
            this.steps = steps;
            this.finalStatus = finalStatus;
        }

        public List<String> steps() {
            return steps;
        }

        public String finalStatus() {
            return finalStatus;
        }
    }
}
