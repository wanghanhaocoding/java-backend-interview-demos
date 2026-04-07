package com.example.lotterydrawdemo.web;

import com.example.lotterydrawdemo.lottery.LotteryCampaign;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/lottery")
public class LotteryDrawController {

    private final LotteryCampaign lotteryCampaign;

    public LotteryDrawController(LotteryCampaign lotteryCampaign) {
        this.lotteryCampaign = lotteryCampaign;
    }

    @PostMapping("/draw")
    public DrawResponse draw(@RequestBody(required = false) DrawRequest drawRequest) {
        validate(drawRequest);
        LotteryCampaign.DrawResult drawResult = lotteryCampaign.draw(drawRequest.getUserId(), drawRequest.getRequestId());
        return DrawResponse.from(drawResult);
    }

    @GetMapping("/stats")
    public CampaignStatsResponse stats() {
        return CampaignStatsResponse.from(lotteryCampaign.snapshot());
    }

    private void validate(DrawRequest drawRequest) {
        if (drawRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (!StringUtils.hasText(drawRequest.getUserId()) || !StringUtils.hasText(drawRequest.getRequestId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and requestId are required");
        }
    }

    public static final class DrawRequest {

        private String userId;
        private String requestId;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }

    public static final class DrawResponse {

        private final String requestId;
        private final String userId;
        private final boolean campaignOpen;
        private final boolean drawAccepted;
        private final boolean wonPrize;
        private final String prizeCode;
        private final String prizeName;
        private final int drawSequence;
        private final int remainingDraws;
        private final long elapsedMicros;
        private final String message;

        private DrawResponse(String requestId,
                             String userId,
                             boolean campaignOpen,
                             boolean drawAccepted,
                             boolean wonPrize,
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
            this.wonPrize = wonPrize;
            this.prizeCode = prizeCode;
            this.prizeName = prizeName;
            this.drawSequence = drawSequence;
            this.remainingDraws = remainingDraws;
            this.elapsedMicros = elapsedMicros;
            this.message = message;
        }

        public static DrawResponse from(LotteryCampaign.DrawResult drawResult) {
            return new DrawResponse(
                    drawResult.requestId(),
                    drawResult.userId(),
                    drawResult.campaignOpen(),
                    drawResult.drawAccepted(),
                    drawResult.wonPrize(),
                    drawResult.prizeCode(),
                    drawResult.prizeName(),
                    drawResult.drawSequence(),
                    drawResult.remainingDraws(),
                    drawResult.elapsedMicros(),
                    drawResult.message()
            );
        }

        public String getRequestId() {
            return requestId;
        }

        public String getUserId() {
            return userId;
        }

        public boolean isCampaignOpen() {
            return campaignOpen;
        }

        public boolean isDrawAccepted() {
            return drawAccepted;
        }

        public boolean isWonPrize() {
            return wonPrize;
        }

        public String getPrizeCode() {
            return prizeCode;
        }

        public String getPrizeName() {
            return prizeName;
        }

        public int getDrawSequence() {
            return drawSequence;
        }

        public int getRemainingDraws() {
            return remainingDraws;
        }

        public long getElapsedMicros() {
            return elapsedMicros;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class CampaignStatsResponse {

        private final int totalDrawCount;
        private final int drawsIssued;
        private final int remainingDraws;
        private final int nonWinningDraws;
        private final int uniqueRequestCount;
        private final List<PrizeStockResponse> prizeStocks;

        private CampaignStatsResponse(int totalDrawCount,
                                      int drawsIssued,
                                      int remainingDraws,
                                      int nonWinningDraws,
                                      int uniqueRequestCount,
                                      List<PrizeStockResponse> prizeStocks) {
            this.totalDrawCount = totalDrawCount;
            this.drawsIssued = drawsIssued;
            this.remainingDraws = remainingDraws;
            this.nonWinningDraws = nonWinningDraws;
            this.uniqueRequestCount = uniqueRequestCount;
            this.prizeStocks = prizeStocks;
        }

        public static CampaignStatsResponse from(LotteryCampaign.CampaignSnapshot campaignSnapshot) {
            List<PrizeStockResponse> stocks = new ArrayList<>();
            for (LotteryCampaign.PrizeStock prizeStock : campaignSnapshot.prizeStocks()) {
                stocks.add(new PrizeStockResponse(
                        prizeStock.prizeCode(),
                        prizeStock.prizeName(),
                        prizeStock.configuredCount(),
                        prizeStock.issuedCount(),
                        prizeStock.remainingCount()
                ));
            }
            return new CampaignStatsResponse(
                    campaignSnapshot.totalDrawCount(),
                    campaignSnapshot.drawsIssued(),
                    campaignSnapshot.remainingDraws(),
                    campaignSnapshot.nonWinningDraws(),
                    campaignSnapshot.uniqueRequestCount(),
                    stocks
            );
        }

        public int getTotalDrawCount() {
            return totalDrawCount;
        }

        public int getDrawsIssued() {
            return drawsIssued;
        }

        public int getRemainingDraws() {
            return remainingDraws;
        }

        public int getNonWinningDraws() {
            return nonWinningDraws;
        }

        public int getUniqueRequestCount() {
            return uniqueRequestCount;
        }

        public List<PrizeStockResponse> getPrizeStocks() {
            return prizeStocks;
        }
    }

    public static final class PrizeStockResponse {

        private final String prizeCode;
        private final String prizeName;
        private final int configuredCount;
        private final int issuedCount;
        private final int remainingCount;

        private PrizeStockResponse(String prizeCode, String prizeName, int configuredCount, int issuedCount, int remainingCount) {
            this.prizeCode = prizeCode;
            this.prizeName = prizeName;
            this.configuredCount = configuredCount;
            this.issuedCount = issuedCount;
            this.remainingCount = remainingCount;
        }

        public String getPrizeCode() {
            return prizeCode;
        }

        public String getPrizeName() {
            return prizeName;
        }

        public int getConfiguredCount() {
            return configuredCount;
        }

        public int getIssuedCount() {
            return issuedCount;
        }

        public int getRemainingCount() {
            return remainingCount;
        }
    }
}
