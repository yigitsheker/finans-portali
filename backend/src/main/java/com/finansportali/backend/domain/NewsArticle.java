package com.finansportali.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "news_articles")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 2000)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String sourceUrl;

    @Column(length = 100)
    private String sourceName;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false)
    private Instant publishedAt;

    public NewsArticle() {
    }

    public NewsArticle(String title, String summary, String category, Instant publishedAt) {
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.publishedAt = publishedAt;
    }

    public NewsArticle(String title, String summary, String category, Instant publishedAt, String sourceUrl, String sourceName) {
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.publishedAt = publishedAt;
        this.sourceUrl = sourceUrl;
        this.sourceName = sourceName;
    }

    public NewsArticle(String title, String summary, String content, String category, Instant publishedAt, String sourceUrl, String sourceName) {
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.category = category;
        this.publishedAt = publishedAt;
        this.sourceUrl = sourceUrl;
        this.sourceName = sourceName;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceName() { return sourceName; }

    public void setTitle(String title) { this.title = title; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setContent(String content) { this.content = content; }
    public void setCategory(String category) { this.category = category; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
}
