package com.finansportali.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserService}.
 *
 * The class is stateless and pulls everything from
 * SecurityContextHolder. We test by installing different Authentication
 * objects on the thread-local context.
 */
class UserServiceTest {

    private final UserService service = new UserService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        if (!claims.containsKey("sub")) {
            b.subject("user-1");
        }
        return b.build();
    }

    private static void installJwt(Jwt jwt, String... roles) {
        List<SimpleGrantedAuthority> auths = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication auth = new JwtAuthenticationToken(jwt, auths);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void all_getters_return_null_when_no_authentication() {
        assertThat(service.getCurrentUserId()).isNull();
        assertThat(service.getCurrentUsername()).isNull();
        assertThat(service.getCurrentUserEmail()).isNull();
        assertThat(service.getCurrentUserGivenName()).isNull();
        assertThat(service.getCurrentUserFamilyName()).isNull();
        assertThat(service.getCurrentUserFullName()).isNull();
        assertThat(service.getCurrentUserRoles()).isEmpty();
        assertThat(service.getAllClaims()).isEmpty();
        assertThat(service.isAuthenticated()).isFalse();
        assertThat(service.isAdmin()).isFalse();
    }

    @Test
    void returns_null_when_principal_is_not_a_jwt() {
        // A non-JWT auth (e.g. anonymous user, form login) → service treats as missing
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "pwd", List.of()));

        assertThat(service.getCurrentUserId()).isNull();
        assertThat(service.getCurrentUserEmail()).isNull();
    }

    @Test
    void getCurrentUserId_returns_subject() {
        installJwt(jwt(Map.of("sub", "user-42")));
        assertThat(service.getCurrentUserId()).isEqualTo("user-42");
    }

    @Test
    void getCurrentUsername_prefers_preferred_username() {
        installJwt(jwt(Map.of("preferred_username", "alice", "name", "Alice X", "email", "a@x")));
        assertThat(service.getCurrentUsername()).isEqualTo("alice");
    }

    @Test
    void getCurrentUsername_falls_back_to_name() {
        installJwt(jwt(Map.of("name", "Alice X", "email", "a@x")));
        assertThat(service.getCurrentUsername()).isEqualTo("Alice X");
    }

    @Test
    void getCurrentUsername_falls_back_to_email_when_no_name_or_preferred() {
        installJwt(jwt(Map.of("email", "a@x")));
        assertThat(service.getCurrentUsername()).isEqualTo("a@x");
    }

    @Test
    void getCurrentUserEmail_and_names() {
        installJwt(jwt(Map.of(
                "email", "a@x",
                "given_name", "Alice",
                "family_name", "Xie",
                "name", "Alice Xie")));
        assertThat(service.getCurrentUserEmail()).isEqualTo("a@x");
        assertThat(service.getCurrentUserGivenName()).isEqualTo("Alice");
        assertThat(service.getCurrentUserFamilyName()).isEqualTo("Xie");
        assertThat(service.getCurrentUserFullName()).isEqualTo("Alice Xie");
    }

    @Test
    void roles_strip_ROLE_prefix() {
        installJwt(jwt(Map.of("sub", "u")), "ROLE_ADMIN", "ROLE_USER", "SCOPE_read");
        assertThat(service.getCurrentUserRoles())
                .containsExactlyInAnyOrder("ADMIN", "USER", "SCOPE_read");
        assertThat(service.hasRole("ADMIN")).isTrue();
        assertThat(service.hasRole("MISSING")).isFalse();
        assertThat(service.isAdmin()).isTrue();
    }

    @Test
    void roles_empty_when_anonymous_unauthenticated() {
        // AnonymousAuthenticationToken is technically "authenticated" → ensure we still return roles list
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("k", "anon",
                        List.of(new SimpleGrantedAuthority("ROLE_ANON"))));
        assertThat(service.getCurrentUserRoles()).containsExactly("ANON");
    }

    @Test
    void isAuthenticated_true_when_a_jwt_is_installed() {
        installJwt(jwt(Map.of("sub", "u")));
        assertThat(service.isAuthenticated()).isTrue();
    }

    @Test
    void getAllClaims_returns_jwt_claims_map() {
        installJwt(jwt(Map.of("sub", "u", "custom", "yes")));
        assertThat(service.getAllClaims()).containsEntry("custom", "yes").containsEntry("sub", "u");
    }
}
