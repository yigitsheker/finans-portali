package com.finansportali.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finansportali.backend.entity.UserPreferences;
import com.finansportali.backend.repository.UserPreferencesRepository;
import com.finansportali.backend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Per-user notification preferences. The Settings page reads on mount and
 * writes (debounced) on every toggle so the user's choice survives across
 * browsers and devices, not just the originating localStorage.
 *
 * Endpoint shape stays simple — a flat string → boolean map — so the
 * frontend doesn't need a custom DTO and the backend doesn't need a
 * stronger schema than "JSON we wrote, JSON we read back".
 */
@RestController
@RequestMapping("/api/v1/users/me/notification-prefs")
public class UserPreferencesController {

    private static final Logger log = LoggerFactory.getLogger(UserPreferencesController.class);
    private static final TypeReference<Map<String, Boolean>> MAP_TYPE = new TypeReference<>() {};

    private final UserPreferencesRepository repo;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public UserPreferencesController(UserPreferencesRepository repo,
                                     UserService userService,
                                     ObjectMapper objectMapper) {
        this.repo = repo;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /** Returns the current user's stored notification preferences (empty map if none). */
    @GetMapping
    public ResponseEntity<Map<String, Boolean>> get() {
        String userId = userService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        Map<String, Boolean> prefs = repo.findById(userId)
                .map(this::deserialize)
                .orElse(Map.of());
        return ResponseEntity.ok(prefs);
    }

    /** Replaces the current user's notification preferences, returning the saved map. */
    @PutMapping
    public ResponseEntity<Map<String, Boolean>> put(@RequestBody Map<String, Boolean> incoming) {
        String userId = userService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        if (incoming == null) return ResponseEntity.badRequest().build();

        // Strip non-boolean values defensively (Jackson would normally do
        // this, but a malformed client could still send strings/nulls).
        Map<String, Boolean> clean = new java.util.LinkedHashMap<>();
        incoming.forEach((k, v) -> {
            if (k != null && v != null) clean.put(k, v);
        });

        String json;
        try {
            json = objectMapper.writeValueAsString(clean);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise notification prefs for {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }

        UserPreferences row = repo.findById(userId)
                .orElseGet(() -> new UserPreferences(userId, null));
        row.setNotificationPrefs(json);
        repo.save(row);
        return ResponseEntity.ok(clean);
    }

    private Map<String, Boolean> deserialize(UserPreferences row) {
        String json = row.getNotificationPrefs();
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Stored notification prefs for {} were unparseable; treating as empty", row.getUserId());
            return Map.of();
        }
    }
}
