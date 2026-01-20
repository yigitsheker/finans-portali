package com.finansportali.backend.repo;

import com.finansportali.backend.domain.MarketInstrument;
import com.finansportali.backend.domain.MarketQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketQuoteRepository extends JpaRepository<MarketQuote, Long> {

    Optional<MarketQuote> findTop1ByInstrumentOrderByAsOfDesc(MarketInstrument instrument);

    List<MarketQuote> findTop20ByOrderByAsOfDesc();
}
