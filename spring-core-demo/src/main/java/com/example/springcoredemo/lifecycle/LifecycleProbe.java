package com.example.springcoredemo.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class LifecycleProbe implements InitializingBean, DisposableBean {

    private final LifecycleRecorder lifecycleRecorder;

    public LifecycleProbe(LifecycleRecorder lifecycleRecorder) {
        this.lifecycleRecorder = lifecycleRecorder;
        this.lifecycleRecorder.record("constructor");
    }

    @PostConstruct
    public void onPostConstruct() {
        lifecycleRecorder.record("postConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        lifecycleRecorder.record("afterPropertiesSet");
    }

    @PreDestroy
    public void onPreDestroy() {
        lifecycleRecorder.record("preDestroy");
    }

    @Override
    public void destroy() {
        lifecycleRecorder.record("destroy");
    }
}
