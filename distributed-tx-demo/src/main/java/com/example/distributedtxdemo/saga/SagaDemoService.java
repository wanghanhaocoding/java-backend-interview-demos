package com.example.distributedtxdemo.saga;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SagaDemoService {

    public SagaResult orchestrateWithCompensation(String requestNo, boolean failAtShippingStep) {
        List<String> steps = new ArrayList<>();
        List<String> compensations = new ArrayList<>();

        steps.add("step-1 freeze funds for order " + requestNo);
        steps.add("step-2 reserve inventory for order " + requestNo);

        if (failAtShippingStep) {
            steps.add("step-3 create shipment -> failed");
            compensations.add("compensate inventory reservation");
            compensations.add("compensate fund freeze");
            return new SagaResult(requestNo, false, steps, compensations);
        }

        steps.add("step-3 create shipment -> success");
        return new SagaResult(requestNo, true, steps, compensations);
    }

    public static final class SagaResult {

        private final String requestNo;
        private final boolean success;
        private final List<String> steps;
        private final List<String> compensations;

        public SagaResult(String requestNo, boolean success, List<String> steps, List<String> compensations) {
            this.requestNo = requestNo;
            this.success = success;
            this.steps = steps;
            this.compensations = compensations;
        }

        public String requestNo() {
            return requestNo;
        }

        public boolean success() {
            return success;
        }

        public List<String> steps() {
            return steps;
        }

        public List<String> compensations() {
            return compensations;
        }
    }
}
