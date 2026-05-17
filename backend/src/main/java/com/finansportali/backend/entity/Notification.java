package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Per-user in-app notification record. Surfaced in the Topbar bell dropdown.
 * Created server-side whenever something noteworthy happens (price alert
 * triggered, system message, etc.).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "type", nullable = false, length = 40)
    private String type;            // "PRICE_ALERT", "SYSTEM"

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "reference_id", length = 100)
    private String referenceId;     // free-form, e.g. PriceAlert.id as string

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public Notification() {}

    public Notification(String userId, String type, String title, String message, String referenceId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceId = referenceId;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getReferenceId() { return referenceId; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setType(String type) { this.type = type; }
    public void setTitle(String title) { this.title = title; }
    public void setMessage(String message) { this.message = message; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public void setRead(boolean read) { this.read = read; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
