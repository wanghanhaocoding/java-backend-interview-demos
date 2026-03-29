package com.example.redislockdemo.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ExecutionTracker {

    private final AtomicInteger counter = new AtomicInteger();

    public int incrementAndGet() {
        return counter.incrementAndGet();
    }

    public int get() {
        return counter.get();
    }

    public void reset() {
        counter.set(0);
    }
}
