package com.finansportali.backend.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security config used by every {@code @WebMvcTest} in this
 * package. The production {@link com.finansportali.backend.config.SecurityConfig}
 * configures an OAuth2 resource server (Keycloak JWT), which isn't
 * available in a controller slice — pulling the prod config in would
 * fail bean wiring because the JwtDecoder isn't initialised.
 *
 * We replace the filter chain with a permissive one. Individual tests
 * still drive authentication via {@code @WithMockUser} or by leaving
 * the endpoint open per the SecurityConfig rules, but the filter
 * itself doesn't try to validate a JWT.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
