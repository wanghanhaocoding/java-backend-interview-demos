package com.example.cpuhightroubleshootingdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public class CpuHighDemoProperties {

    private String nodeId = "cpu-node-a";

    private final Scenario scenario = new Scenario();

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public static class Scenario {

        private String autoStart = "none";

        private int durationSeconds = 0;

        private int logIntervalSeconds = 5;

        public String getAutoStart() {
            return autoStart;
        }

        public void setAutoStart(String autoStart) {
            this.autoStart = autoStart;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public int getLogIntervalSeconds() {
            return logIntervalSeconds;
        }

        public void setLogIntervalSeconds(int logIntervalSeconds) {
            this.logIntervalSeconds = logIntervalSeconds;
        }
    }
}
