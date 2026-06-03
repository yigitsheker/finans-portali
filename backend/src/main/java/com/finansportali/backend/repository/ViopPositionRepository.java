package com.finansportali.backend.repository;

import com.finansportali.backend.entity.ViopPosition;
import com.finansportali.backend.entity.ViopPositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ViopPositionRepository extends JpaRepository<ViopPosition, Long> {

    List<ViopPosition> findByUserIdOrderByOpenedAtDesc(String userId);

    List<ViopPosition> findByUserIdAndStatus(String userId, ViopPositionStatus status);

    Optional<ViopPosition> findByUserIdAndContractSymbol(String userId, String contractSymbol);

    /** Open positions whose contract has matured — picked up by the expiry job. */
    List<ViopPosition> findByStatusAndMaturityDateLessThanEqual(ViopPositionStatus status, LocalDate date);
}
