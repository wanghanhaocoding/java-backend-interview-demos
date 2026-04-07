package com.example.cpuhightroubleshootingdemo;

import com.example.cpuhightroubleshootingdemo.config.CpuHighDemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CpuHighDemoProperties.class)
public class CpuHighTroubleshootingTeachingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CpuHighTroubleshootingTeachingApplication.class, args);
    }
}
