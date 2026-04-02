package com.example.cpuhightroubleshootingdemo.cpu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class CpuHighTroubleshootingDemoTest {

    @Autowired
    private CpuHighTroubleshootingDemoService cpuHighTroubleshootingDemoService;

    @Test
    void shouldDescribeXtimerEmptyScanCase() {
        CpuHighTroubleshootingDemoService.XtimerEmptyScanCaseResult result =
                cpuHighTroubleshootingDemoService.xtimerEmptyScanCase();

        assertThat(result.signals().get("hotThread")).isEqualTo(XtimerEmptyScanCpuDemo.HOT_THREAD_NAME);
        assertThat(result.steps()).anyMatch(step -> step.contains("TriggerWorker"));
        assertThat(result.commands()).contains("top -Hp <pid>");
        assertThat(result.fixes()).anyMatch(fix -> fix.contains("empty scan"));
    }

    @Test
    void shouldDescribeXtimerFallbackStormCase() {
        CpuHighTroubleshootingDemoService.XtimerFallbackStormCaseResult result =
                cpuHighTroubleshootingDemoService.xtimerFallbackStormCase();

        assertThat(result.bucketAttempts()).containsKeys("2026-03-30 10:14_0", "2026-03-30 10:14_1", "2026-03-30 10:15_0");
        assertThat(result.bucketAttempts().values()).allMatch(attempts -> attempts > 2_000);
        assertThat(result.steps()).anyMatch(step -> step.contains("taskMapper.getTasksByTimeRange"));
        assertThat(result.fixes()).anyMatch(fix -> fix.contains("fallback"));
    }

    @Test
    void shouldProvideDiagnosisPlaybook() {
        CpuHighTroubleshootingDemoService.DiagnosticPlaybook playbook =
                cpuHighTroubleshootingDemoService.diagnosisPlaybook();

        assertThat(playbook.steps()).hasSize(6);
        assertThat(playbook.steps()).anyMatch(step -> step.contains("top -Hp"));
        assertThat(playbook.evidenceChecklist()).anyMatch(item -> item.contains("minuteBucketKey"));
    }
}
