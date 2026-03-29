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

    public record SagaResult(String requestNo, boolean success, List<String> steps, List<String> compensations) {
    }
}
