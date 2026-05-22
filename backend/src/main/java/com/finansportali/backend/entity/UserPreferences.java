package com.finansportali.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Per-user preferences keyed by Keycloak subject (the JWT 'sub' claim).
 *
 * Currently only holds the notification-toggle map serialised as JSON
 * — adding a new preference category should mean adding a column here,
 * not yet another table. The Settings page is the only writer; readers
 * are the page itself and notify.js (gating toasts by category).
 */
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name = "user_id", length = 100)
    private String userId;

    /** JSON-encoded map of category → boolean (e.g. {"transactions":true,"push":false}). */
    @Column(name = "notification_prefs", columnDefinition = "TEXT")
    private String notificationPrefs;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public UserPreferences() {}

    public UserPreferences(String userId, String notificationPrefs) {
        this.userId = userId;
        this.notificationPrefs = notificationPrefs;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getNotificationPrefs() { return notificationPrefs; }
    public void setNotificationPrefs(String notificationPrefs) { this.notificationPrefs = notificationPrefs; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
