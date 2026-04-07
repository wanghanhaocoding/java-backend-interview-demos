package com.example.lotterydrawdemo.lottery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LotteryCampaign {

    private final LotteryCampaignConfig lotteryCampaignConfig;
    private final byte[] prizePool;
    private final PrizeTierDefinition[] prizeIndex;
    private final Map<String, AtomicInteger> issuedPrizeCounts;
    private final ConcurrentHashMap<String, DrawResult> requestResults;
    private final AtomicInteger drawCursor;

    public LotteryCampaign(LotteryCampaignConfig lotteryCampaignConfig) {
        this.lotteryCampaignConfig = lotteryCampaignConfig;
        this.prizeIndex = new PrizeTierDefinition[lotteryCampaignConfig.prizeTiers().size() + 1];
        this.issuedPrizeCounts = new LinkedHashMap<>();
        this.requestResults = new ConcurrentHashMap<>();
        this.drawCursor = new AtomicInteger(0);
        this.prizePool = buildPrizePool();
    }

    public DrawResult draw(String userId, String requestId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        return requestResults.computeIfAbsent(requestId, key -> doDraw(userId, requestId));
    }

    public CampaignSnapshot snapshot() {
        int drawsIssued = Math.min(drawCursor.get(), prizePool.length);
        int issuedPrizeCount = 0;
        List<PrizeStock> prizeStocks = new ArrayList<>();
        for (PrizeTierDefinition prizeTierDefinition : lotteryCampaignConfig.prizeTiers()) {
            int issuedCount = issuedPrizeCounts.get(prizeTierDefinition.prizeCode()).get();
            issuedPrizeCount += issuedCount;
            prizeStocks.add(new PrizeStock(
                    prizeTierDefinition.prizeCode(),
                    prizeTierDefinition.prizeName(),
                    prizeTierDefinition.prizeCount(),
                    issuedCount,
                    prizeTierDefinition.prizeCount() - issuedCount
            ));
        }
        return new CampaignSnapshot(
                lotteryCampaignConfig.totalDrawCount(),
                drawsIssued,
                Math.max(lotteryCampaignConfig.totalDrawCount() - drawsIssued, 0),
                drawsIssued - issuedPrizeCount,
                requestResults.size(),
                Collections.unmodifiableList(prizeStocks)
        );
    }

    private DrawResult doDraw(String userId, String requestId) {
        long startNanos = System.nanoTime();
        int slot = drawCursor.getAndIncrement();
        if (slot >= prizePool.length) {
            return new DrawResult(
                    requestId,
                    userId,
                    false,
                    false,
                    null,
                    "活动已结束",
                    0,
                    0,
                    elapsedMicros(startNanos),
                    "campaign-closed"
            );
        }

        PrizeTierDefinition prizeTierDefinition = prizeIndex[prizePool[slot]];
        String prizeCode = null;
        String prizeName = "未中奖";
        if (prizeTierDefinition != null) {
            prizeCode = prizeTierDefinition.prizeCode();
            prizeName = prizeTierDefinition.prizeName();
            issuedPrizeCounts.get(prizeCode).incrementAndGet();
        }

        return new DrawResult(
                requestId,
                userId,
                true,
                true,
                prizeCode,
                prizeName,
                slot + 1,
                Math.max(lotteryCampaignConfig.totalDrawCount() - (slot + 1), 0),
                elapsedMicros(startNanos),
                prizeTierDefinition == null ? "not-winning" : "prize-issued"
        );
    }

    private byte[] buildPrizePool() {
        byte[] pool = new byte[lotteryCampaignConfig.totalDrawCount()];
        int cursor = 0;
        int index = 1;
        int totalPrizeCount = 0;
        for (PrizeTierDefinition prizeTierDefinition : lotteryCampaignConfig.prizeTiers()) {
            if (issuedPrizeCounts.containsKey(prizeTierDefinition.prizeCode())) {
                throw new IllegalArgumentException("duplicate prizeCode: " + prizeTierDefinition.prizeCode());
            }
            if (index > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("too many prize tiers for byte-based pool");
            }
            totalPrizeCount += prizeTierDefinition.prizeCount();
            if (totalPrizeCount > lotteryCampaignConfig.totalDrawCount()) {
                throw new IllegalArgumentException("total prize count exceeds total draw count");
            }
            for (int i = 0; i < prizeTierDefinition.prizeCount(); i++) {
                pool[cursor++] = (byte) index;
            }
            prizeIndex[index] = prizeTierDefinition;
            issuedPrizeCounts.put(prizeTierDefinition.prizeCode(), new AtomicInteger(0));
            index++;
        }
        shuffle(pool, lotteryCampaignConfig.shuffleSeed());
        return pool;
    }

    private void shuffle(byte[] pool, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = pool.length - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            byte temp = pool[i];
            pool[i] = pool[swapIndex];
            pool[swapIndex] = temp;
        }
    }

    private long elapsedMicros(long startNanos) {
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
    }

    public static final class DrawResult {

        private final String requestId;
        private final String userId;
        private final boolean campaignOpen;
        private final boolean drawAccepted;
        private final String prizeCode;
        private final String prizeName;
        private final int drawSequence;
        private final int remainingDraws;
        private final long elapsedMicros;
        private final String message;

        private DrawResult(String requestId,
                           String userId,
                           boolean campaignOpen,
                           boolean drawAccepted,
                           String prizeCode,
                           String prizeName,
                           int drawSequence,
                           int remainingDraws,
                           long elapsedMicros,
                           String message) {
            this.requestId = requestId;
            this.userId = userId;
            this.campaignOpen = campaignOpen;
            this.drawAccepted = drawAccepted;
            this.prizeCode = prizeCode;
            this.prizeName = prizeName;
            this.drawSequence = drawSequence;
            this.remainingDraws = remainingDraws;
            this.elapsedMicros = elapsedMicros;
            this.message = message;
        }

        public String requestId() {
            return requestId;
        }

        public String userId() {
            return userId;
        }

        public boolean campaignOpen() {
            return campaignOpen;
        }

        public boolean drawAccepted() {
            return drawAccepted;
        }

        public boolean wonPrize() {
            return prizeCode != null;
        }

        public String prizeCode() {
            return prizeCode;
        }

        public String prizeName() {
            return prizeName;
        }

        public int drawSequence() {
            return drawSequence;
        }

        public int remainingDraws() {
            return remainingDraws;
        }

        public long elapsedMicros() {
            return elapsedMicros;
        }

        public String message() {
            return message;
        }
    }

    public static final class CampaignSnapshot {

        private final int totalDrawCount;
        private final int drawsIssued;
        private final int remainingDraws;
        private final int nonWinningDraws;
        private final int uniqueRequestCount;
        private final List<PrizeStock> prizeStocks;

        private CampaignSnapshot(int totalDrawCount,
                                 int drawsIssued,
                                 int remainingDraws,
                                 int nonWinningDraws,
                                 int uniqueRequestCount,
                                 List<PrizeStock> prizeStocks) {
            this.totalDrawCount = totalDrawCount;
            this.drawsIssued = drawsIssued;
            this.remainingDraws = remainingDraws;
            this.nonWinningDraws = nonWinningDraws;
            this.uniqueRequestCount = uniqueRequestCount;
            this.prizeStocks = prizeStocks;
        }

        public int totalDrawCount() {
            return totalDrawCount;
        }

        public int drawsIssued() {
            return drawsIssued;
        }

        public int remainingDraws() {
            return remainingDraws;
        }

        public int nonWinningDraws() {
            return nonWinningDraws;
        }

        public int uniqueRequestCount() {
            return uniqueRequestCount;
        }

        public List<PrizeStock> prizeStocks() {
            return prizeStocks;
        }
    }

    public static final class PrizeStock {

        private final String prizeCode;
        private final String prizeName;
        private final int configuredCount;
        private final int issuedCount;
        private final int remainingCount;

        private PrizeStock(String prizeCode, String prizeName, int configuredCount, int issuedCount, int remainingCount) {
            this.prizeCode = prizeCode;
            this.prizeName = prizeName;
            this.configuredCount = configuredCount;
            this.issuedCount = issuedCount;
            this.remainingCount = remainingCount;
        }

        public String prizeCode() {
            return prizeCode;
        }

        public String prizeName() {
            return prizeName;
        }

        public int configuredCount() {
            return configuredCount;
        }

        public int issuedCount() {
            return issuedCount;
        }

        public int remainingCount() {
            return remainingCount;
        }
    }
}
