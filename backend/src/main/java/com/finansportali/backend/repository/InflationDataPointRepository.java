package com.finansportali.backend.repository;

import com.finansportali.backend.entity.InflationDataPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InflationDataPointRepository extends JpaRepository<InflationDataPoint, Long> {

    Optional<InflationDataPoint> findByPeriodDate(LocalDate periodDate);

    List<InflationDataPoint> findAllByOrderByPeriodDateAsc();

    @Query("SELECT i FROM InflationDataPoint i ORDER BY i.periodDate DESC")
    List<InflationDataPoint> findAllOrderByPeriodDateDesc();

    /**
     * Find the row whose periodDate is the largest one not after the given target.
     * Used when comparing a historical buy date to the closest available inflation month.
     */
    @Query("SELECT i FROM InflationDataPoint i WHERE i.periodDate <= :target ORDER BY i.periodDate DESC LIMIT 1")
    Optional<InflationDataPoint> findLatestOnOrBefore(@Param("target") LocalDate target);
}
