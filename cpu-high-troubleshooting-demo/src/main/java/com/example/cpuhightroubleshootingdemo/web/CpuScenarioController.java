package com.example.cpuhightroubleshootingdemo.web;

import com.example.cpuhightroubleshootingdemo.cpu.CpuHighTroubleshootingDemoService;
import com.example.cpuhightroubleshootingdemo.cpu.CpuScenarioRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/cpu")
public class CpuScenarioController {

    private final CpuScenarioRuntimeService cpuScenarioRuntimeService;

    public CpuScenarioController(CpuScenarioRuntimeService cpuScenarioRuntimeService) {
        this.cpuScenarioRuntimeService = cpuScenarioRuntimeService;
    }

    @GetMapping("/status")
    public CpuScenarioRuntimeService.ScenarioStatus status() {
        return cpuScenarioRuntimeService.currentStatus();
    }

    @GetMapping("/cases")
    public Map<String, Object> cases() {
        return cpuScenarioRuntimeService.scenarioCatalog();
    }

    @GetMapping("/playbook")
    public CpuHighTroubleshootingDemoService.DiagnosticPlaybook playbook() {
        return cpuScenarioRuntimeService.diagnosisPlaybook();
    }

    @PostMapping("/scenarios/{scenario}/start")
    public CpuScenarioRuntimeService.ScenarioStatus start(@PathVariable String scenario,
                                                          @RequestParam(required = false) Integer durationSeconds) {
        try {
            return cpuScenarioRuntimeService.startScenario(scenario, durationSeconds);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/scenarios/stop")
    public CpuScenarioRuntimeService.ScenarioStatus stop() {
        return cpuScenarioRuntimeService.stopScenario();
    }
}
