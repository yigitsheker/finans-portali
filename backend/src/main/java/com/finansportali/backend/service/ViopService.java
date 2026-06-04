package com.finansportali.backend.service;

import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.repository.ViopContractRepository;
import com.finansportali.backend.service.client.viop.IsYatirimViopFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Maintains the VIOP (Borsa İstanbul derivatives) contract table by periodically
 * fetching delayed quotes from İş Yatırım, upserting them, and exposing ordered
 * read queries for the full list and per-category views.
 */
@Service
public class ViopService {

    private static final Logger log = LoggerFactory.getLogger(ViopService.class);

    private final IsYatirimViopFetcher fetcher;
    private final ViopContractRepository repo;

    public ViopService(IsYatirimViopFetcher fetcher, ViopContractRepository repo) {
        this.fetcher = fetcher;
        this.repo = repo;
    }

    /** All contracts ordered by category, underlying, then maturity. */
    @Transactional(readOnly = true)
    public List<ViopContract> findAll() {
        return repo.findAllByOrderByCategoryAscUnderlyingAscMaturityYearAscMaturityMonthAsc();
    }

    /** Contracts in a single category, ordered by maturity then underlying. */
    @Transactional(readOnly = true)
    public List<ViopContract> findByCategory(ViopContract.Category category) {
        return repo.findByCategoryOrderByMaturityYearAscMaturityMonthAscUnderlyingAsc(category);
    }

    /**
     * Scheduled refresh — every 15 minutes during the trading day. İş Yatırım
     * publishes 15-minute-delayed VIOP quotes, so polling more often would
     * just hit cached pages.
     *
     * First fire 30s after startup so Flyway + Keycloak bootstrap finish first.
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = 15 * 60 * 1000L)
    public void refresh() {
        log.info("VIOP refresh: fetching contracts from İş Yatırım...");
        List<ViopContract> fetched = fetcher.fetchAll();
        if (fetched.isEmpty()) {
            log.warn("VIOP refresh: 0 contracts parsed — leaving existing data untouched");
            return;
        }
        Map<String, ViopContract> existing = repo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(ViopContract::getSymbol, c -> c));
        int updated = 0, inserted = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (ViopContract f : fetched) {
            ViopContract row = existing.get(f.getSymbol());
            if (row == null) {
                f.setUpdatedAt(now);
                repo.save(f);
                inserted++;
            } else {
                row.setName(f.getName());
                row.setUnderlying(f.getUnderlying());
                row.setMaturityMonth(f.getMaturityMonth());
                row.setMaturityYear(f.getMaturityYear());
                row.setCategory(f.getCategory());
                row.setLastPrice(f.getLastPrice());
                row.setChangePct(f.getChangePct());
                row.setChangeAbs(f.getChangeAbs());
                row.setVolumeTl(f.getVolumeTl());
                row.setVolumeLots(f.getVolumeLots());
                row.setUpdatedAt(now);
                repo.save(row);
                updated++;
            }
        }
        log.info("VIOP refresh complete: {} updated, {} inserted (total fetched={})",
                updated, inserted, fetched.size());
    }
}
