package com.finansportali.backend.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resolves user identity attributes (email, username, stable id) from the
 * Keycloak-issued JWT held in the Spring Security {@link Authentication}.
 * Used wherever a request-scoped principal needs to be turned into a persisted
 * user reference (alerts, portfolio positions, notifications).
 */
@Service
public class KeycloakUserService {

    /**
     * Extract the user's email from the JWT "email" claim.
     * Falls back to the TEST_EMAIL environment variable when the claim is absent
     * (used in test/dev tokens that carry no email).
     */
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

    /**
     * Resolve a human-readable username, preferring "preferred_username" and
     * falling back through "name", the subject, and finally the authentication name.
     */
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

    /**
     * Resolve a stable per-user identifier for persistence. Normally the OIDC
     * subject ("sub"), but falls back to preferred_username / email / "sid" when
     * the access token omits "sub" so NOT-NULL user_id columns never break.
     */
    public String getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            // Normally the OIDC subject ("sub"). But some Keycloak access-token
            // configurations (e.g. lightweight access tokens) omit "sub" from
            // the access token, which left getSubject() null and blew up inserts
            // into NOT-NULL user_id columns (price_alerts, portfolio_positions).
            // Fall back to a stable per-user claim so a missing "sub" can't break
            // persistence: preferred_username is unique per user in Keycloak and
            // is present here; "sid"/email are last resorts.
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
            return jwt.getClaimAsString("sid");
        }

        return null;
    }
}
