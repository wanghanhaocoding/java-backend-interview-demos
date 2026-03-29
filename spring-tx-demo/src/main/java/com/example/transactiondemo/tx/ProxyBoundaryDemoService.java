package com.example.transactiondemo.tx;

import org.springframework.stereotype.Service;

@Service
public class ProxyBoundaryDemoService {

    private final SelfInvocationDemoService selfInvocationDemoService;

    public ProxyBoundaryDemoService(SelfInvocationDemoService selfInvocationDemoService) {
        this.selfInvocationDemoService = selfInvocationDemoService;
    }

    public boolean invokeThroughProxy() {
        return selfInvocationDemoService.innerTransactional();
    }

    public boolean invokeInsideSameBean() {
        return selfInvocationDemoService.callInnerByThis();
    }
}
