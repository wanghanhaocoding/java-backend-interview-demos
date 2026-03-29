package com.example.orderobservabilitypatterndemo.observability;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObservabilityDemoService {

    private final List<String> logs = new ArrayList<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final Map<String, Long> lastLatencies = new LinkedHashMap<>();
    private int traceSequence;

    public void reset() {
        logs.clear();
        counters.clear();
        lastLatencies.clear();
        traceSequence = 0;
    }

    public String startTrace(String operation) {
        String traceId = String.format("trace-%03d", ++traceSequence);
        log(traceId, operation, "start");
        return traceId;
    }

    public void log(String traceId, String span, String message) {
        logs.add("traceId=" + traceId + " span=" + span + " message=" + message);
    }

    public void increment(String metric) {
        counters.merge(metric, 1L, Long::sum);
    }

    public void recordLatency(String metric, long millis) {
        lastLatencies.put(metric, millis);
    }

    public List<String> logs() {
        return List.copyOf(logs);
    }

    public long counter(String metric) {
        return counters.getOrDefault(metric, 0L);
    }

    public long latency(String metric) {
        return lastLatencies.getOrDefault(metric, 0L);
    }
}
