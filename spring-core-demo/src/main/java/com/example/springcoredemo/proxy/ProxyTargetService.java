package com.example.springcoredemo.proxy;

import org.springframework.stereotype.Service;

@Service
public class ProxyTargetService {

    private final ProxyInvocationRecorder proxyInvocationRecorder;

    public ProxyTargetService(ProxyInvocationRecorder proxyInvocationRecorder) {
        this.proxyInvocationRecorder = proxyInvocationRecorder;
    }

    public String sayHello(String name) {
        proxyInvocationRecorder.record("target:" + name);
        return "hello, " + name;
    }
}
