package com.finansportali.backend.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.info.Info;

import java.time.LocalDateTime;
import java.util.Map;

@Configuration
public class ObservabilityConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(false); // Security: don't log request body
        filter.setIncludeHeaders(false); // Security: don't log headers
        filter.setMaxPayloadLength(1000);
        return filter;
    }

    @Bean
    public HealthIndicator customHealthIndicator() {
        return () -> {
            // Custom health checks
            boolean databaseHealthy = checkDatabaseHealth();
            boolean externalApiHealthy = checkExternalApiHealth();
            
            if (databaseHealthy && externalApiHealthy) {
                return Health.up()
                    .withDetail("database", "UP")
                    .withDetail("external-apis", "UP")
                    .withDetail("timestamp", LocalDateTime.now())
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", databaseHealthy ? "UP" : "DOWN")
                    .withDetail("external-apis", externalApiHealthy ? "UP" : "DOWN")
                    .withDetail("timestamp", LocalDateTime.now())
                    .build();
            }
        };
    }

    @Bean
    public InfoContributor customInfoContributor() {
        return builder -> builder
            .withDetail("app", Map.of(
                "name", "Finans Backend",
                "version", "1.0.0",
                "description", "Enterprise Financial Portfolio Management System",
                "build-time", LocalDateTime.now().toString()
            ))
            .withDetail("features", Map.of(
                "real-time-quotes", "enabled",
                "price-alerts", "enabled",
                "portfolio-management", "enabled",
                "news-integration", "enabled",
                "distributed-tracing", "enabled",
                "metrics-collection", "enabled"
            ))
            .withDetail("integrations", Map.of(
                "yahoo-finance", "active",
                "keycloak-auth", "active",
                "postgresql", "active",
                "prometheus", "active",
                "jaeger", "active"
            ));
    }

    private boolean checkDatabaseHealth() {
        // Implement actual database health check
        // This is a placeholder
        return true;
    }

    private boolean checkExternalApiHealth() {
        // Implement actual external API health check
        // This is a placeholder
        return true;
    }
}