package com.example.mqcacheidempotencydemo.cache;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CacheConsistencyDemoService {

    private final Map<String, Integer> database = new LinkedHashMap<>();
    private final Map<String, Integer> cache = new LinkedHashMap<>();

    public void reset() {
        database.clear();
        cache.clear();
    }

    public CacheReadResult staleReadDemo(String key, int oldValue, int newValue) {
        reset();
        List<String> steps = new ArrayList<>();

        database.put(key, oldValue);
        cache.put(key, oldValue);
        steps.add("1. 先把旧值写进数据库，并预热到缓存");

        database.put(key, newValue);
        steps.add("2. 只更新数据库但忘记删除缓存，缓存里仍然保留旧值");

        int staleValue = readThroughCache(key);
        steps.add("3. 下次读请求优先命中缓存，因此读到了旧值 " + staleValue);

        return new CacheReadResult(steps, staleValue, database.get(key), cache.get(key));
    }

    public CacheRepairResult delayedDoubleDeleteDemo(String key, int oldValue, int newValue) {
        reset();
        List<String> steps = new ArrayList<>();

        database.put(key, oldValue);
        cache.put(key, oldValue);
        steps.add("1. 旧值已经同时存在于数据库和缓存");

        cache.remove(key);
        steps.add("2. 更新前先删缓存，尽量减少脏读窗口");

        cache.put(key, oldValue);
        steps.add("3. 并发请求在更新完成前把旧值重新回填到缓存");

        database.put(key, newValue);
        steps.add("4. 数据库更新成新值 " + newValue);

        cache.remove(key);
        steps.add("5. 延时双删的第二次删除把脏缓存再清一遍");

        int finalRead = readThroughCache(key);
        steps.add("6. 下一次读请求回源数据库，重新加载到缓存，读到新值 " + finalRead);

        return new CacheRepairResult(steps, finalRead, cache.get(key));
    }

    public BreakdownProtectionResult breakdownProtectionDemo(String key, int requestCount) {
        reset();
        database.put(key, 200);
        int loadersWithoutMutex = simulateConcurrentMisses(key, requestCount, false);
        int loadersWithMutex = simulateConcurrentMisses(key, requestCount, true);
        return new BreakdownProtectionResult(loadersWithoutMutex, loadersWithMutex);
    }

    private int readThroughCache(String key) {
        return cache.computeIfAbsent(key, database::get);
    }

    private int simulateConcurrentMisses(String key, int requestCount, boolean protectWithMutex) {
        cache.clear();
        if (protectWithMutex) {
            readThroughCache(key);
            return 1;
        }
        return requestCount;
    }

    public static final class CacheReadResult {

        private final List<String> steps;
        private final int staleCacheValue;
        private final int databaseValue;
        private final int cacheValue;

        public CacheReadResult(List<String> steps, int staleCacheValue, int databaseValue, int cacheValue) {
            this.steps = steps;
            this.staleCacheValue = staleCacheValue;
            this.databaseValue = databaseValue;
            this.cacheValue = cacheValue;
        }

        public List<String> steps() {
            return steps;
        }

        public int staleCacheValue() {
            return staleCacheValue;
        }

        public int databaseValue() {
            return databaseValue;
        }

        public int cacheValue() {
            return cacheValue;
        }
    }

    public static final class CacheRepairResult {

        private final List<String> steps;
        private final int finalReadValue;
        private final int finalCacheValue;

        public CacheRepairResult(List<String> steps, int finalReadValue, int finalCacheValue) {
            this.steps = steps;
            this.finalReadValue = finalReadValue;
            this.finalCacheValue = finalCacheValue;
        }

        public List<String> steps() {
            return steps;
        }

        public int finalReadValue() {
            return finalReadValue;
        }

        public int finalCacheValue() {
            return finalCacheValue;
        }
    }

    public static final class BreakdownProtectionResult {

        private final int loadersWithoutMutex;
        private final int loadersWithMutex;

        public BreakdownProtectionResult(int loadersWithoutMutex, int loadersWithMutex) {
            this.loadersWithoutMutex = loadersWithoutMutex;
            this.loadersWithMutex = loadersWithMutex;
        }

        public int loadersWithoutMutex() {
            return loadersWithoutMutex;
        }

        public int loadersWithMutex() {
            return loadersWithMutex;
        }
    }
}
