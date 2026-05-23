package com.finansportali.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of allowed origins. Defaulted to the two
     * developer-friendly localhost origins; production deploys override
     * via APP_CORS_ALLOWED_ORIGINS (Sonar S5122: never hard-code
     * arbitrary origins in source).
     */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost}")
    private String allowedOriginsCsv;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(allowedOrigins);

        // Preflight + normal istekler
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Browser'ın göndereceği header'lara izin ver
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));

        // (Opsiyonel ama faydalı) Frontend'in okuyabilmesi için
        config.setExposedHeaders(List.of("Authorization"));

        // Cookie vs gerekiyorsa true, bizde şart değil ama açık kalsın
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}