package com.finansportali.backend.repository;

import com.finansportali.backend.entity.MarketInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketInstrumentRepository extends JpaRepository<MarketInstrument, Long> {
    Optional<MarketInstrument> findBySymbol(String symbol);
}
