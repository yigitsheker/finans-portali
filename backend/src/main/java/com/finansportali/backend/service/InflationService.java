package com.finansportali.backend.service;

import com.finansportali.backend.dto.response.inflation.InflationCompareDto;
import com.finansportali.backend.entity.InflationDataPoint;
import com.finansportali.backend.repository.InflationDataPointRepository;
import com.finansportali.backend.service.client.inflation.TcmbInflationFetcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class InflationService {

    private static final Logger log = LoggerFactory.getLogger(InflationService.class);

    private final InflationDataPointRepository repo;
    private final TcmbInflationFetcher fetcher;

    public InflationService(InflationDataPointRepository repo, TcmbInflationFetcher fetcher) {
        this.repo = repo;
        this.fetcher = fetcher;
    }

    @PostConstruct
    void onStartup() {
        if (repo.count() == 0) {
            log.info("[Inflation] DB empty — triggering first sync from TCMB");
            try {
                refresh();
            } catch (Exception e) {
                log.warn("[Inflation] Startup sync failed: {}", e.getMessage());
            }
        }
    }

    /** Refresh ~10 years of monthly history from TCMB EVDS3. Idempotent. */
    @Transactional
    @CacheEvict(value = {"inflation-all", "inflation-latest"}, allEntries = true)
    public int refresh() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(10).withDayOfMonth(1);

        List<InflationDataPoint> fresh = fetcher.fetchInflationHistory(from, to);
        if (fresh.isEmpty()) {
            log.warn("[Inflation] No data from TCMB; DB left untouched");
            return 0;
        }

        int upserts = 0;
        for (InflationDataPoint p : fresh) {
            InflationDataPoint persisted = repo.findByPeriodDate(p.getPeriodDate())
                    .orElseGet(() -> new InflationDataPoint(p.getPeriodDate()));
            persisted.setCpiIndex(p.getCpiIndex());
            persisted.setCpiYearlyChange(p.getCpiYearlyChange());
            persisted.setCpiMonthlyChange(p.getCpiMonthlyChange());
            persisted.setPpiIndex(p.getPpiIndex());
            persisted.setPpiYearlyChange(p.getPpiYearlyChange());
            persisted.setSource("TCMB_EVDS3");
            persisted.setUpdatedAt(java.time.LocalDateTime.now());
            repo.save(persisted);
            upserts++;
        }
        log.info("[Inflation] Refreshed {} monthly rows", upserts);
        return upserts;
    }

    /**
     * Scheduled refresh: TÜFE typically released on the 3rd-5th of each month.
     * Run daily at 09:00 local time; refresh is idempotent so re-running is cheap.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("[Inflation] Scheduled refresh failed: {}", e.getMessage());
        }
    }

    @Cacheable("inflation-all")
    public List<InflationDataPoint> getAllAscending() {
        return repo.findAllByOrderByPeriodDateAsc();
    }

    @Cacheable("inflation-latest")
    public Optional<InflationDataPoint> getLatest() {
        return repo.findLatestOnOrBefore(LocalDate.now());
    }

    public Optional<InflationDataPoint> getOnOrBefore(LocalDate target) {
        return repo.findLatestOnOrBefore(target);
    }

    /**
     * Compute cumulative CPI inflation between two dates and the resulting real return,
     * given the user's nominal return percentage for the same window.
     *
     * Real return formula: r_real = (1 + r_nominal) / (1 + r_infl) - 1
     */
    public Optional<InflationCompareDto> compare(LocalDate fromDate,
                                                 LocalDate toDate,
                                                 BigDecimal nominalReturnPct) {
        Optional<InflationDataPoint> fromPoint = getOnOrBefore(fromDate);
        Optional<InflationDataPoint> toPoint = getOnOrBefore(toDate);

        if (fromPoint.isEmpty() || toPoint.isEmpty()) return Optional.empty();
        BigDecimal cpiFrom = fromPoint.get().getCpiIndex();
        BigDecimal cpiTo = toPoint.get().getCpiIndex();
        if (cpiFrom == null || cpiTo == null || cpiFrom.signum() <= 0) return Optional.empty();

        BigDecimal cumulative = cpiTo.divide(cpiFrom, 8, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal real = null;
        if (nominalReturnPct != null) {
            BigDecimal ratioNom = nominalReturnPct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                    .add(BigDecimal.ONE);
            BigDecimal ratioInf = cumulative.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                    .add(BigDecimal.ONE);
            if (ratioInf.signum() > 0) {
                real = ratioNom.divide(ratioInf, 8, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP);
            }
        }

        return Optional.of(new InflationCompareDto(
                fromPoint.get().getPeriodDate(),
                toPoint.get().getPeriodDate(),
                cpiFrom, cpiTo,
                cumulative,
                nominalReturnPct,
                real
        ));
    }
}
