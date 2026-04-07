package com.example.lotterydrawdemo.demo;

import com.example.lotterydrawdemo.lottery.LotteryCampaign;
import com.example.lotterydrawdemo.lottery.LotteryCampaignConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final LotteryCampaignConfig lotteryCampaignConfig;

    public DemoRunner(LotteryCampaignConfig lotteryCampaignConfig) {
        this.lotteryCampaignConfig = lotteryCampaignConfig;
    }

    @Override
    public void run(String... args) {
        LotteryCampaign previewCampaign = new LotteryCampaign(lotteryCampaignConfig);

        printTitle("1. 题目澄清");
        System.out.println("题目里的抽奖次数如果是 20w，就无法发完 35w 个奖品。");
        System.out.println("所以这个 demo 按 200w 总抽奖次数实现；如果面试官坚持 20w，奖品库存必须同步缩容。");

        printTitle("2. 抽奖样例");
        printDrawResult(previewCampaign.draw("user-1001", "REQ-1001"));
        printDrawResult(previewCampaign.draw("user-1002", "REQ-1002"));
        printDrawResult(previewCampaign.draw("user-1003", "REQ-1003"));
        printDrawResult(previewCampaign.draw("user-1001", "REQ-1001"));

        LotteryCampaign.CampaignSnapshot snapshot = previewCampaign.snapshot();
        System.out.println("drawsIssued = " + snapshot.drawsIssued());
        System.out.println("remainingDraws = " + snapshot.remainingDraws());
        System.out.println("nonWinningDraws = " + snapshot.nonWinningDraws());
        snapshot.prizeStocks().forEach(stock -> System.out.println(
                stock.prizeName() + " => issued=" + stock.issuedCount() + ", remaining=" + stock.remainingCount()
        ));

        printTitle("3. 面试里怎么回答");
        System.out.println("生产版我会用 Redis 预生成奖池 + Lua 原子出队，保证总次数和库存绝对准确。");
        System.out.println("抽奖主链路只做资格校验、幂等校验、Redis 原子出队和结果返回，数据库落库和发奖走 MQ 异步化。");
        System.out.println("这样既能保证奖品必须抽完，又能保证用户在 2 秒内拿到结果。");
        System.out.println("当前 demo 用 byte[] 奖池 + AtomicInteger 模拟 Redis 原子出队，用 REST API 演示服务化接口。");
    }

    private void printDrawResult(LotteryCampaign.DrawResult drawResult) {
        System.out.println(
                drawResult.requestId()
                        + " => open=" + drawResult.campaignOpen()
                        + ", accepted=" + drawResult.drawAccepted()
                        + ", prize=" + drawResult.prizeName()
                        + ", drawSequence=" + drawResult.drawSequence()
                        + ", elapsedMicros=" + drawResult.elapsedMicros()
        );
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
