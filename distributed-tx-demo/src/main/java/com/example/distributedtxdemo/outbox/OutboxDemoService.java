package com.example.distributedtxdemo.outbox;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OutboxDemoService {

    private final JdbcTemplate jdbcTemplate;

    public OutboxDemoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void createOrderAndStageEvent(String requestNo, BigDecimal amount) {
        jdbcTemplate.update(
                "insert into demo_order(request_no, status, amount, created_at) values (?, 'CREATED', ?, current_timestamp)",
                requestNo,
                amount
        );
        jdbcTemplate.update(
                "insert into outbox_event(event_key, topic, payload, status, retry_count, created_at, updated_at) values (?, 'order-created', ?, 'NEW', 0, current_timestamp, current_timestamp)",
                eventKey(requestNo),
                "orderNo=" + requestNo + ",amount=" + amount
        );
    }

    public DispatchResult dispatchPendingEvents(boolean failFirstAttempt) {
        List<OutboxEvent> events = jdbcTemplate.query(
                "select id, event_key, status, retry_count from outbox_event where status in ('NEW', 'FAILED') order by id",
                (rs, rowNum) -> new OutboxEvent(
                        rs.getLong("id"),
                        rs.getString("event_key"),
                        rs.getString("status"),
                        rs.getInt("retry_count")
                )
        );

        int sent = 0;
        int failed = 0;
        for (OutboxEvent event : events) {
            if (failFirstAttempt && event.retryCount() == 0) {
                jdbcTemplate.update(
                        "update outbox_event set status = 'FAILED', retry_count = retry_count + 1, updated_at = current_timestamp where id = ?",
                        event.id()
                );
                failed++;
                continue;
            }

            jdbcTemplate.update(
                    "update outbox_event set status = 'SENT', updated_at = current_timestamp where id = ?",
                    event.id()
            );
            sent++;
        }
        return new DispatchResult(sent, failed);
    }

    public ConsumeResult consumeSentEvents(String consumerName) {
        List<String> eventKeys = jdbcTemplate.query(
                "select event_key from outbox_event where status = 'SENT' order by id",
                (rs, rowNum) -> rs.getString("event_key")
        );

        int consumed = 0;
        int skipped = 0;
        for (String eventKey : eventKeys) {
            try {
                jdbcTemplate.update(
                        "insert into consumer_record(consumer_name, event_key, created_at) values (?, ?, ?)",
                        consumerName,
                        eventKey,
                        LocalDateTime.now()
                );
                consumed++;
            } catch (DataIntegrityViolationException ex) {
                skipped++;
            }
        }
        return new ConsumeResult(consumed, skipped);
    }

    public OutboxFlowResult endToEndDemo(String requestNo, BigDecimal amount) {
        List<String> steps = new ArrayList<>();
        createOrderAndStageEvent(requestNo, amount);
        steps.add("create order + outbox in one local transaction");

        DispatchResult firstDispatch = dispatchPendingEvents(true);
        steps.add("first dispatch -> sent=" + firstDispatch.sent() + ", failed=" + firstDispatch.failed());

        DispatchResult secondDispatch = dispatchPendingEvents(false);
        steps.add("second dispatch -> sent=" + secondDispatch.sent() + ", failed=" + secondDispatch.failed());

        ConsumeResult firstConsume = consumeSentEvents("order-consumer");
        steps.add("first consume -> consumed=" + firstConsume.consumed() + ", skipped=" + firstConsume.skipped());

        ConsumeResult secondConsume = consumeSentEvents("order-consumer");
        steps.add("second consume -> consumed=" + secondConsume.consumed() + ", skipped=" + secondConsume.skipped());

        String eventStatus = jdbcTemplate.queryForObject(
                "select status from outbox_event where event_key = ?",
                String.class,
                eventKey(requestNo)
        );

        Integer orderCount = jdbcTemplate.queryForObject(
                "select count(*) from demo_order where request_no = ?",
                Integer.class,
                requestNo
        );

        return new OutboxFlowResult(requestNo, orderCount == null ? 0 : orderCount, eventStatus, steps);
    }

    public int countConsumerRecords(String consumerName, String requestNo) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from consumer_record where consumer_name = ? and event_key = ?",
                Integer.class,
                consumerName,
                eventKey(requestNo)
        );
        return count == null ? 0 : count;
    }

    private String eventKey(String requestNo) {
        return "order-created:" + requestNo;
    }

    private record OutboxEvent(long id, String eventKey, String status, int retryCount) {
    }

    public record DispatchResult(int sent, int failed) {
    }

    public record ConsumeResult(int consumed, int skipped) {
    }

    public record OutboxFlowResult(String requestNo, int orderRows, String finalEventStatus, List<String> steps) {
    }
}
