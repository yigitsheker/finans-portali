package com.finansportali.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(
            "marketSummary", 
            "marketHistory", 
            "yahooChart",
            "exchange-rates",
            "exchange-rates-by-source",
            "investment-funds",
            "funds-by-type",
            "inflation-all",
            "inflation-latest",
            "deposit-rates-all",
            "deposit-rates-latest"
        );
        mgr.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(30))); // Short TTL for fresh chart data
        return mgr;
    }
}
