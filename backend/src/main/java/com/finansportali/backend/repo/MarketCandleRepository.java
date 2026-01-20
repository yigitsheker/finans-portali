package com.finansportali.backend.repo;

import com.finansportali.backend.domain.MarketCandle;
import com.finansportali.backend.domain.MarketInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MarketCandleRepository extends JpaRepository<MarketCandle, Long> {

    List<MarketCandle> findByInstrumentAndDayBetweenOrderByDayAsc(
            MarketInstrument instrument, LocalDate start, LocalDate end
    );
}
