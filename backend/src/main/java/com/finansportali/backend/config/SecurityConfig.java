package com.finansportali.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Preflight (CORS)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Swagger / OpenAPI
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                        // Actuator
                        .requestMatchers("/actuator/**").permitAll()

                        // Admin endpoints - require ADMIN role
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // Public market & news endpoints (GET only)
                        .requestMatchers(HttpMethod.GET, "/api/v1/market/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/news/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/funds/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/investment-funds/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/exchange-rates/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bonds/**").permitAll()

                        // Portfolio requires authentication (any authenticated user)
                        .requestMatchers("/api/v1/portfolio/**").authenticated()

                        // Price alerts require authentication
                        .requestMatchers("/api/v1/alerts/**").authenticated()

                        // Technical analysis - public GET access
                        .requestMatchers(HttpMethod.GET, "/api/v1/technical-analysis/**").permitAll()

                        // Everything else - deny by default for security
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Configure JWT authentication converter to extract roles from token.
     * This converter uses our custom JwtRoleConverter to map JWT roles
     * to Spring Security GrantedAuthority objects.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
        return converter;
    }
}
