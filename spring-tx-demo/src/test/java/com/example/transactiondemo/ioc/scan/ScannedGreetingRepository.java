package com.example.transactiondemo.ioc.scan;

import org.springframework.stereotype.Repository;

@Repository
public class ScannedGreetingRepository {

    public String registrationMessage() {
        return "component scan -> BeanDefinition -> BeanFactory -> bean instance";
    }
}
