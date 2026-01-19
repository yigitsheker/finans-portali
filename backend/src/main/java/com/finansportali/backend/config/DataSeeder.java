package com.finansportali.backend.config;

import com.finansportali.backend.service.NewsService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    ApplicationRunner seed(NewsService newsService) {
        return args -> newsService.seedIfEmpty();
    }
}
