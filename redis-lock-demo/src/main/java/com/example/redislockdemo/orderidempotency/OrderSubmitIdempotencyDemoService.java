package com.example.redislockdemo.orderidempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderSubmitIdempotencyDemoService {

    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "end "
                    + "return 0";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    public OrderSubmitIdempotencyDemoService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public SubmitDemoResult unsafeCheckThenCreate(String requestNo, long userId, int threadCount) throws InterruptedException {
        SimulatedOrderStore store = new SimulatedOrderStore();
        List<AttemptOutcome> outcomes = runConcurrent(threadCount, () -> {
            if (store.existsByRequestNo(requestNo)) {
                return AttemptOutcome.replayed(store.findFirstByRequestNo(requestNo));
            }

            pause(30);
            return AttemptOutcome.created(store.create(userId, requestNo));
        });

        return summarize("unsafe-check-then-create", requestNo, userId, threadCount, store.allOrders(), outcomes);
    }

    public SubmitDemoResult claimFirstWithRedis(String requestNo, long userId, int threadCount) throws InterruptedException {
        String claimKey = claimKey(requestNo);
        stringRedisTemplate.delete(claimKey);

        SimulatedOrderStore store = new SimulatedOrderStore();
        ConcurrentHashMap<String, OrderRecord> orderResultByRequestNo = new ConcurrentHashMap<>();

        List<AttemptOutcome> outcomes = runConcurrent(threadCount, () -> {
            String ownerToken = UUID.randomUUID() + ":" + Thread.currentThread().getId();
            Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(claimKey, ownerToken, Duration.ofSeconds(5));

            if (Boolean.TRUE.equals(claimed)) {
                try {
                    pause(30);
                    OrderRecord created = store.create(userId, requestNo);
                    orderResultByRequestNo.put(requestNo, created);
                    return AttemptOutcome.created(created);
                } finally {
                    releaseClaim(claimKey, ownerToken);
                }
            }

            OrderRecord replayed = waitForOrderResult(orderResultByRequestNo, requestNo, Duration.ofMillis(400));
            if (replayed != null) {
                return AttemptOutcome.replayed(replayed);
            }
            return AttemptOutcome.processing();
        });

        return summarize("redis-claim-first", requestNo, userId, threadCount, store.allOrders(), outcomes);
    }

    public void clearClaim(String requestNo) {
        stringRedisTemplate.delete(claimKey(requestNo));
    }

    private List<AttemptOutcome> runConcurrent(int threadCount, ThrowingSupplier action) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<AttemptOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(3, TimeUnit.SECONDS);
                    outcomes.add(action.get());
                } catch (Throwable throwable) {
                    failures.add(throwable);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Order submit demo execution failed", failures.get(0));
        }
        return List.copyOf(outcomes);
    }

    private SubmitDemoResult summarize(String strategy,
                                       String requestNo,
                                       long userId,
                                       int threadCount,
                                       List<OrderRecord> storedOrders,
                                       List<AttemptOutcome> outcomes) {
        int createdResponses = 0;
        int replayedResponses = 0;
        int processingResponses = 0;
        Set<String> observedOrderNos = new LinkedHashSet<>();

        for (AttemptOutcome outcome : outcomes) {
            if (outcome.order() != null) {
                observedOrderNos.add(outcome.order().orderNo());
            }
            switch (outcome.status()) {
                case CREATED -> createdResponses++;
                case REPLAYED -> replayedResponses++;
                case PROCESSING -> processingResponses++;
            }
        }

        return new SubmitDemoResult(
                strategy,
                requestNo,
                userId,
                threadCount,
                storedOrders.size(),
                createdResponses,
                replayedResponses,
                processingResponses,
                List.copyOf(storedOrders),
                Set.copyOf(observedOrderNos)
        );
    }

    private OrderRecord waitForOrderResult(ConcurrentHashMap<String, OrderRecord> orderResultByRequestNo,
                                           String requestNo,
                                           Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            OrderRecord result = orderResultByRequestNo.get(requestNo);
            if (result != null) {
                return result;
            }
            pause(10);
        }
        return null;
    }

    private void releaseClaim(String claimKey, String ownerToken) {
        stringRedisTemplate.execute(releaseScript, Collections.singletonList(claimKey), ownerToken);
    }

    private String claimKey(String requestNo) {
        return "idem:order:submit:" + requestNo;
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record SubmitDemoResult(String strategy,
                                   String requestNo,
                                   long userId,
                                   int threadCount,
                                   int ordersCreated,
                                   int createdResponses,
                                   int replayedResponses,
                                   int processingResponses,
                                   List<OrderRecord> storedOrders,
                                   Set<String> observedOrderNos) {
    }

    public record OrderRecord(long id, String orderNo, long userId, String requestNo) {
    }

    private record AttemptOutcome(AttemptStatus status, OrderRecord order) {

        static AttemptOutcome created(OrderRecord order) {
            return new AttemptOutcome(AttemptStatus.CREATED, order);
        }

        static AttemptOutcome replayed(OrderRecord order) {
            return new AttemptOutcome(AttemptStatus.REPLAYED, order);
        }

        static AttemptOutcome processing() {
            return new AttemptOutcome(AttemptStatus.PROCESSING, null);
        }
    }

    private enum AttemptStatus {
        CREATED,
        REPLAYED,
        PROCESSING
    }

    private static final class SimulatedOrderStore {

        private final AtomicLong sequence = new AtomicLong(1000);
        private final Queue<OrderRecord> orders = new ConcurrentLinkedQueue<>();

        boolean existsByRequestNo(String requestNo) {
            return findFirstByRequestNo(requestNo) != null;
        }

        OrderRecord findFirstByRequestNo(String requestNo) {
            for (OrderRecord order : orders) {
                if (order.requestNo().equals(requestNo)) {
                    return order;
                }
            }
            return null;
        }

        OrderRecord create(long userId, String requestNo) {
            long id = sequence.incrementAndGet();
            OrderRecord record = new OrderRecord(id, "ORD" + id, userId, requestNo);
            orders.add(record);
            return record;
        }

        List<OrderRecord> allOrders() {
            return new ArrayList<>(orders);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        AttemptOutcome get() throws Exception;
    }
}
