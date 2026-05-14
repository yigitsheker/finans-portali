package com.finansportali.backend.repository;

import com.finansportali.backend.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Watchlist> findByIdAndUserId(Long id, String userId);
    long deleteByIdAndUserId(Long id, String userId);
}
