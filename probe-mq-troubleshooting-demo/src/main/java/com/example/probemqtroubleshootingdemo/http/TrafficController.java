package com.example.probemqtroubleshootingdemo.http;

import com.example.probemqtroubleshootingdemo.node.NodeScenarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class TrafficController {

    private static final Logger log = LoggerFactory.getLogger(TrafficController.class);

    private final NodeScenarioService nodeScenarioService;

    private final RequestPressureTracker requestPressureTracker;

    public TrafficController(NodeScenarioService nodeScenarioService,
                             RequestPressureTracker requestPressureTracker) {
        this.nodeScenarioService = nodeScenarioService;
        this.requestPressureTracker = requestPressureTracker;
    }

    @GetMapping("/api/traffic")
    public ResponseEntity<Map<String, Object>> traffic(@RequestParam(defaultValue = "demo-order") String businessKey) {
        boolean blocked = nodeScenarioService.shouldBlockBusinessRequest();
        try (RequestPressureTracker.RequestToken ignored = requestPressureTracker.beginRequest(blocked)) {
            if (blocked) {
                log.info("[{}] blocking HTTP request businessKey={} thread={} blockSeconds={}",
                        nodeScenarioService.getNodeId(),
                        businessKey,
                        Thread.currentThread().getName(),
                        nodeScenarioService.getBlockSeconds());
                TimeUnit.SECONDS.sleep(nodeScenarioService.getBlockSeconds());
            } else {
                log.info("[{}] served healthy HTTP request businessKey={} thread={}",
                        nodeScenarioService.getNodeId(), businessKey, Thread.currentThread().getName());
            }

            Map<String, Object> response = new LinkedHashMap<>(nodeScenarioService.evidence());
            response.put("businessKey", businessKey);
            response.put("blocked", blocked);
            response.put("threadName", Thread.currentThread().getName());
            return ResponseEntity.ok(response);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("nodeId", nodeScenarioService.getNodeId());
            response.put("error", "request interrupted");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/api/fault/enable")
    public NodeScenarioService.FaultState enableFault(@RequestParam(required = false) Integer blockSeconds) {
        NodeScenarioService.FaultState state = nodeScenarioService.enableFault(blockSeconds);
        log.info("[{}] enabled fault blockSeconds={}", state.getNodeId(), state.getBlockSeconds());
        return state;
    }

    @PostMapping("/api/fault/disable")
    public NodeScenarioService.FaultState disableFault() {
        NodeScenarioService.FaultState state = nodeScenarioService.disableFault();
        log.info("[{}] disabled fault", state.getNodeId());
        return state;
    }

    @GetMapping("/api/evidence")
    public Map<String, Object> evidence() {
        return nodeScenarioService.evidence();
    }
}
