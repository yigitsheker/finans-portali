package com.finansportali.backend.config;

import com.finansportali.backend.service.NewsService;
import com.finansportali.backend.service.ExchangeRateService;
import com.finansportali.backend.service.InvestmentFundService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    ApplicationRunner seed(NewsService newsService, 
                          ExchangeRateService exchangeRateService,
                          InvestmentFundService investmentFundService) {
        return args -> {
            newsService.seedIfEmpty();
            exchangeRateService.seedIfEmpty();
            investmentFundService.seedIfEmpty();
        };
    }
}
