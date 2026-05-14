package com.finansportali.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist_items", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"watchlist_id", "symbol"}))
public class WatchlistItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "watchlist_id", nullable = false)
    private Long watchlistId;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(name = "added_at")
    private LocalDateTime addedAt;
    
    public WatchlistItem() {
        this.addedAt = LocalDateTime.now();
    }
    
    public WatchlistItem(Long watchlistId, String symbol) {
        this();
        this.watchlistId = watchlistId;
        this.symbol = symbol;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getWatchlistId() {
        return watchlistId;
    }
    
    public void setWatchlistId(Long watchlistId) {
        this.watchlistId = watchlistId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public LocalDateTime getAddedAt() {
        return addedAt;
    }
    
    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}
