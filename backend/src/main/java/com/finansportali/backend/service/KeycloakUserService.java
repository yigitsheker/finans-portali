package com.finansportali.backend.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class KeycloakUserService {

    public String getUserEmail(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            
            // For testing: use TEST_EMAIL environment variable if no email in JWT
            if (email == null || email.isEmpty()) {
                email = System.getenv("TEST_EMAIL");
            }
            
            return email;
        }

        return null;
    }

    public String getUsername(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username == null || username.isEmpty()) {
                username = jwt.getClaimAsString("name");
            }
            if (username == null || username.isEmpty()) {
                username = jwt.getSubject();
            }
            return username;
        }

        return authentication.getName();
    }

    public String getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        return null;
    }
}
