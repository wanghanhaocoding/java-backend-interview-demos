package com.example.cpuhightroubleshootingdemo.demo;

import com.example.cpuhightroubleshootingdemo.config.CpuHighDemoProperties;
import com.example.cpuhightroubleshootingdemo.cpu.CpuHighTroubleshootingDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final CpuHighTroubleshootingDemoService cpuHighTroubleshootingDemoService;

    private final CpuHighDemoProperties cpuHighDemoProperties;

    public DemoRunner(CpuHighTroubleshootingDemoService cpuHighTroubleshootingDemoService,
                      CpuHighDemoProperties cpuHighDemoProperties) {
        this.cpuHighTroubleshootingDemoService = cpuHighTroubleshootingDemoService;
        this.cpuHighDemoProperties = cpuHighDemoProperties;
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

        printTitle("4. 常驻服务模式");
        System.out.println("当前节点 = " + cpuHighDemoProperties.getNodeId() + ", 默认端口 = 8080");
        System.out.println("status: curl -s http://127.0.0.1:8080/api/cpu/status");
        System.out.println("cases: curl -s http://127.0.0.1:8080/api/cpu/cases");
        System.out.println("start empty-scan: curl -s -X POST \"http://127.0.0.1:8080/api/cpu/scenarios/empty-scan/start?durationSeconds=0\"");
        System.out.println("start fallback-storm: curl -s -X POST \"http://127.0.0.1:8080/api/cpu/scenarios/fallback-storm/start?durationSeconds=0\"");
        System.out.println("stop: curl -s -X POST http://127.0.0.1:8080/api/cpu/scenarios/stop");
        System.out.println("health: curl -s http://127.0.0.1:8080/actuator/health");
        System.out.println("autoStart = " + cpuHighDemoProperties.getScenario().getAutoStart()
                + ", durationSeconds = " + cpuHighDemoProperties.getScenario().getDurationSeconds());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
