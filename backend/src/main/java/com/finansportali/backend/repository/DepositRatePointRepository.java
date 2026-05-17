package com.finansportali.backend.repository;

import com.finansportali.backend.entity.DepositRatePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepositRatePointRepository extends JpaRepository<DepositRatePoint, Long> {

    Optional<DepositRatePoint> findByPeriodDateAndCurrency(LocalDate periodDate, String currency);

    List<DepositRatePoint> findByCurrencyOrderByPeriodDateAsc(String currency);

    @Query("SELECT d FROM DepositRatePoint d WHERE d.currency = :currency AND d.periodDate <= :target ORDER BY d.periodDate DESC LIMIT 1")
    Optional<DepositRatePoint> findLatestOnOrBefore(@Param("currency") String currency, @Param("target") LocalDate target);
}
