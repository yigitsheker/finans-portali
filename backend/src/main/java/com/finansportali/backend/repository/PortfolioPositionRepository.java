package com.finansportali.backend.repository;

import com.finansportali.backend.entity.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    List<PortfolioPosition> findByUserId(String userId);

    Optional<PortfolioPosition> findByUserIdAndSymbol(String userId, String symbol);

    @Transactional
    long deleteByUserIdAndSymbol(String userId, String symbol);

    @Transactional
    void deleteByUserId(String userId);
}
