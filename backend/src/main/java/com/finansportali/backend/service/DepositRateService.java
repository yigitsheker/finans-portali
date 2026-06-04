package com.finansportali.backend.service;

import com.finansportali.backend.entity.DepositRatePoint;
import com.finansportali.backend.repository.DepositRatePointRepository;
import com.finansportali.backend.service.client.deposit.TcmbDepositRateFetcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Maintains the TCMB deposit-rate series, upserting ~10 years of monthly history
 * per currency on a daily schedule, and exposes cached read accessors for the
 * full history and the latest point per currency.
 */
@Service
public class DepositRateService {

    private static final Logger log = LoggerFactory.getLogger(DepositRateService.class);

    private final DepositRatePointRepository repo;
    private final TcmbDepositRateFetcher fetcher;

    public DepositRateService(DepositRatePointRepository repo, TcmbDepositRateFetcher fetcher) {
        this.repo = repo;
        this.fetcher = fetcher;
    }

    @PostConstruct
    void onStartup() {
        if (repo.count() == 0) {
            log.info("[DepositRates] DB empty — triggering first sync from TCMB");
            try {
                refresh();
            } catch (Exception e) {
                log.warn("[DepositRates] Startup sync failed: {}", e.getMessage());
            }
        }
    }

    /** Refresh ~10 years of monthly history from TCMB EVDS3. Idempotent. */
    @Transactional
    @CacheEvict(value = {"deposit-rates-all", "deposit-rates-latest"}, allEntries = true)
    public int refresh() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(10).withDayOfMonth(1);

        List<DepositRatePoint> fresh = fetcher.fetchDepositRates(from, to);
        if (fresh.isEmpty()) {
            log.warn("[DepositRates] No data from TCMB; DB left untouched");
            return 0;
        }

        int upserts = 0;
        for (DepositRatePoint p : fresh) {
            DepositRatePoint persisted = repo.findByPeriodDateAndCurrency(p.getPeriodDate(), p.getCurrency())
                    .orElseGet(() -> new DepositRatePoint(p.getPeriodDate(), p.getCurrency()));
            persisted.setRate1m(p.getRate1m());
            persisted.setRate3m(p.getRate3m());
            persisted.setRate6m(p.getRate6m());
            persisted.setRate12m(p.getRate12m());
            persisted.setRateOver12m(p.getRateOver12m());
            persisted.setRateAvg(p.getRateAvg());
            persisted.setSource("TCMB_EVDS3");
            persisted.setUpdatedAt(LocalDateTime.now());
            repo.save(persisted);
            upserts++;
        }
        log.info("[DepositRates] Refreshed {} (currency,month) rows", upserts);
        return upserts;
    }

    /** Same daily 09:00 cadence as inflation — TCMB tends to publish around the same time. */
    @Scheduled(cron = "0 5 9 * * *")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("[DepositRates] Scheduled refresh failed: {}", e.getMessage());
        }
    }

    /** Full monthly deposit-rate history for a currency, oldest first. */
    @Cacheable("deposit-rates-all")
    public List<DepositRatePoint> getAllForCurrency(String currency) {
        return repo.findByCurrencyOrderByPeriodDateAsc(currency);
    }

    /** Latest deposit-rate point on or before today for a currency. */
    @Cacheable(value = "deposit-rates-latest", key = "#currency")
    public java.util.Optional<DepositRatePoint> getLatest(String currency) {
        return repo.findLatestOnOrBefore(currency, LocalDate.now());
    }
}
