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
        CpuHighTroubleshootingDemoService.BusySpinCaseResult busySpinCase =
                cpuHighTroubleshootingDemoService.scheduleCenterBusySpinCase();
        printTitle("1. ScheduleCenter 空转扫描把 CPU 打高");
        busySpinCase.steps().forEach(System.out::println);
        System.out.println("signals = " + busySpinCase.signals());
        System.out.println("commands = " + busySpinCase.commands());
        System.out.println("fixes = " + busySpinCase.fixes());

        CpuHighTroubleshootingDemoService.RetryStormCaseResult retryStormCase =
                cpuHighTroubleshootingDemoService.asyncJobRetryStormCase();
        printTitle("2. AsyncJobCenter 失败重试风暴");
        retryStormCase.steps().forEach(System.out::println);
        System.out.println("jobAttempts = " + retryStormCase.jobAttempts());
        System.out.println("commands = " + retryStormCase.commands());
        System.out.println("fixes = " + retryStormCase.fixes());

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
