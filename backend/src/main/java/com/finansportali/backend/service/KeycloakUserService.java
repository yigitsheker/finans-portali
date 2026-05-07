package com.finansportali.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class KeycloakUserService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserService.class);

    /**
     * Get user email from JWT token
     * Keycloak JWT token contains 'email' claim
     */
    public String getUserEmail(Authentication authentication) {
        try {
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String email = jwt.getClaimAsString("email");
                
                if (email != null && !email.isEmpty()) {
                    log.debug("Found email in JWT token: {}", email);
                    return email;
                }
                
                log.warn("No email claim found in JWT token for user: {}", authentication.getName());
            } else {
                log.warn("Authentication is not JwtAuthenticationToken: {}", authentication.getClass().getName());
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to extract email from JWT token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get username from JWT token
     * Keycloak JWT token contains 'preferred_username' claim
     */
    public String getUsername(Authentication authentication) {
        try {
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String username = jwt.getClaimAsString("preferred_username");
                
                if (username != null && !username.isEmpty()) {
                    log.debug("Found username in JWT token: {}", username);
                    return username;
                }
                
                // Fallback to subject
                return authentication.getName();
            }
            
            return authentication.getName();
        } catch (Exception e) {
            log.error("Failed to extract username from JWT token: {}", e.getMessage(), e);
            return authentication.getName();
        }
    }

    /**
     * Get user's full name from JWT token
     * Keycloak JWT token contains 'name' claim
     */
    public String getFullName(Authentication authentication) {
        try {
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String name = jwt.getClaimAsString("name");
                
                if (name != null && !name.isEmpty()) {
                    return name;
                }
                
                // Try to construct from given_name and family_name
                String givenName = jwt.getClaimAsString("given_name");
                String familyName = jwt.getClaimAsString("family_name");
                
                if (givenName != null && familyName != null) {
                    return givenName + " " + familyName;
                }
                
                if (givenName != null) {
                    return givenName;
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to extract full name from JWT token: {}", e.getMessage(), e);
            return null;
        }
    }
}
