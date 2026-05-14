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
}
