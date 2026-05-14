package com.finansportali.backend.repository;

import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DebtInstrumentQuoteRepository extends JpaRepository<DebtInstrumentQuote, Long> {

    Optional<DebtInstrumentQuote> findByInstrumentAndQuoteDateAndSource(
        DebtInstrument instrument, LocalDate quoteDate, String source
    );

    @Query("SELECT q FROM DebtInstrumentQuote q WHERE q.instrument = :instrument " +
           "ORDER BY q.quoteDate DESC, q.createdAt DESC LIMIT 1")
    Optional<DebtInstrumentQuote> findLatestByInstrument(@Param("instrument") DebtInstrument instrument);

    @Query("SELECT q FROM DebtInstrumentQuote q WHERE q.instrument = :instrument " +
           "AND q.quoteDate BETWEEN :from AND :to ORDER BY q.quoteDate ASC")
    List<DebtInstrumentQuote> findHistoricalQuotes(
        @Param("instrument") DebtInstrument instrument,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("SELECT MAX(q.quoteDate) FROM DebtInstrumentQuote q")
    Optional<LocalDate> findLatestQuoteDate();

    List<DebtInstrumentQuote> findByInstrumentAndQuoteDateBetween(
        DebtInstrument instrument, LocalDate from, LocalDate to
    );
}
