package com.example.probemqtroubleshootingdemo.mq;

import com.example.probemqtroubleshootingdemo.config.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class InMemoryMqConsumerService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMqConsumerService.class);

    private final DemoProperties demoProperties;

    private final BlockingQueue<String> queue;

    private final AtomicLong offeredCount = new AtomicLong();

    private final AtomicLong consumedCount = new AtomicLong();

    private final AtomicLong messageSequence = new AtomicLong();

    private final AtomicReference<Instant> lastConsumedAt = new AtomicReference<>();

    private final Deque<String> recentMessages = new ConcurrentLinkedDeque<>();

    private final Deque<String> recentConsumerThreads = new ConcurrentLinkedDeque<>();

    private volatile boolean running;

    private ThreadPoolExecutor consumerExecutor;

    private ScheduledExecutorService producerExecutor;

    public InMemoryMqConsumerService(DemoProperties demoProperties) {
        this.demoProperties = demoProperties;
        this.queue = new LinkedBlockingQueue<>(demoProperties.getMq().getQueueCapacity());
    }

    @Override
    public synchronized void afterPropertiesSet() {
        start();
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        consumerExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                demoProperties.getMq().getConsumerCount(),
                namedFactory(demoProperties.getNodeId() + "-mq-consumer-")
        );
        for (int i = 0; i < demoProperties.getMq().getConsumerCount(); i++) {
            consumerExecutor.submit(this::consumeLoop);
        }

        if (demoProperties.getMq().isAutoProduce()) {
            producerExecutor = Executors.newSingleThreadScheduledExecutor(
                    namedFactory(demoProperties.getNodeId() + "-mq-producer-")
            );
            producerExecutor.scheduleAtFixedRate(
                    () -> enqueue("tick-" + messageSequence.incrementAndGet()),
                    0L,
                    demoProperties.getMq().getProduceIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public synchronized void stop() {
        running = false;
        if (producerExecutor != null) {
            producerExecutor.shutdownNow();
            producerExecutor = null;
        }
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
            consumerExecutor = null;
        }
    }

    @Override
    public void destroy() {
        stop();
    }

    public void enqueue(String payload) {
        boolean accepted = queue.offer(payload);
        if (accepted) {
            long offered = offeredCount.incrementAndGet();
            trim(recentMessages, "offered:" + payload);
            log.info("[{}] offered in-memory MQ message={} offeredCount={}",
                    demoProperties.getNodeId(), payload, offered);
            return;
        }

        log.warn("[{}] dropped in-memory MQ message={} queueSize={} capacity={}",
                demoProperties.getNodeId(), payload, queue.size(), demoProperties.getMq().getQueueCapacity());
    }

    public boolean awaitConsumedAtLeast(long targetCount, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (consumedCount.get() >= targetCount) {
                return true;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(50L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return consumedCount.get() >= targetCount;
    }

    public MqSnapshot snapshot() {
        return new MqSnapshot(
                demoProperties.getNodeId(),
                queue.size(),
                offeredCount.get(),
                consumedCount.get(),
                lastConsumedAt.get(),
                new ArrayList<>(recentMessages),
                new ArrayList<>(recentConsumerThreads)
        );
    }

    private void consumeLoop() {
        while (running || !queue.isEmpty()) {
            try {
                String payload = queue.poll(250L, TimeUnit.MILLISECONDS);
                if (payload == null) {
                    continue;
                }
                long consumed = consumedCount.incrementAndGet();
                lastConsumedAt.set(Instant.now());
                String threadName = Thread.currentThread().getName();
                trim(recentMessages, "consumed:" + payload);
                trim(recentConsumerThreads, threadName);
                log.info("[{}] mq consumer thread={} consumed={} consumedCount={}",
                        demoProperties.getNodeId(), threadName, payload, consumed);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private ThreadFactory namedFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private void trim(Deque<String> deque, String value) {
        deque.addFirst(value);
        while (deque.size() > 6) {
            deque.removeLast();
        }
    }

    public static final class MqSnapshot {

        private final String nodeId;

        private final int queueSize;

        private final long offeredCount;

        private final long consumedCount;

        private final Instant lastConsumedAt;

        private final List<String> recentMessages;

        private final List<String> recentConsumerThreads;

        public MqSnapshot(String nodeId,
                          int queueSize,
                          long offeredCount,
                          long consumedCount,
                          Instant lastConsumedAt,
                          List<String> recentMessages,
                          List<String> recentConsumerThreads) {
            this.nodeId = nodeId;
            this.queueSize = queueSize;
            this.offeredCount = offeredCount;
            this.consumedCount = consumedCount;
            this.lastConsumedAt = lastConsumedAt;
            this.recentMessages = Collections.unmodifiableList(recentMessages);
            this.recentConsumerThreads = Collections.unmodifiableList(recentConsumerThreads);
        }

        public String getNodeId() {
            return nodeId;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public long getOfferedCount() {
            return offeredCount;
        }

        public long getConsumedCount() {
            return consumedCount;
        }

        public Instant getLastConsumedAt() {
            return lastConsumedAt;
        }

        public List<String> getRecentMessages() {
            return recentMessages;
        }

        public List<String> getRecentConsumerThreads() {
            return recentConsumerThreads;
        }
    }
}
