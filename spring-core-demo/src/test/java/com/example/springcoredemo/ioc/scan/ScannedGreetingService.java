package com.example.springcoredemo.ioc.scan;

import org.springframework.stereotype.Service;

@Service
public class ScannedGreetingService {

    public String describeRegistrationFlow() {
        return "component scan -> BeanDefinition -> BeanFactory -> bean instance";
    }
}
