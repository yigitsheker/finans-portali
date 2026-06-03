package com.finansportali.backend.repository;

import com.finansportali.backend.entity.BondTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BondTransactionRepository extends JpaRepository<BondTransaction, Long> {

    List<BondTransaction> findByUserIdOrderByExecutedAtDesc(String userId);

    List<BondTransaction> findByUserIdAndIsinOrderByExecutedAtDesc(String userId, String isin);
}
