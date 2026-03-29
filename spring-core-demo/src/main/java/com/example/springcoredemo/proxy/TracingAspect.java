package com.example.springcoredemo.proxy;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TracingAspect {

    private final ProxyInvocationRecorder proxyInvocationRecorder;

    public TracingAspect(ProxyInvocationRecorder proxyInvocationRecorder) {
        this.proxyInvocationRecorder = proxyInvocationRecorder;
    }

    @Around("execution(* com.example.springcoredemo.proxy.ProxyTargetService.sayHello(..))")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        proxyInvocationRecorder.record("before:" + joinPoint.getSignature().getName());
        try {
            Object result = joinPoint.proceed();
            proxyInvocationRecorder.record("after:" + joinPoint.getSignature().getName());
            return result;
        } catch (Throwable throwable) {
            proxyInvocationRecorder.record("error:" + joinPoint.getSignature().getName());
            throw throwable;
        }
    }
}
