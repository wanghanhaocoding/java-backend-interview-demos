package com.example.transactiondemo.ioc.scan;

import org.springframework.stereotype.Service;

@Service
public class ScannedGreetingService {

    private final ScannedGreetingRepository greetingRepository;

    public ScannedGreetingService(ScannedGreetingRepository greetingRepository) {
        this.greetingRepository = greetingRepository;
    }

    public String describeRegistrationFlow() {
        return greetingRepository.registrationMessage();
    }
}
