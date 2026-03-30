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
    void shouldDescribeScheduleCenterBusySpinCase() {
        CpuHighTroubleshootingDemoService.BusySpinCaseResult result =
                cpuHighTroubleshootingDemoService.scheduleCenterBusySpinCase();

        assertThat(result.signals().get("hotThread")).isEqualTo(BusySpinScheduleScannerDemo.HOT_THREAD_NAME);
        assertThat(result.steps()).anyMatch(step -> step.contains("空扫"));
        assertThat(result.commands()).contains("top -Hp <pid>");
        assertThat(result.fixes()).anyMatch(fix -> fix.contains("退避"));
    }

    @Test
    void shouldDescribeRetryStormCase() {
        CpuHighTroubleshootingDemoService.RetryStormCaseResult result =
                cpuHighTroubleshootingDemoService.asyncJobRetryStormCase();

        assertThat(result.jobAttempts()).containsKeys("AJC-RETRY-1001", "AJC-RETRY-1002", "AJC-RETRY-1003");
        assertThat(result.jobAttempts().values()).allMatch(attempts -> attempts > 2_000);
        assertThat(result.steps()).anyMatch(step -> step.contains("order_time"));
        assertThat(result.fixes()).anyMatch(fix -> fix.contains("指数退避"));
    }

    @Test
    void shouldProvideDiagnosisPlaybook() {
        CpuHighTroubleshootingDemoService.DiagnosticPlaybook playbook =
                cpuHighTroubleshootingDemoService.diagnosisPlaybook();

        assertThat(playbook.steps()).hasSize(6);
        assertThat(playbook.steps()).anyMatch(step -> step.contains("top -Hp"));
        assertThat(playbook.evidenceChecklist()).anyMatch(item -> item.contains("火焰图"));
    }
}
