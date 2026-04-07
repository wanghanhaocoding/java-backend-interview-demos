package com.example.probemqtroubleshootingdemo.demo;

import com.example.probemqtroubleshootingdemo.node.NodeScenarioService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final NodeScenarioService nodeScenarioService;

    public DemoRunner(NodeScenarioService nodeScenarioService) {
        this.nodeScenarioService = nodeScenarioService;
    }

    @Override
    public void run(String... args) {
        printTitle("1. probe unhealthy 但 MQ consumer 继续运行");
        nodeScenarioService.startupNotes().forEach(System.out::println);

        printTitle("2. 直接可用的 Linux 演练命令");
        nodeScenarioService.startupCommands().forEach(System.out::println);

        printTitle("3. 双节点启动建议");
        System.out.println("node-a: mvn spring-boot:run -Dspring-boot.run.arguments=\"--server.port=8080 --demo.node-id=node-a\"");
        System.out.println("node-b: mvn spring-boot:run -Dspring-boot.run.arguments=\"--server.port=8081 --demo.node-id=node-b --demo.fault.enabled=true --demo.fault.block-seconds=25\"");
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
