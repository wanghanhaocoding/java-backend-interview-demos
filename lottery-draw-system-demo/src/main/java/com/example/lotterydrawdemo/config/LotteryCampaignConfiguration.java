package com.example.lotterydrawdemo.config;

import com.example.lotterydrawdemo.lottery.LotteryCampaign;
import com.example.lotterydrawdemo.lottery.LotteryCampaignConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LotteryCampaignConfiguration {

    @Bean
    public LotteryCampaignConfig lotteryCampaignConfig() {
        return LotteryCampaignConfig.interviewDemoDefault();
    }

    @Bean
    public LotteryCampaign lotteryCampaign(LotteryCampaignConfig lotteryCampaignConfig) {
        return new LotteryCampaign(lotteryCampaignConfig);
    }
}
