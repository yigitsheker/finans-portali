package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Admin-managed RSS feed source. Replaces the hard-coded list in NewsService.
 * One row per upstream URL; the service iterates active rows every cycle.
 */
@Entity
@Table(name = "news_feeds")
public class NewsFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, unique = true, length = 500)
    private String url;

    @Column(name = "category", nullable = false, length = 60)
    private String category;

    @Column(name = "source", nullable = false, length = 120)
    private String source;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public NewsFeed() {}

    public NewsFeed(String url, String category, String source) {
        this.url = url;
        this.category = category;
        this.source = source;
    }

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getCategory() { return category; }
    public String getSource() { return source; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUrl(String url) { this.url = url; }
    public void setCategory(String category) { this.category = category; }
    public void setSource(String source) { this.source = source; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
