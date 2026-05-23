package com.finansportali.backend.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;

/**
 * Converts JWT token roles to Spring Security GrantedAuthority objects.
 * 
 * This converter extracts roles from the JWT token and converts them to
 * Spring Security authorities with the ROLE_ prefix.
 * 
 * Keycloak can send roles in different formats:
 * 1. realm_access.roles - Realm-level roles
 * 2. resource_access.{client}.roles - Client-specific roles
 * 3. roles - Direct roles claim (configured via client scope mapper)
 * 
 * This implementation looks for roles in the "roles" claim first (simplest),
 * then falls back to realm_access.roles if needed.
 */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // Try to get roles from direct "roles" claim (configured via client scope mapper)
        Collection<String> roles = extractRolesFromClaim(jwt, "roles");
        
        // If not found, try realm_access.roles
        if (roles.isEmpty()) {
            roles = extractRolesFromRealmAccess(jwt);
        }
        
        // Convert roles to GrantedAuthority with ROLE_ prefix
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    /**
     * Extract roles from a direct claim (e.g., "roles": ["USER", "ADMIN"])
     */
    @SuppressWarnings("unchecked")
    private Collection<String> extractRolesFromClaim(Jwt jwt, String claimName) {
        Object rolesClaim = jwt.getClaim(claimName);

        if (rolesClaim instanceof Collection<?> rolesCollection) {
            return (Collection<String>) rolesCollection;
        }
        if (rolesClaim instanceof String role) {
            return Collections.singletonList(role);
        }

        return Collections.emptyList();
    }

    /**
     * Extract roles from realm_access.roles claim
     * (e.g., "realm_access": { "roles": ["USER", "ADMIN"] })
     */
    @SuppressWarnings("unchecked")
    private Collection<String> extractRolesFromRealmAccess(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");

        if (realmAccess instanceof java.util.Map<?, ?> realmAccessMap) {
            Object roles = realmAccessMap.get("roles");

            if (roles instanceof Collection<?> rolesCollection) {
                return (Collection<String>) rolesCollection;
            }
            if (roles instanceof String role) {
                return Collections.singletonList(role);
            }
        }

        return Collections.emptyList();
    }
}
