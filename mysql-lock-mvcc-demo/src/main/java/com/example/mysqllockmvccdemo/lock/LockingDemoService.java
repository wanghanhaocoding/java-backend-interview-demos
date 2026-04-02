package com.example.mysqllockmvccdemo.lock;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LockingDemoService {

    public GapLockResult gapLockDemo() {
        List<String> steps = new ArrayList<>();
        RangeLockManager rangeLockManager = new RangeLockManager();

        rangeLockManager.lock("T1", 10, 20);
        boolean insert15Blocked = rangeLockManager.isBlocked(15);
        boolean insert25Blocked = rangeLockManager.isBlocked(25);

        steps.add("1. T1 对索引区间 [10, 20) 加 gap lock");
        steps.add("2. 新插入值 15 落在区间内，因此会被拦住");
        steps.add("3. 新插入值 25 不在区间内，因此不会被这个 gap lock 拦住");

        return new GapLockResult(steps, insert15Blocked, insert25Blocked);
    }

    public DeadlockResult deadlockDemo() {
        List<String> steps = new ArrayList<>();
        Map<String, String> holderByResource = new LinkedHashMap<>();
        Map<String, String> waitingFor = new LinkedHashMap<>();

        holderByResource.put("account:A", "T1");
        holderByResource.put("account:B", "T2");
        steps.add("1. T1 先拿到 account:A，T2 先拿到 account:B");

        waitingFor.put("T1", "T2");
        waitingFor.put("T2", "T1");
        steps.add("2. T1 接着想拿 account:B，开始等待 T2");
        steps.add("3. T2 接着想拿 account:A，也开始等待 T1");

        boolean deadlockDetected = detectsCycle(waitingFor, "T1");
        steps.add("4. 等待图里出现 T1 -> T2 -> T1 的环，因此判定为死锁");

        return new DeadlockResult(steps, deadlockDetected);
    }

    private boolean detectsCycle(Map<String, String> waitingFor, String start) {
        String slow = start;
        String fast = start;
        while (true) {
            slow = waitingFor.get(slow);
            fast = waitingFor.get(waitingFor.get(fast));
            if (slow == null || fast == null) {
                return false;
            }
            if (slow.equals(fast)) {
                return true;
            }
        }
    }

    public static final class GapLockResult {

        private final List<String> steps;
        private final boolean insertInRangeBlocked;
        private final boolean insertOutOfRangeBlocked;

        public GapLockResult(List<String> steps, boolean insertInRangeBlocked, boolean insertOutOfRangeBlocked) {
            this.steps = steps;
            this.insertInRangeBlocked = insertInRangeBlocked;
            this.insertOutOfRangeBlocked = insertOutOfRangeBlocked;
        }

        public List<String> steps() {
            return steps;
        }

        public boolean insertInRangeBlocked() {
            return insertInRangeBlocked;
        }

        public boolean insertOutOfRangeBlocked() {
            return insertOutOfRangeBlocked;
        }
    }

    public static final class DeadlockResult {

        private final List<String> steps;
        private final boolean deadlockDetected;

        public DeadlockResult(List<String> steps, boolean deadlockDetected) {
            this.steps = steps;
            this.deadlockDetected = deadlockDetected;
        }

        public List<String> steps() {
            return steps;
        }

        public boolean deadlockDetected() {
            return deadlockDetected;
        }
    }

    private static final class RangeLockManager {

        private final List<RangeLock> rangeLocks = new ArrayList<>();

        private void lock(String transactionId, int startInclusive, int endExclusive) {
            rangeLocks.add(new RangeLock(transactionId, startInclusive, endExclusive));
        }

        private boolean isBlocked(int value) {
            return rangeLocks.stream().anyMatch(lock -> value >= lock.startInclusive && value < lock.endExclusive);
        }
    }

    private static final class RangeLock {

        private final String transactionId;
        private final int startInclusive;
        private final int endExclusive;

        private RangeLock(String transactionId, int startInclusive, int endExclusive) {
            this.transactionId = transactionId;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }
    }
}
