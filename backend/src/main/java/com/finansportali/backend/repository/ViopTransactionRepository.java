package com.finansportali.backend.repository;

import com.finansportali.backend.entity.ViopTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViopTransactionRepository extends JpaRepository<ViopTransaction, Long> {

    List<ViopTransaction> findByUserIdOrderByExecutedAtDesc(String userId);

    List<ViopTransaction> findByUserIdAndContractSymbolOrderByExecutedAtDesc(String userId, String contractSymbol);
}
