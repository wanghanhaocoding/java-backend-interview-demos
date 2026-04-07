package com.example.probemqtroubleshootingdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProbeMqTroubleshootingDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProbeMqTroubleshootingDemoApplication.class, args);
    }
}
