package com.example.springcoredemo.demo;

import com.example.springcoredemo.lifecycle.LifecycleRecorder;
import com.example.springcoredemo.proxy.ProxyInvocationRecorder;
import com.example.springcoredemo.proxy.ProxyTargetService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final LifecycleRecorder lifecycleRecorder;
    private final ProxyTargetService proxyTargetService;
    private final ProxyInvocationRecorder proxyInvocationRecorder;

    public DemoRunner(LifecycleRecorder lifecycleRecorder,
                      ProxyTargetService proxyTargetService,
                      ProxyInvocationRecorder proxyInvocationRecorder) {
        this.lifecycleRecorder = lifecycleRecorder;
        this.proxyTargetService = proxyTargetService;
        this.proxyInvocationRecorder = proxyInvocationRecorder;
    }

    @Override
    public void run(String... args) {
        printTitle("1. Bean 生命周期");
        lifecycleRecorder.snapshot().forEach(System.out::println);

        printTitle("2. AOP 代理");
        proxyInvocationRecorder.clear();
        System.out.println(proxyTargetService.sayHello("spring"));
        proxyInvocationRecorder.snapshot().forEach(System.out::println);
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
