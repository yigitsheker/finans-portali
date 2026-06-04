package com.finansportali.backend.service;

import com.finansportali.backend.config.KeycloakAdminProperties;
import com.finansportali.backend.dto.response.admin.KeycloakUserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client for Keycloak Admin REST API.
 *
 * Uses a confidential service-account client (finans-backend-admin) configured
 * in the finans realm. The client must have the realm-management roles:
 * view-users, manage-users, query-users.
 *
 * Tokens are cached in-process until ~30s before expiry to avoid hammering
 * the token endpoint on every admin call.
 */
@Service
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);
    private static final int DEFAULT_PAGE_SIZE = 100;

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_BY_ID_URI = "/admin/realms/{realm}/users/{id}";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_REQUIRED_ACTIONS = "requiredActions";
    private static final String FIELD_PHONE = "phone";
    private static final String ERR_USER_NOT_FOUND = "Kullanıcı bulunamadı";
    private static final String ERR_USER_FETCH_FAILED = "Kullanıcı alınamadı";

    private final KeycloakAdminProperties props;
    private WebClient webClient;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public KeycloakAdminService(KeycloakAdminProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        this.webClient = WebClient.builder()
                .baseUrl(props.getServerUrl())
                .build();
    }

    /**
     * List realm users, optionally filtered by a free-text {@code search} term,
     * with pagination via {@code first} offset and {@code max} page size.
     */
    public List<KeycloakUserDto> listUsers(String search, int first, int max) {
        String token = getAdminToken();
        int pageSize = max > 0 ? max : DEFAULT_PAGE_SIZE;

        List<Map<String, Object>> raw = webClient.get()
                .uri(uriBuilder -> {
                    var b = uriBuilder.path("/admin/realms/{realm}/users")
                            .queryParam("first", Math.max(first, 0))
                            .queryParam("max", pageSize)
                            .queryParam("briefRepresentation", false);
                    if (search != null && !search.isBlank()) {
                        b.queryParam("search", search.trim());
                    }
                    return b.build(props.getRealm());
                })
                .header(AUTH_HEADER, BEARER_PREFIX + token)
                .retrieve()
                .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .collectList()
                .block();

        if (raw == null) {
            return Collections.emptyList();
        }

        List<KeycloakUserDto> result = new ArrayList<>(raw.size());
        for (Map<String, Object> u : raw) {
            result.add(toDto(u));
        }
        return result;
    }

    /**
     * Fetch the current email for a user, bypassing any stale value cached locally.
     * Returns null if the user has no email or Keycloak rejects the lookup.
     * Used by notification dispatchers so an email change in Keycloak is picked up
     * immediately on the next alert without rewriting old rows.
     */
    public String getUserEmailById(String userId) {
        if (userId == null || userId.isBlank()) return null;
        String token = getAdminToken();
        try {
            Map<String, Object> user = webClient.get()
                    .uri(USER_BY_ID_URI, props.getRealm(), userId)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            if (user == null) return null;
            Object email = user.get(FIELD_EMAIL);
            return email == null ? null : email.toString();
        } catch (WebClientResponseException e) {
            log.warn("Could not fetch email for user {}: {} - {}", userId, e.getStatusCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Could not fetch email for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Update profile fields on a Keycloak user. Null/blank values are skipped so
     * callers can do partial updates. Phone is stored as a custom attribute
     * ({@code phone}) because Keycloak's core schema doesn't ship one.
     */
    public void updateUserProfile(String userId, String firstName, String lastName,
                                  String email, String phone) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
        String token = getAdminToken();

        // Fetch current user so we can merge — Keycloak PUT replaces top-level fields.
        Map<String, Object> current;
        try {
            current = webClient.get()
                    .uri(USER_BY_ID_URI, props.getRealm(), userId)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException e) {
            throw translate(e, ERR_USER_FETCH_FAILED);
        }
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ERR_USER_NOT_FOUND);
        }

        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>(current);
        if (firstName != null && !firstName.isBlank()) body.put("firstName", firstName.trim());
        if (lastName != null && !lastName.isBlank()) body.put("lastName", lastName.trim());
        if (email != null && !email.isBlank()) {
            body.put(FIELD_EMAIL, email.trim());
            body.put("emailVerified", true); // self-edit, trust it
        }

        if (phone != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> attrs = (Map<String, Object>) body.getOrDefault("attributes", new java.util.LinkedHashMap<>());
            java.util.LinkedHashMap<String, Object> newAttrs = new java.util.LinkedHashMap<>(attrs);
            if (phone.isBlank()) newAttrs.remove(FIELD_PHONE);
            else newAttrs.put(FIELD_PHONE, java.util.List.of(phone.trim()));
            body.put("attributes", newAttrs);
        }

        try {
            webClient.put()
                    .uri(USER_BY_ID_URI, props.getRealm(), userId)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Updated profile for user {}", userId);
        } catch (WebClientResponseException e) {
            throw translate(e, "Profil güncellenemedi");
        }
    }

    /** Enable or disable a user account in Keycloak. */
    public void setUserEnabled(String userId, boolean enabled) {
        String token = getAdminToken();
        Map<String, Object> body = Map.of("enabled", enabled);

        try {
            webClient.put()
                    .uri(USER_BY_ID_URI, props.getRealm(), userId)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Set user {} enabled={}", userId, enabled);
        } catch (WebClientResponseException e) {
            throw translate(e, "Kullanıcı durumu güncellenemedi");
        }
    }

    /**
     * Add a required action (e.g. UPDATE_PASSWORD, CONFIGURE_TOTP) to the user,
     * merging with any existing actions so the user must complete it at next login.
     */
    public void addRequiredAction(String userId, String action) {
        String token = getAdminToken();

        Map<String, Object> current;
        try {
            current = webClient.get()
                    .uri(USER_BY_ID_URI, props.getRealm(), userId)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException e) {
            throw translate(e, ERR_USER_FETCH_FAILED);
        }

        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ERR_USER_NOT_FOUND);
        }

        @SuppressWarnings("unchecked")
        List<String> existing = (List<String>) current.getOrDefault(FIELD_REQUIRED_ACTIONS, Collections.emptyList());
        List<String> merged = new ArrayList<>(existing);
        if (!merged.contains(action)) {
            merged.add(action);
        }

        Map<String, Object> body = Map.of(FIELD_REQUIRED_ACTIONS, merged);
        try {
            webClient.put()
                    .uri(USER_BY_ID_URI, props.getRealm(), userId)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Added required action '{}' to user {}", action, userId);
        } catch (WebClientResponseException e) {
            throw translate(e, "Gerekli eylem eklenemedi");
        }
    }

    /**
     * Whether the user currently has at least one OTP/TOTP credential.
     * Read-only mirror of {@link #removeTotpCredentials}'s credential scan.
     *
     * <p>The Settings page uses this for self-service 2FA status: Keycloak's
     * access token carries no "has TOTP" claim, so the only reliable source of
     * truth is the user's credential list via the Admin REST API.
     */
    public boolean hasTotpCredential(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        String token = getAdminToken();
        List<Map<String, Object>> creds;
        try {
            creds = webClient.get()
                    .uri("/admin/realms/{realm}/users/{id}/credentials", props.getRealm(), userId)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .retrieve()
                    .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .collectList()
                    .block();
        } catch (WebClientResponseException e) {
            throw translate(e, "Kimlik bilgileri alınamadı");
        }
        if (creds == null) {
            return false;
        }
        for (Map<String, Object> cred : creds) {
            String type = Objects.toString(cred.get("type"), "");
            if ("otp".equalsIgnoreCase(type) || "totp".equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    /** Delete all OTP/TOTP credentials for the user, effectively disabling 2FA. */
    public void removeTotpCredentials(String userId) {
        String token = getAdminToken();

        List<Map<String, Object>> creds;
        try {
            creds = webClient.get()
                    .uri("/admin/realms/{realm}/users/{id}/credentials", props.getRealm(), userId)
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .retrieve()
                    .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .collectList()
                    .block();
        } catch (WebClientResponseException e) {
            throw translate(e, "Kimlik bilgileri alınamadı");
        }

        if (creds == null) {
            return;
        }

        for (Map<String, Object> cred : creds) {
            String type = Objects.toString(cred.get("type"), "");
            if (!"otp".equalsIgnoreCase(type) && !"totp".equalsIgnoreCase(type)) {
                continue;
            }
            String credId = Objects.toString(cred.get("id"), null);
            if (credId == null) continue;
            try {
                webClient.delete()
                        .uri("/admin/realms/{realm}/users/{uid}/credentials/{cid}",
                                props.getRealm(), userId, credId)
                        .header(AUTH_HEADER, BEARER_PREFIX + token)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
            } catch (WebClientResponseException e) {
                log.warn("Failed to delete OTP credential {} for user {}: {}", credId, userId, e.getMessage());
            }
        }
    }

    private synchronized String getAdminToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.getAdminClientId());
        form.add("client_secret", props.getAdminClientSecret());

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", props.getRealm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || response.get("access_token") == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Keycloak admin token alınamadı");
            }

            String token = response.get("access_token").toString();
            int expiresIn = ((Number) response.getOrDefault("expires_in", 60)).intValue();
            this.cachedToken = token;
            this.cachedTokenExpiry = Instant.now().plus(Duration.ofSeconds(Math.max(expiresIn - 30, 10)));
            return token;
        } catch (WebClientResponseException e) {
            log.error("Keycloak admin token request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak'a bağlanılamadı");
        }
    }

    @SuppressWarnings("unchecked")
    private KeycloakUserDto toDto(Map<String, Object> u) {
        List<String> requiredActions = (List<String>) u.getOrDefault(FIELD_REQUIRED_ACTIONS, Collections.emptyList());

        boolean totpEnabled = false;
        Object totp = u.get("totp");
        if (totp instanceof Boolean b) {
            totpEnabled = b;
        }
        // Newer Keycloak versions expose this through 'credentials' which we don't fetch here.
        // The 'totp' field is the canonical hint for whether OTP is configured.

        Long createdTs = null;
        Object created = u.get("createdTimestamp");
        if (created instanceof Number n) {
            createdTs = n.longValue();
        }

        return new KeycloakUserDto(
                Objects.toString(u.get("id"), null),
                Objects.toString(u.get("username"), null),
                Objects.toString(u.get(FIELD_EMAIL), null),
                Objects.toString(u.get("firstName"), null),
                Objects.toString(u.get("lastName"), null),
                u.get("enabled") instanceof Boolean be ? be : true,
                u.get("emailVerified") instanceof Boolean bv && bv,
                createdTs,
                requiredActions,
                totpEnabled
        );
    }

    private ResponseStatusException translate(WebClientResponseException e, String fallback) {
        log.warn("Keycloak admin API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        if (e.getStatusCode().value() == 404) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, ERR_USER_NOT_FOUND);
        }
        if (e.getStatusCode().value() == 403) {
            return new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Backend service hesabının yetkisi yok (realm-management rolleri kontrol edin)");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, fallback);
    }
}
