package com.finansportali.backend.repository;

import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DebtInstrumentRepository extends JpaRepository<DebtInstrument, Long>, JpaSpecificationExecutor<DebtInstrument> {

    Optional<DebtInstrument> findBySymbol(String symbol);

    Optional<DebtInstrument> findByIsin(String isin);

    List<DebtInstrument> findByType(DebtInstrumentType type);

    List<DebtInstrument> findByActiveTrue();

    List<DebtInstrument> findByActiveTrueAndType(DebtInstrumentType type);

    /**
     * Active instruments that this {@code source} has previously quoted but
     * whose symbol is NOT in the latest refresh batch — i.e. retired symbols
     * and matured bonds that should be soft-deleted from the listing. Scoped by
     * source so a TCMB refresh never deactivates DEMO/BIST rows.
     */
    @Query("""
            select distinct i from DebtInstrument i
            where i.active = true
              and i.symbol not in :symbols
              and exists (
                  select 1 from DebtInstrumentQuote q
                  where q.instrument = i and q.source = :source)
            """)
    List<DebtInstrument> findActiveManagedBySourceExcluding(@Param("source") String source,
                                                            @Param("symbols") java.util.Collection<String> symbols);
}
