package com.example.springcoredemo.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class ProxyTargetServiceTest {

    @Autowired
    private ProxyTargetService proxyTargetService;

    @Autowired
    private ProxyInvocationRecorder proxyInvocationRecorder;

    @BeforeEach
    void setUp() {
        proxyInvocationRecorder.clear();
    }

    @Test
    void aspectShouldWrapTargetInvocation() {
        String result = proxyTargetService.sayHello("alice");

        assertThat(result).isEqualTo("hello, alice");
        assertThat(proxyInvocationRecorder.snapshot()).containsExactly(
                "before:sayHello",
                "target:alice",
                "after:sayHello"
        );
    }
}
