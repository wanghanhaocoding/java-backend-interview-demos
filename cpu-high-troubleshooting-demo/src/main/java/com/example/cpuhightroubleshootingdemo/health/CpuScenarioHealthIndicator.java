package com.example.cpuhightroubleshootingdemo.health;

import com.example.cpuhightroubleshootingdemo.cpu.CpuScenarioRuntimeService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CpuScenarioHealthIndicator implements HealthIndicator {

    private final CpuScenarioRuntimeService cpuScenarioRuntimeService;

    public CpuScenarioHealthIndicator(CpuScenarioRuntimeService cpuScenarioRuntimeService) {
        this.cpuScenarioRuntimeService = cpuScenarioRuntimeService;
    }

    @Override
    public Health health() {
        CpuScenarioRuntimeService.ScenarioStatus status = cpuScenarioRuntimeService.currentStatus();
        Health.Builder builder = status.getFailure() == null ? Health.up() : Health.down();
        return builder
                .withDetail("nodeId", status.getNodeId())
                .withDetail("activeScenario", status.getActiveScenario())
                .withDetail("running", status.isRunning())
                .withDetail("hotThreadName", status.getHotThreadName())
                .withDetail("hotThreadState", status.getHotThreadState())
                .withDetail("metrics", status.getMetrics())
                .build();
    }
}
