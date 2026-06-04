package com.finansportali.backend.controller;

import com.finansportali.backend.service.KeycloakAdminService;
import com.finansportali.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Self-service profile endpoint. The user can edit their own name, email and
 * phone (Keycloak custom attribute). UserId is resolved from the JWT — there
 * is no userId path parameter to prevent one user editing another's profile.
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final UserService userService;
    private final KeycloakAdminService keycloakAdminService;

    public UserProfileController(UserService userService, KeycloakAdminService keycloakAdminService) {
        this.userService = userService;
        this.keycloakAdminService = keycloakAdminService;
    }

    /** Request body for a profile update; {@code phone} is stored as a Keycloak custom attribute. */
    public record ProfileUpdate(String firstName, String lastName, String email, String phone) {}

    /** Updates the current user's name, email and phone via the Keycloak Admin API. */
    @PatchMapping
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody ProfileUpdate req) {
        String userId = userService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        keycloakAdminService.updateUserProfile(userId, req.firstName(), req.lastName(), req.email(), req.phone());
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "updated", true
        ));
    }

    /**
     * Self-service 2FA status. Returns whether the current user has a TOTP
     * authenticator configured, so the Settings page can show "active" + a
     * Disable action instead of always offering "set up" (Keycloak emits no
     * such token claim, hence the Admin-REST credential lookup).
     */
    @GetMapping("/security")
    public ResponseEntity<Map<String, Object>> security() {
        String userId = userService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(Map.of(
                "totpEnabled", keycloakAdminService.hasTotpCredential(userId)
        ));
    }

    /**
     * Self-service 2FA disable: removes the user's own TOTP credential(s).
     * Re-enrolling is done via Keycloak's CONFIGURE_TOTP flow from the UI.
     */
    @DeleteMapping("/2fa")
    public ResponseEntity<Map<String, Object>> disable2fa() {
        String userId = userService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        keycloakAdminService.removeTotpCredentials(userId);
        return ResponseEntity.ok(Map.of(
                "totpEnabled", false
        ));
    }
}
