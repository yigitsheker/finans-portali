package com.finansportali.backend.repository;

import com.finansportali.backend.entity.InflationDataPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InflationDataPointRepository extends JpaRepository<InflationDataPoint, Long> {

    // ── Country-aware queries (preferred — call these from the service) ─────

    Optional<InflationDataPoint> findByPeriodDateAndCountry(LocalDate periodDate, String country);

    List<InflationDataPoint> findAllByCountryOrderByPeriodDateAsc(String country);

    @Query("SELECT i FROM InflationDataPoint i WHERE i.country = :country AND i.periodDate <= :target ORDER BY i.periodDate DESC LIMIT 1")
    Optional<InflationDataPoint> findLatestOnOrBefore(@Param("target") LocalDate target,
                                                     @Param("country") String country);

    // ── Legacy single-country wrappers (default to TR for back-compat) ──────
    // Old callers still exist in compare() etc.; these keep them working
    // while we migrate the call sites in the service.

    default Optional<InflationDataPoint> findByPeriodDate(LocalDate periodDate) {
        return findByPeriodDateAndCountry(periodDate, "TR");
    }

    default List<InflationDataPoint> findAllByOrderByPeriodDateAsc() {
        return findAllByCountryOrderByPeriodDateAsc("TR");
    }

    default Optional<InflationDataPoint> findLatestOnOrBefore(LocalDate target) {
        return findLatestOnOrBefore(target, "TR");
    }
}
