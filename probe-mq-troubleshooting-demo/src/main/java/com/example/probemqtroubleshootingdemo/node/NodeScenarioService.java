package com.example.probemqtroubleshootingdemo.node;

import com.example.probemqtroubleshootingdemo.config.DemoProperties;
import com.example.probemqtroubleshootingdemo.http.RequestPressureTracker;
import com.example.probemqtroubleshootingdemo.mq.InMemoryMqConsumerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NodeScenarioService {

    private final DemoProperties demoProperties;

    private final RequestPressureTracker requestPressureTracker;

    private final InMemoryMqConsumerService inMemoryMqConsumerService;

    private final AtomicBoolean faultEnabled;

    private final AtomicInteger blockSeconds;

    private final int serverPort;

    public NodeScenarioService(DemoProperties demoProperties,
                               RequestPressureTracker requestPressureTracker,
                               InMemoryMqConsumerService inMemoryMqConsumerService,
                               @Value("${server.port:8080}") int serverPort) {
        this.demoProperties = demoProperties;
        this.requestPressureTracker = requestPressureTracker;
        this.inMemoryMqConsumerService = inMemoryMqConsumerService;
        this.serverPort = serverPort;
        this.faultEnabled = new AtomicBoolean(demoProperties.getFault().isEnabled());
        this.blockSeconds = new AtomicInteger(demoProperties.getFault().getBlockSeconds());
    }

    public String getNodeId() {
        return demoProperties.getNodeId();
    }

    public int getReadinessDownThreshold() {
        return demoProperties.getReadiness().getDownThreshold();
    }

    public boolean isFaultEnabled() {
        return faultEnabled.get();
    }

    public int getBlockSeconds() {
        return blockSeconds.get();
    }

    public boolean shouldBlockBusinessRequest() {
        return faultEnabled.get();
    }

    public FaultState enableFault(Integer requestedBlockSeconds) {
        faultEnabled.set(true);
        if (requestedBlockSeconds != null && requestedBlockSeconds > 0) {
            blockSeconds.set(requestedBlockSeconds);
        }
        return new FaultState(demoProperties.getNodeId(), true, blockSeconds.get(), Instant.now());
    }

    public FaultState disableFault() {
        faultEnabled.set(false);
        return new FaultState(demoProperties.getNodeId(), false, blockSeconds.get(), Instant.now());
    }

    public Map<String, Object> evidence() {
        RequestPressureTracker.PressureSnapshot pressure = requestPressureTracker.snapshot();
        InMemoryMqConsumerService.MqSnapshot mq = inMemoryMqConsumerService.snapshot();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("nodeId", demoProperties.getNodeId());
        response.put("port", serverPort);
        response.put("faultEnabled", faultEnabled.get());
        response.put("blockSeconds", blockSeconds.get());
        response.put("readinessDownThreshold", demoProperties.getReadiness().getDownThreshold());
        response.put("activeBlockedRequests", pressure.getActiveBlockedRequests());
        response.put("maxBlockedRequests", pressure.getMaxBlockedRequests());
        response.put("totalRequests", pressure.getTotalRequests());
        response.put("totalBlockedRequests", pressure.getTotalBlockedRequests());
        response.put("lastBlockingThread", pressure.getLastBlockingThread());
        response.put("lastBlockedAt", pressure.getLastBlockedAt());
        response.put("mqOfferedCount", mq.getOfferedCount());
        response.put("mqConsumedCount", mq.getConsumedCount());
        response.put("mqQueueSize", mq.getQueueSize());
        response.put("recentMessages", mq.getRecentMessages());
        response.put("recentConsumerThreads", mq.getRecentConsumerThreads());
        return response;
    }

    public List<String> startupNotes() {
        return Arrays.asList(
                "当前节点 = " + demoProperties.getNodeId() + ", port = " + serverPort,
                "故障模式 = " + faultEnabled.get() + ", blockSeconds = " + blockSeconds.get(),
                "readiness DOWN 阈值 = activeBlockedRequests >= " + demoProperties.getReadiness().getDownThreshold(),
                "MQ 消费线程前缀 = " + demoProperties.getNodeId() + "-mq-consumer-*",
                "HTTP 故障入口 = /api/traffic, 观察入口 = /api/evidence, 健康检查 = /actuator/health/readiness"
        );
    }

    public List<String> startupCommands() {
        String baseUrl = "http://127.0.0.1:" + serverPort;
        int threshold = demoProperties.getReadiness().getDownThreshold();
        return Arrays.asList(
                "curl -s " + baseUrl + "/actuator/health/readiness",
                "curl -s " + baseUrl + "/api/evidence",
                "curl -s -X POST \"" + baseUrl + "/api/fault/enable?blockSeconds=" + blockSeconds.get() + "\"",
                "seq 1 " + threshold + " | xargs -I{} -P " + threshold
                        + " curl -s \"" + baseUrl + "/api/traffic?businessKey=order-{}\"",
                "jps -l | grep probe-mq-troubleshooting-demo"
        );
    }

    public static final class FaultState {

        private final String nodeId;

        private final boolean faultEnabled;

        private final int blockSeconds;

        private final Instant changedAt;

        public FaultState(String nodeId, boolean faultEnabled, int blockSeconds, Instant changedAt) {
            this.nodeId = nodeId;
            this.faultEnabled = faultEnabled;
            this.blockSeconds = blockSeconds;
            this.changedAt = changedAt;
        }

        public String getNodeId() {
            return nodeId;
        }

        public boolean isFaultEnabled() {
            return faultEnabled;
        }

        public int getBlockSeconds() {
            return blockSeconds;
        }

        public Instant getChangedAt() {
            return changedAt;
        }
    }
}
