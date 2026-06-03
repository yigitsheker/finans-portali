package com.finansportali.backend.repository;

import com.finansportali.backend.entity.BondPosition;
import com.finansportali.backend.entity.BondPositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BondPositionRepository extends JpaRepository<BondPosition, Long> {

    List<BondPosition> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<BondPosition> findByUserIdAndIsin(String userId, String isin);

    List<BondPosition> findByStatus(BondPositionStatus status);
}
