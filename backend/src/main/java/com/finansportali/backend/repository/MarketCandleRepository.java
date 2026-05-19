package com.finansportali.backend.repository;

import com.finansportali.backend.entity.MarketCandle;
import com.finansportali.backend.entity.MarketInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MarketCandleRepository extends JpaRepository<MarketCandle, Long> {

    List<MarketCandle> findByInstrumentAndDayBetweenOrderByDayAsc(
            MarketInstrument instrument, LocalDate start, LocalDate end
    );

    List<MarketCandle> findTop100ByInstrument_SymbolOrderByDayDesc(String symbol);

    List<MarketCandle> findTop50ByInstrument_SymbolOrderByDayDesc(String symbol);

    /** Two newest candles — used to derive daily change when the cached quote lacks it. */
    List<MarketCandle> findTop2ByInstrumentOrderByDayDesc(MarketInstrument instrument);

    void deleteByInstrumentAndDay(MarketInstrument instrument, LocalDate day);
    
    long countByInstrument(MarketInstrument instrument);
}
