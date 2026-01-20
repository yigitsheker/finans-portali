package com.finansportali.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // Tarayıcıyı /login sayfasına yönlendirmesin
                .formLogin(form -> form.disable())
                // Şimdilik Basic Auth da kapalı kalsın (tamamen açık geliştirme modu)
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Açık endpointler
                        .requestMatchers(
                                "/",
                                "/error",
                                "/actuator/health",
                                "/actuator/info",
                                "/api/v1/news",
                                "/api/v1/news/**",
                                "/api/v1/market/summary",
                                "/api/v1/portfolio/**"
                        ).permitAll()
                        // Diğer her şey (şimdilik de açık istersen permitAll bırakabilirsin)
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
