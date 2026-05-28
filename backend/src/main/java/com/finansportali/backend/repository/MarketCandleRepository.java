package com.finansportali.backend.repository;

import com.finansportali.backend.entity.MarketCandle;
import com.finansportali.backend.entity.MarketInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketCandleRepository extends JpaRepository<MarketCandle, Long> {

    List<MarketCandle> findByInstrumentAndDayBetweenOrderByDayAsc(
            MarketInstrument instrument, LocalDate start, LocalDate end
    );

    List<MarketCandle> findTop100ByInstrument_SymbolOrderByDayDesc(String symbol);

    List<MarketCandle> findTop50ByInstrument_SymbolOrderByDayDesc(String symbol);

    /** Two newest candles — used to derive daily change when the cached quote lacks it. */
    List<MarketCandle> findTop2ByInstrumentOrderByDayDesc(MarketInstrument instrument);

    /**
     * Lookup used by the upsert path in PriceRefreshScheduler. Hibernate
     * orders inserts before deletes when both touch the same row, so a
     * naive delete-then-save inside one transaction hits the
     * (instrument_id, day) unique constraint — find-and-mutate sidesteps it.
     */
    Optional<MarketCandle> findByInstrumentAndDay(MarketInstrument instrument, LocalDate day);

    void deleteByInstrumentAndDay(MarketInstrument instrument, LocalDate day);

    long countByInstrument(MarketInstrument instrument);
}
