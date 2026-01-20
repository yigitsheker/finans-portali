package com.finansportali.backend.repo;

import com.finansportali.backend.domain.MarketInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketInstrumentRepository extends JpaRepository<MarketInstrument, Long> {
    Optional<MarketInstrument> findBySymbol(String symbol);
}
