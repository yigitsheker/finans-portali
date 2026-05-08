package com.finansportali.backend.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for extracting user information from JWT tokens.
 * 
 * This service provides helper methods to access user data from the
 * JWT token stored in the Spring Security context. It does NOT store
 * user data in the database - all user information comes from LDAP
 * via Keycloak JWT tokens.
 * 
 * Usage in controllers:
 * <pre>
 * {@code
 * @Autowired
 * private UserService userService;
 * 
 * public ResponseEntity<?> getUserProfile() {
 *     String userId = userService.getCurrentUserId();
 *     String username = userService.getCurrentUsername();
 *     String email = userService.getCurrentUserEmail();
 *     List<String> roles = userService.getCurrentUserRoles();
 *     
 *     return ResponseEntity.ok(Map.of(
 *         "userId", userId,
 *         "username", username,
 *         "email", email,
 *         "roles", roles
 *     ));
 * }
 * }
 * </pre>
 */
@Service
public class UserService {

    /**
     * Get the current authenticated user's ID (subject claim from JWT).
     * 
     * @return User ID (UUID from LDAP) or null if not authenticated
     */
    public String getCurrentUserId() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getSubject() : null;
    }

    /**
     * Get the current authenticated user's username (preferred_username claim).
     * 
     * @return Username (e.g., "john.doe") or null if not authenticated
     */
    public String getCurrentUsername() {
        Jwt jwt = getCurrentJwt();
        if (jwt == null) {
            return null;
        }
        
        // Try preferred_username first (standard Keycloak claim)
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null) {
            return username;
        }
        
        // Fallback to name or email
        username = jwt.getClaimAsString("name");
        if (username != null) {
            return username;
        }
        
        return jwt.getClaimAsString("email");
    }

    /**
     * Get the current authenticated user's email address.
     * 
     * @return Email address or null if not authenticated or email not available
     */
    public String getCurrentUserEmail() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    /**
     * Get the current authenticated user's given name (first name).
     * 
     * @return Given name or null if not available
     */
    public String getCurrentUserGivenName() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("given_name") : null;
    }

    /**
     * Get the current authenticated user's family name (last name).
     * 
     * @return Family name or null if not available
     */
    public String getCurrentUserFamilyName() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("family_name") : null;
    }

    /**
     * Get the current authenticated user's full name.
     * 
     * @return Full name or null if not available
     */
    public String getCurrentUserFullName() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("name") : null;
    }

    /**
     * Get the current authenticated user's roles.
     * 
     * @return List of role names (e.g., ["USER", "ADMIN"]) or empty list
     */
    public List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Collections.emptyList();
        }
        
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .collect(Collectors.toList());
    }

    /**
     * Check if the current user has a specific role.
     * 
     * @param role Role name (e.g., "ADMIN" or "USER")
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(String role) {
        return getCurrentUserRoles().contains(role);
    }

    /**
     * Check if the current user is an administrator.
     * 
     * @return true if user has ADMIN role, false otherwise
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Check if there is a currently authenticated user.
     * 
     * @return true if user is authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * Get the current JWT token from the security context.
     * 
     * @return JWT token or null if not authenticated or not a JWT authentication
     */
    private Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt) {
            return (Jwt) principal;
        }
        
        return null;
    }

    /**
     * Get all claims from the current JWT token.
     * Useful for debugging or accessing custom claims.
     * 
     * @return Map of all JWT claims or empty map if not authenticated
     */
    public java.util.Map<String, Object> getAllClaims() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaims() : Collections.emptyMap();
    }
}
