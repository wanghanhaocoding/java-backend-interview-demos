package com.example.schedulecentercallbacktimeoutdemo.demo;

import com.example.schedulecentercallbacktimeoutdemo.callback.ScheduleCenterCallbackTimeoutDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final ScheduleCenterCallbackTimeoutDemoService scheduleCenterCallbackTimeoutDemoService;

    public DemoRunner(ScheduleCenterCallbackTimeoutDemoService scheduleCenterCallbackTimeoutDemoService) {
        this.scheduleCenterCallbackTimeoutDemoService = scheduleCenterCallbackTimeoutDemoService;
    }

    @Override
    public void run(String... args) {
        ScheduleCenterCallbackTimeoutDemoService.CallbackTimeoutCaseStudy result =
                scheduleCenterCallbackTimeoutDemoService.callbackTimeoutCaseStudy();

        printTitle("1. callback timeout 的事故链路");
        result.incidentTimeline().forEach(System.out::println);

        printTitle("2. 当前案例的关键口径");
        System.out.println(result.runtimeProfile());
        System.out.println(result.pressureMetrics());

        printTitle("3. 真实 xtimer 代码锚点");
        result.codeAnchors().forEach(System.out::println);

        printTitle("4. delay propagation stages");
        result.propagationStages().forEach(System.out::println);

        printTitle("5. 现场证据");
        result.evidenceSignals().forEach(System.out::println);

        printTitle("6. 边界与处理");
        result.scopeBoundary().forEach(System.out::println);
        result.mitigationActions().forEach(System.out::println);
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
