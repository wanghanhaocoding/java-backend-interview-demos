package com.example.cpuhightroubleshootingdemo.web;

import com.example.cpuhightroubleshootingdemo.cpu.CpuScenarioRuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "teaching.runner.enabled=false",
                "demo.scenario.auto-start=none",
                "demo.scenario.log-interval-seconds=60"
        }
)
class CpuScenarioControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private CpuScenarioRuntimeService cpuScenarioRuntimeService;

    @AfterEach
    void tearDown() {
        cpuScenarioRuntimeService.stopScenario();
    }

    @Test
    void shouldExposeRuntimeEndpoints() {
        ResponseEntity<Map> startResponse = testRestTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/api/cpu/scenarios/empty-scan/start?durationSeconds=0",
                null,
                Map.class
        );
        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(startResponse.getBody()).containsEntry("activeScenario", "empty-scan");

        ResponseEntity<Map> statusResponse = testRestTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/api/cpu/status",
                Map.class
        );
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).containsEntry("activeScenario", "empty-scan");

        ResponseEntity<Map> healthResponse = testRestTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/actuator/health",
                Map.class
        );
        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(healthResponse.getBody()).containsEntry("status", "UP");
        Map components = (Map) healthResponse.getBody().get("components");
        assertThat(components).containsKey("cpuScenario");
    }
}
