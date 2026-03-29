package com.example.springcoredemo.lifecycle;

import com.example.springcoredemo.SpringCoreTeachingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleProbeTest {

    @Test
    void lifecycleCallbacksShouldFollowSpringOrder() {
        SpringApplication application = new SpringApplication(SpringCoreTeachingApplication.class);
        application.setDefaultProperties(Map.of("teaching.runner.enabled", "false"));

        ConfigurableApplicationContext context = application.run("--teaching.runner.enabled=false");
        LifecycleRecorder recorder = context.getBean(LifecycleRecorder.class);

        assertThat(recorder.snapshot()).containsExactly(
                "constructor",
                "postConstruct",
                "afterPropertiesSet"
        );

        context.close();

        List<String> finalEvents = recorder.snapshot();
        assertThat(finalEvents).endsWith("preDestroy", "destroy");
    }
}
