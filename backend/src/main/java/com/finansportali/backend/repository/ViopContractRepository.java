package com.finansportali.backend.repository;

import com.finansportali.backend.entity.ViopContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ViopContractRepository extends JpaRepository<ViopContract, Long> {

    Optional<ViopContract> findBySymbol(String symbol);

    List<ViopContract> findByCategoryOrderByMaturityYearAscMaturityMonthAscUnderlyingAsc(
            ViopContract.Category category);

    List<ViopContract> findAllByOrderByCategoryAscUnderlyingAscMaturityYearAscMaturityMonthAsc();
}
