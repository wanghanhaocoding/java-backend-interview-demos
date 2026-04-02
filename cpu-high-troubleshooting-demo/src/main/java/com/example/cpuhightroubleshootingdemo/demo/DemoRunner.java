package com.example.cpuhightroubleshootingdemo.demo;

import com.example.cpuhightroubleshootingdemo.cpu.CpuHighTroubleshootingDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final CpuHighTroubleshootingDemoService cpuHighTroubleshootingDemoService;

    public DemoRunner(CpuHighTroubleshootingDemoService cpuHighTroubleshootingDemoService) {
        this.cpuHighTroubleshootingDemoService = cpuHighTroubleshootingDemoService;
    }

    @Override
    public void run(String... args) {
        CpuHighTroubleshootingDemoService.XtimerEmptyScanCaseResult emptyScanCase =
                cpuHighTroubleshootingDemoService.xtimerEmptyScanCase();
        printTitle("1. xtimer 空 minuteBucketKey 扫描把 CPU 打高");
        emptyScanCase.steps().forEach(System.out::println);
        System.out.println("signals = " + emptyScanCase.signals());
        System.out.println("commands = " + emptyScanCase.commands());
        System.out.println("fixes = " + emptyScanCase.fixes());

        CpuHighTroubleshootingDemoService.XtimerFallbackStormCaseResult fallbackStormCase =
                cpuHighTroubleshootingDemoService.xtimerFallbackStormCase();
        printTitle("2. xtimer DB fallback 查询风暴");
        fallbackStormCase.steps().forEach(System.out::println);
        System.out.println("bucketAttempts = " + fallbackStormCase.bucketAttempts());
        System.out.println("commands = " + fallbackStormCase.commands());
        System.out.println("fixes = " + fallbackStormCase.fixes());

        CpuHighTroubleshootingDemoService.DiagnosticPlaybook playbook =
                cpuHighTroubleshootingDemoService.diagnosisPlaybook();
        printTitle("3. CPU 标高排查 SOP");
        playbook.steps().forEach(System.out::println);
        System.out.println("evidence = " + playbook.evidenceChecklist());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
