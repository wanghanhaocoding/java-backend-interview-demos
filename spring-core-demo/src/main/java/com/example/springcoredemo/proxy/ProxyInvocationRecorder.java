package com.example.springcoredemo.proxy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProxyInvocationRecorder {

    private final List<String> events = new ArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
