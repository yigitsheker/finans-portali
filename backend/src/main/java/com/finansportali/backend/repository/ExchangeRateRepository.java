package com.finansportali.backend.repository;

import com.finansportali.backend.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("SELECT e FROM ExchangeRate e WHERE e.rateDate = :date ORDER BY e.currencyCode")
    List<ExchangeRate> findByRateDate(@Param("date") LocalDate date);

    @Query("SELECT e FROM ExchangeRate e WHERE e.source = :source AND e.rateDate = :date ORDER BY e.currencyCode")
    List<ExchangeRate> findBySourceAndRateDate(@Param("source") String source, @Param("date") LocalDate date);

    @Query("SELECT e FROM ExchangeRate e WHERE e.currencyCode = :currencyCode ORDER BY e.rateDate DESC")
    List<ExchangeRate> findByCurrencyCodeOrderByRateDateDesc(@Param("currencyCode") String currencyCode);

    @Query("SELECT DISTINCT e.source FROM ExchangeRate e ORDER BY e.source")
    List<String> findDistinctSources();

    @Query("SELECT e FROM ExchangeRate e WHERE e.rateDate = (SELECT MAX(er.rateDate) FROM ExchangeRate er WHERE er.source = e.source) ORDER BY e.source, e.currencyCode")
    List<ExchangeRate> findLatestRatesBySource();
}