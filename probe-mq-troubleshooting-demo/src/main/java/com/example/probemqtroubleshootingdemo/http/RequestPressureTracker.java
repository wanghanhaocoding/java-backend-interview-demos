package com.example.probemqtroubleshootingdemo.http;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RequestPressureTracker {

    private final AtomicInteger activeBlockedRequests = new AtomicInteger();

    private final AtomicInteger maxBlockedRequests = new AtomicInteger();

    private final AtomicLong totalRequests = new AtomicLong();

    private final AtomicLong totalBlockedRequests = new AtomicLong();

    private final AtomicReference<Instant> lastBlockedAt = new AtomicReference<>();

    private final AtomicReference<String> lastBlockingThread = new AtomicReference<>("n/a");

    public RequestToken beginRequest(boolean blocked) {
        totalRequests.incrementAndGet();
        if (!blocked) {
            return RequestToken.noop();
        }

        int current = activeBlockedRequests.incrementAndGet();
        updateMax(current);
        totalBlockedRequests.incrementAndGet();
        lastBlockedAt.set(Instant.now());
        lastBlockingThread.set(Thread.currentThread().getName());
        return new RequestToken(this, true);
    }

    public PressureSnapshot snapshot() {
        return new PressureSnapshot(
                activeBlockedRequests.get(),
                maxBlockedRequests.get(),
                totalRequests.get(),
                totalBlockedRequests.get(),
                lastBlockedAt.get(),
                lastBlockingThread.get()
        );
    }

    public boolean isReadinessDown(int threshold) {
        return activeBlockedRequests.get() >= threshold;
    }

    private void finishBlockedRequest() {
        activeBlockedRequests.updateAndGet(current -> Math.max(0, current - 1));
    }

    private void updateMax(int current) {
        int previous;
        do {
            previous = maxBlockedRequests.get();
            if (current <= previous) {
                return;
            }
        } while (!maxBlockedRequests.compareAndSet(previous, current));
    }

    public static final class RequestToken implements AutoCloseable {

        private static final RequestToken NOOP = new RequestToken(null, false);

        private final RequestPressureTracker tracker;

        private final boolean blocked;

        private RequestToken(RequestPressureTracker tracker, boolean blocked) {
            this.tracker = tracker;
            this.blocked = blocked;
        }

        public static RequestToken noop() {
            return NOOP;
        }

        @Override
        public void close() {
            if (blocked && tracker != null) {
                tracker.finishBlockedRequest();
            }
        }
    }

    public static final class PressureSnapshot {

        private final int activeBlockedRequests;

        private final int maxBlockedRequests;

        private final long totalRequests;

        private final long totalBlockedRequests;

        private final Instant lastBlockedAt;

        private final String lastBlockingThread;

        public PressureSnapshot(int activeBlockedRequests,
                                int maxBlockedRequests,
                                long totalRequests,
                                long totalBlockedRequests,
                                Instant lastBlockedAt,
                                String lastBlockingThread) {
            this.activeBlockedRequests = activeBlockedRequests;
            this.maxBlockedRequests = maxBlockedRequests;
            this.totalRequests = totalRequests;
            this.totalBlockedRequests = totalBlockedRequests;
            this.lastBlockedAt = lastBlockedAt;
            this.lastBlockingThread = lastBlockingThread;
        }

        public int getActiveBlockedRequests() {
            return activeBlockedRequests;
        }

        public int getMaxBlockedRequests() {
            return maxBlockedRequests;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public long getTotalBlockedRequests() {
            return totalBlockedRequests;
        }

        public Instant getLastBlockedAt() {
            return lastBlockedAt;
        }

        public String getLastBlockingThread() {
            return lastBlockingThread;
        }
    }
}
