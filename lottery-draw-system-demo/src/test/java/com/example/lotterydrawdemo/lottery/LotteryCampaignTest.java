package com.example.lotterydrawdemo.lottery;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LotteryCampaignTest {

    @Test
    void shouldExhaustAllPrizesAndStopAfterConfiguredDrawCount() {
        LotteryCampaign campaign = new LotteryCampaign(sampleConfig(20, 2, 3, 5));

        for (int i = 0; i < 20; i++) {
            LotteryCampaign.DrawResult drawResult = campaign.draw("user-" + i, "request-" + i);
            assertThat(drawResult.drawAccepted()).isTrue();
        }

        LotteryCampaign.CampaignSnapshot snapshot = campaign.snapshot();
        assertThat(snapshot.drawsIssued()).isEqualTo(20);
        assertThat(snapshot.remainingDraws()).isZero();
        assertThat(snapshot.nonWinningDraws()).isEqualTo(10);
        assertThat(snapshot.prizeStocks()).extracting(LotteryCampaign.PrizeStock::prizeName)
                .containsExactly("一等奖", "二等奖", "三等奖");
        assertThat(snapshot.prizeStocks()).extracting(LotteryCampaign.PrizeStock::issuedCount)
                .containsExactly(2, 3, 5);
        assertThat(snapshot.prizeStocks()).extracting(LotteryCampaign.PrizeStock::remainingCount)
                .containsExactly(0, 0, 0);

        LotteryCampaign.DrawResult closedResult = campaign.draw("late-user", "late-request");
        assertThat(closedResult.campaignOpen()).isFalse();
        assertThat(closedResult.drawAccepted()).isFalse();
        assertThat(closedResult.message()).isEqualTo("campaign-closed");
    }

    @Test
    void shouldKeepIdempotentResultForSameRequestId() {
        LotteryCampaign campaign = new LotteryCampaign(sampleConfig(10, 1, 2, 2));

        LotteryCampaign.DrawResult first = campaign.draw("user-1", "req-1");
        LotteryCampaign.DrawResult second = campaign.draw("user-1", "req-1");

        assertThat(second.drawSequence()).isEqualTo(first.drawSequence());
        assertThat(second.prizeName()).isEqualTo(first.prizeName());
        assertThat(campaign.snapshot().drawsIssued()).isEqualTo(1);
        assertThat(campaign.snapshot().uniqueRequestCount()).isEqualTo(1);
    }

    @Test
    void shouldStayThreadSafeUnderConcurrentDrawRequests() throws Exception {
        LotteryCampaign campaign = new LotteryCampaign(sampleConfig(500, 50, 100, 150));
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<LotteryCampaign.DrawResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 700; i++) {
                final int index = i;
                futures.add(executorService.submit(() -> {
                    startGate.await(3, TimeUnit.SECONDS);
                    return campaign.draw("user-" + index, "req-" + index);
                }));
            }

            startGate.countDown();

            int acceptedCount = 0;
            int closedCount = 0;
            Set<Integer> sequences = new HashSet<>();
            for (Future<LotteryCampaign.DrawResult> future : futures) {
                LotteryCampaign.DrawResult drawResult = future.get(3, TimeUnit.SECONDS);
                if (drawResult.drawAccepted()) {
                    acceptedCount++;
                    sequences.add(drawResult.drawSequence());
                } else {
                    closedCount++;
                }
            }

            LotteryCampaign.CampaignSnapshot snapshot = campaign.snapshot();
            assertThat(acceptedCount).isEqualTo(500);
            assertThat(closedCount).isEqualTo(200);
            assertThat(sequences).hasSize(500);
            assertThat(snapshot.drawsIssued()).isEqualTo(500);
            assertThat(snapshot.remainingDraws()).isZero();
            assertThat(snapshot.prizeStocks()).extracting(LotteryCampaign.PrizeStock::issuedCount)
                    .containsExactly(50, 100, 150);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldFinishSingleDrawWithinTwoSecondsInLocalDemo() {
        LotteryCampaign campaign = new LotteryCampaign(sampleConfig(100, 5, 10, 15));

        LotteryCampaign.DrawResult drawResult = campaign.draw("user-latency", "req-latency");

        assertThat(drawResult.elapsedMicros()).isLessThan(2_000_000L);
    }

    private LotteryCampaignConfig sampleConfig(int totalDrawCount, int firstPrizeCount, int secondPrizeCount, int thirdPrizeCount) {
        return new LotteryCampaignConfig(
                totalDrawCount,
                20260406L,
                Arrays.asList(
                        new PrizeTierDefinition("FIRST_PRIZE", "一等奖", firstPrizeCount),
                        new PrizeTierDefinition("SECOND_PRIZE", "二等奖", secondPrizeCount),
                        new PrizeTierDefinition("THIRD_PRIZE", "三等奖", thirdPrizeCount)
                )
        );
    }
}
