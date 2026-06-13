package com.finansportali.backend.repository;

import com.finansportali.backend.entity.HistoricalPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoricalPositionRepository extends JpaRepository<HistoricalPosition, Long> {
    List<HistoricalPosition> findByUserIdOrderByBuyDateDesc(String userId);
    void deleteByIdAndUserId(Long id, String userId);
    boolean existsByIdAndUserId(Long id, String userId);
}
