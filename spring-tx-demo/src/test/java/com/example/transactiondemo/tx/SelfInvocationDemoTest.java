package com.example.transactiondemo.tx;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SelfInvocationDemoTest {

    @Autowired
    private ProxyBoundaryDemoService proxyBoundaryDemoService;

    @Test
    void invocationThroughProxyActivatesTransaction() {
        assertThat(proxyBoundaryDemoService.invokeThroughProxy()).isTrue();
    }

    @Test
    void selfInvocationBypassesTransactionalProxy() {
        assertThat(proxyBoundaryDemoService.invokeInsideSameBean()).isFalse();
    }
}
