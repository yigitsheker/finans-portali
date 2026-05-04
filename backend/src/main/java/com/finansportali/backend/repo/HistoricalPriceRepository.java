package com.finansportali.backend.repo;

import com.finansportali.backend.domain.HistoricalPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HistoricalPriceRepository extends JpaRepository<HistoricalPrice, Long> {
    
    Optional<HistoricalPrice> findBySymbolAndPriceDate(String symbol, LocalDate priceDate);
    
    List<HistoricalPrice> findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
            String symbol, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT hp FROM HistoricalPrice hp WHERE hp.symbol = :symbol " +
           "AND hp.priceDate <= :date ORDER BY hp.priceDate DESC LIMIT 1")
    Optional<HistoricalPrice> findLatestPriceOnOrBefore(
            @Param("symbol") String symbol, @Param("date") LocalDate date);
    
    @Query("SELECT COUNT(hp) FROM HistoricalPrice hp WHERE hp.symbol = :symbol " +
           "AND hp.priceDate BETWEEN :startDate AND :endDate")
    long countBySymbolAndDateRange(
            @Param("symbol") String symbol, 
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate);
    
    boolean existsBySymbolAndPriceDate(String symbol, LocalDate priceDate);
}
