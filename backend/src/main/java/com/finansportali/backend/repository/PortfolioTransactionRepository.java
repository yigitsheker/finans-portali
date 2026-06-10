package com.finansportali.backend.repository;

import com.finansportali.backend.entity.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {

    List<PortfolioTransaction> findByUserIdOrderByExecutedAtDesc(String userId);

    void deleteByUserId(String userId);
}
