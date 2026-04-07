package com.example.lotterydrawdemo.lottery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LotteryCampaignConfig {

    private final int totalDrawCount;
    private final long shuffleSeed;
    private final List<PrizeTierDefinition> prizeTiers;

    public LotteryCampaignConfig(int totalDrawCount, long shuffleSeed, List<PrizeTierDefinition> prizeTiers) {
        if (totalDrawCount <= 0) {
            throw new IllegalArgumentException("totalDrawCount must be positive");
        }
        if (prizeTiers == null || prizeTiers.isEmpty()) {
            throw new IllegalArgumentException("prizeTiers must not be empty");
        }
        this.totalDrawCount = totalDrawCount;
        this.shuffleSeed = shuffleSeed;
        this.prizeTiers = Collections.unmodifiableList(new ArrayList<>(prizeTiers));
    }

    public static LotteryCampaignConfig interviewDemoDefault() {
        return new LotteryCampaignConfig(
                2_000_000,
                20260406L,
                Arrays.asList(
                        new PrizeTierDefinition("FIRST_PRIZE", "一等奖", 50_000),
                        new PrizeTierDefinition("SECOND_PRIZE", "二等奖", 100_000),
                        new PrizeTierDefinition("THIRD_PRIZE", "三等奖", 200_000)
                )
        );
    }

    public int totalDrawCount() {
        return totalDrawCount;
    }

    public long shuffleSeed() {
        return shuffleSeed;
    }

    public List<PrizeTierDefinition> prizeTiers() {
        return prizeTiers;
    }
}
