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

    public record ProfileUpdate(String firstName, String lastName, String email, String phone) {}

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
}
