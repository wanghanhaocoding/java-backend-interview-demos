package com.example.mqcacheidempotencydemo.mq;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MessageReliabilityDemoService {

    private final Map<String, OrderRecord> orderStore = new LinkedHashMap<>();
    private final Map<String, OutboxEvent> outboxStore = new LinkedHashMap<>();
    private final Set<String> consumerIdempotentTable = new LinkedHashSet<>();

    public void reset() {
        orderStore.clear();
        outboxStore.clear();
        consumerIdempotentTable.clear();
    }

    public EndToEndResult endToEndDemo(String orderId, BigDecimal amount) {
        reset();
        List<String> steps = new ArrayList<>();

        OrderRecord order = new OrderRecord(orderId, amount, "CREATED");
        orderStore.put(orderId, order);

        OutboxEvent event = new OutboxEvent("EVT-" + orderId, orderId);
        outboxStore.put(orderId, event);
        steps.add("1. 业务订单和 outbox 事件在同一个本地事务里写入成功");

        event.deliveryAttempts++;
        event.status = "WAIT_RETRY";
        steps.add("2. 第一次投递失败，事件状态改成 WAIT_RETRY，等待后台重试");

        event.deliveryAttempts++;
        event.status = "SENT";
        steps.add("3. 后台重试再次投递，Broker 成功接收消息");

        consume("order-consumer", orderId, steps);
        consume("order-consumer", orderId, steps);

        return new EndToEndResult(
                steps,
                event.deliveryAttempts,
                event.status,
                countConsumerRecords("order-consumer", orderId)
        );
    }

    public int countConsumerRecords(String consumer, String businessKey) {
        return consumerIdempotentTable.contains(consumer + ":" + businessKey) ? 1 : 0;
    }

    private void consume(String consumer, String businessKey, List<String> steps) {
        String dedupeKey = consumer + ":" + businessKey;
        if (consumerIdempotentTable.add(dedupeKey)) {
            steps.add("4. 消费端第一次收到消息，写入幂等表并执行业务");
            return;
        }
        steps.add("5. 消费端收到重复消息，命中幂等表后直接忽略");
    }

    public record EndToEndResult(
            List<String> steps,
            int deliveryAttempts,
            String finalEventStatus,
            int consumerRows
    ) {
    }

    private static final class OrderRecord {

        private final String orderId;
        private final BigDecimal amount;
        private final String status;

        private OrderRecord(String orderId, BigDecimal amount, String status) {
            this.orderId = orderId;
            this.amount = amount;
            this.status = status;
        }
    }

    private static final class OutboxEvent {

        private final String eventId;
        private final String businessKey;
        private String status = "NEW";
        private int deliveryAttempts;

        private OutboxEvent(String eventId, String businessKey) {
            this.eventId = eventId;
            this.businessKey = businessKey;
        }
    }
}
