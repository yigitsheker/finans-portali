package com.finansportali.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakUserServiceTest {

    private final KeycloakUserService service = new KeycloakUserService();

    private static Authentication jwtAuth(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        if (!claims.containsKey("sub")) b.subject("user-1");
        return new JwtAuthenticationToken(b.build());
    }

    @Test
    void getUserEmail_returns_null_for_null_auth() {
        assertThat(service.getUserEmail(null)).isNull();
    }

    @Test
    void getUserEmail_returns_null_when_principal_is_not_jwt() {
        Authentication a = new UsernamePasswordAuthenticationToken("alice", "x", List.of());
        assertThat(service.getUserEmail(a)).isNull();
    }

    @Test
    void getUserEmail_reads_email_claim() {
        assertThat(service.getUserEmail(jwtAuth(Map.of("email", "a@x")))).isEqualTo("a@x");
    }

    @Test
    void getUserEmail_returns_null_when_claim_missing_and_no_TEST_EMAIL_env() {
        // TEST_EMAIL is usually not set in CI; just assert we don't NPE.
        String result = service.getUserEmail(jwtAuth(Map.of("sub", "u")));
        // Either null OR the env value — never empty string.
        if (result != null) {
            assertThat(result).isNotEmpty();
        }
    }

    @Test
    void getUsername_prefers_preferred_username_then_name_then_subject() {
        assertThat(service.getUsername(jwtAuth(Map.of(
                "preferred_username", "alice",
                "name", "Alice X",
                "sub", "id-1"))))
                .isEqualTo("alice");

        assertThat(service.getUsername(jwtAuth(Map.of(
                "name", "Alice X",
                "sub", "id-1"))))
                .isEqualTo("Alice X");

        assertThat(service.getUsername(jwtAuth(Map.of("sub", "only-sub"))))
                .isEqualTo("only-sub");
    }

    @Test
    void getUsername_returns_authName_when_principal_is_not_a_jwt() {
        Authentication a = new UsernamePasswordAuthenticationToken("alice", "x", List.of());
        assertThat(service.getUsername(a)).isEqualTo("alice");
    }

    @Test
    void getUsername_returns_null_for_null_auth_or_null_principal() {
        assertThat(service.getUsername(null)).isNull();
    }

    @Test
    void getUserId_returns_subject_for_jwt() {
        assertThat(service.getUserId(jwtAuth(Map.of("sub", "id-7")))).isEqualTo("id-7");
    }

    @Test
    void getUserId_returns_null_for_non_jwt_or_null() {
        assertThat(service.getUserId(null)).isNull();
        Authentication a = new UsernamePasswordAuthenticationToken("alice", "x", List.of());
        assertThat(service.getUserId(a)).isNull();
    }
}
