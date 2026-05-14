package com.finansportali.backend.repository;

import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketQuoteRepository extends JpaRepository<MarketQuote, Long> {

    Optional<MarketQuote> findTop1ByInstrumentOrderByAsOfDesc(MarketInstrument instrument);

    List<MarketQuote> findTop20ByOrderByAsOfDesc();

    Optional<MarketQuote> findTop1ByInstrument_SymbolOrderByAsOfDesc(String symbol);
}
