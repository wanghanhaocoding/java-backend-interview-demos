package com.example.probemqtroubleshootingdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    private String nodeId = "node-a";

    private final Fault fault = new Fault();

    private final Readiness readiness = new Readiness();

    private final Mq mq = new Mq();

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Fault getFault() {
        return fault;
    }

    public Readiness getReadiness() {
        return readiness;
    }

    public Mq getMq() {
        return mq;
    }

    public static class Fault {

        private boolean enabled;

        private int blockSeconds = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBlockSeconds() {
            return blockSeconds;
        }

        public void setBlockSeconds(int blockSeconds) {
            this.blockSeconds = blockSeconds;
        }
    }

    public static class Readiness {

        private int downThreshold = 4;

        public int getDownThreshold() {
            return downThreshold;
        }

        public void setDownThreshold(int downThreshold) {
            this.downThreshold = downThreshold;
        }
    }

    public static class Mq {

        private int consumerCount = 2;

        private int queueCapacity = 256;

        private long produceIntervalMs = 1000L;

        private boolean autoProduce = true;

        public int getConsumerCount() {
            return consumerCount;
        }

        public void setConsumerCount(int consumerCount) {
            this.consumerCount = consumerCount;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public long getProduceIntervalMs() {
            return produceIntervalMs;
        }

        public void setProduceIntervalMs(long produceIntervalMs) {
            this.produceIntervalMs = produceIntervalMs;
        }

        public boolean isAutoProduce() {
            return autoProduce;
        }

        public void setAutoProduce(boolean autoProduce) {
            this.autoProduce = autoProduce;
        }
    }
}
