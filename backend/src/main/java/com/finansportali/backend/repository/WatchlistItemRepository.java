package com.finansportali.backend.repository;

import com.finansportali.backend.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByWatchlistIdOrderByAddedAtDesc(Long watchlistId);
    Optional<WatchlistItem> findByWatchlistIdAndSymbol(Long watchlistId, String symbol);
    long deleteByWatchlistIdAndSymbol(Long watchlistId, String symbol);
    long deleteByWatchlistId(Long watchlistId);
}
