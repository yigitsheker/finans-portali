package com.finansportali.backend.service;

import com.finansportali.backend.dto.response.inflation.InflationCompareDto;
import com.finansportali.backend.entity.InflationDataPoint;
import com.finansportali.backend.repository.InflationDataPointRepository;
import com.finansportali.backend.service.client.inflation.FredInflationFetcher;
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

    public static final String COUNTRY_TR = "TR";
    public static final String COUNTRY_US = "US";

    private final InflationDataPointRepository repo;
    private final TcmbInflationFetcher fetcher;
    private final FredInflationFetcher fredFetcher;

    public InflationService(InflationDataPointRepository repo,
                            TcmbInflationFetcher fetcher,
                            FredInflationFetcher fredFetcher) {
        this.repo = repo;
        this.fetcher = fetcher;
        this.fredFetcher = fredFetcher;
    }

    @PostConstruct
    void onStartup() {
        if (repo.count() == 0) {
            log.info("[Inflation] DB empty — triggering first sync from TCMB + FRED");
            try {
                refresh();
            } catch (Exception e) {
                log.warn("[Inflation] Startup sync failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Refresh ~10 years of monthly history from BOTH TCMB EVDS3 (Turkey CPI)
     * and FRED CPIAUCSL (US CPI). Idempotent — re-running upserts on the
     * (period_date, country) natural key.
     */
    @Transactional
    @CacheEvict(value = {"inflation-all", "inflation-latest"}, allEntries = true)
    public int refresh() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(10).withDayOfMonth(1);

        int total = 0;
        total += upsertCountry(COUNTRY_TR, "TCMB_EVDS3", fetcher.fetchInflationHistory(from, to));
        total += upsertCountry(COUNTRY_US, "FRED_CPIAUCSL", fredFetcher.fetchInflationHistory(from, to));
        log.info("[Inflation] Refreshed {} monthly rows across TR + US", total);
        return total;
    }

    private int upsertCountry(String country, String fallbackSource, List<InflationDataPoint> fresh) {
        if (fresh.isEmpty()) {
            log.warn("[Inflation] No data from {} source; rows for {} left untouched", fallbackSource, country);
            return 0;
        }
        int upserts = 0;
        for (InflationDataPoint p : fresh) {
            InflationDataPoint persisted = repo.findByPeriodDateAndCountry(p.getPeriodDate(), country)
                    .orElseGet(() -> new InflationDataPoint(p.getPeriodDate(), country));
            persisted.setCpiIndex(p.getCpiIndex());
            persisted.setCpiYearlyChange(p.getCpiYearlyChange());
            persisted.setCpiMonthlyChange(p.getCpiMonthlyChange());
            persisted.setPpiIndex(p.getPpiIndex());
            persisted.setPpiYearlyChange(p.getPpiYearlyChange());
            persisted.setSource(p.getSource() != null ? p.getSource() : fallbackSource);
            persisted.setUpdatedAt(java.time.LocalDateTime.now());
            repo.save(persisted);
            upserts++;
        }
        log.info("[Inflation] Upserted {} monthly rows for {}", upserts, country);
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

    @Cacheable(value = "inflation-all", key = "#country")
    public List<InflationDataPoint> getAllAscending(String country) {
        return repo.findAllByCountryOrderByPeriodDateAsc(country);
    }

    /** Back-compat overload — defaults to Turkey. */
    public List<InflationDataPoint> getAllAscending() {
        return getAllAscending(COUNTRY_TR);
    }

    @Cacheable(value = "inflation-latest", key = "#country")
    public Optional<InflationDataPoint> getLatest(String country) {
        return repo.findLatestOnOrBefore(LocalDate.now(), country);
    }

    public Optional<InflationDataPoint> getLatest() {
        return getLatest(COUNTRY_TR);
    }

    public Optional<InflationDataPoint> getOnOrBefore(LocalDate target) {
        return repo.findLatestOnOrBefore(target, COUNTRY_TR);
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
