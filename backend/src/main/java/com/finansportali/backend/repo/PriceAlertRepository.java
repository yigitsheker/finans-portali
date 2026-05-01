package com.finansportali.backend.repo;

import com.finansportali.backend.domain.PriceAlert;
import com.finansportali.backend.domain.MarketInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    /** Kullanıcının aktif alarmları */
    @EntityGraph(attributePaths = {"instrument"})
    List<PriceAlert> findByUserIdAndActiveOrderByCreatedAtDesc(String userId, Boolean active);

    /** Kullanıcının tüm alarmları (aktif + tetiklenmiş) */
    @EntityGraph(attributePaths = {"instrument"})
    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Belirli sembol için kullanıcının aktif alarmları */
    List<PriceAlert> findByUserIdAndSymbolAndActiveOrderByCreatedAtDesc(String userId, String symbol, Boolean active);

    /** Tüm aktif alarmlar (fiyat kontrolü için) */
    List<PriceAlert> findByActiveOrderBySymbol(Boolean active);

    /** Belirli enstrüman için aktif alarmlar */
    List<PriceAlert> findByInstrumentAndActiveOrderByTargetPrice(MarketInstrument instrument, Boolean active);

    /** Kullanıcının aktif alarm sayısı */
    long countByUserIdAndActive(String userId, Boolean active);

    /** Sistem geneli aktif alarm sayısı */
    @Query("SELECT COUNT(a) FROM PriceAlert a WHERE a.active = true")
    long countActiveAlerts();

    /** Son tetiklenen alarmlar */
    @Query("SELECT a FROM PriceAlert a WHERE a.active = false AND a.triggeredAt IS NOT NULL ORDER BY a.triggeredAt DESC")
    List<PriceAlert> findRecentlyTriggeredAlerts();
}