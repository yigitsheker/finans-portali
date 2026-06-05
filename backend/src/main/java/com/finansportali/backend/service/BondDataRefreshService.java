package com.finansportali.backend.service;

import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentQuote;
import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import com.finansportali.backend.repository.DebtInstrumentQuoteRepository;
import com.finansportali.backend.repository.DebtInstrumentRepository;
import com.finansportali.backend.service.client.bond.BondDataProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tahvil ve bono verilerini periyodik olarak güncelleyen servis.
 * Seçili veri sağlayıcıdan verileri çeker ve veritabanına kaydeder.
 */
@Service
public class BondDataRefreshService {

    private static final Logger log = LoggerFactory.getLogger(BondDataRefreshService.class);

    private final DebtInstrumentRepository instrumentRepo;
    private final DebtInstrumentQuoteRepository quoteRepo;
    private final List<BondDataProvider> providers;
    private final MeterRegistry meterRegistry;

    @Value("${app.bonds.provider:DEMO}")
    private String activeProviderName;

    @Value("${app.bonds.fallback-enabled:true}")
    private boolean fallbackEnabled;

    // Metrics
    private Counter refreshSuccessCounter;
    private Counter refreshFailureCounter;
    private Counter instrumentsFetchedCounter;
    private Timer refreshDurationTimer;

    public BondDataRefreshService(DebtInstrumentRepository instrumentRepo,
                                  DebtInstrumentQuoteRepository quoteRepo,
                                  List<BondDataProvider> providers,
                                  MeterRegistry meterRegistry) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.providers = providers;
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    private void initMetrics() {
        refreshSuccessCounter = Counter.builder("bond_refresh_success_total")
            .description("Total successful bond data refreshes")
            .register(meterRegistry);

        refreshFailureCounter = Counter.builder("bond_refresh_failure_total")
            .description("Total failed bond data refreshes")
            .register(meterRegistry);

        instrumentsFetchedCounter = Counter.builder("bond_instruments_fetched_total")
            .description("Total bond instruments fetched")
            .register(meterRegistry);

        refreshDurationTimer = Timer.builder("bond_refresh_duration_seconds")
            .description("Duration of bond data refresh operations")
            .register(meterRegistry);
    }

    /**
     * Tahvil verilerini günceller.
     * @return Güncellenen enstrüman sayısı
     */
    @Transactional
    public int refreshBondData() {
        return refreshDurationTimer.record(this::runRefresh);
    }

    private int runRefresh() {
        log.info("[BOND-REFRESH] Starting bond data refresh with provider: {}", activeProviderName);
        try {
            BondDataProvider provider = selectProvider();
            if (provider == null || !provider.isEnabled()) {
                log.warn("[BOND-REFRESH] No active provider available");
                refreshFailureCounter.increment();
                return 0;
            }

            log.info("[BOND-REFRESH] Using provider: {}", provider.getProviderName());
            List<BondQuoteDto> quotes = provider.fetchLatestBondQuotes();
            log.info("[BOND-REFRESH] Fetched {} bond quotes from {}",
                    quotes.size(), provider.getProviderName());

            int updatedCount = applyQuotes(quotes);
            deactivateStale(quotes);
            log.info("[BOND-REFRESH] Successfully updated {} bond instruments", updatedCount);
            refreshSuccessCounter.increment();
            return updatedCount;
        } catch (RuntimeException e) {
            // Provider network IO, JSON parsing, and JPA save all surface as
            // RuntimeException. We deliberately swallow + record-and-move-on
            // here because this is a scheduled job — a single bad fetch
            // shouldn't kill the loop or take the app down.
            log.error("[BOND-REFRESH] Bond data refresh failed", e);
            refreshFailureCounter.increment();
            return 0;
        }
    }

    private int applyQuotes(List<BondQuoteDto> quotes) {
        // The fetcher now returns one dto per (bond, day) — many rows share an
        // ISIN. Group by symbol so the instrument is upserted ONCE per bond and
        // each day's quote is stored, instead of re-saving the instrument N times.
        Map<String, List<BondQuoteDto>> bySymbol = new LinkedHashMap<>();
        for (BondQuoteDto dto : quotes) {
            if (dto.getSymbol() == null) continue;
            bySymbol.computeIfAbsent(dto.getSymbol(), k -> new ArrayList<>()).add(dto);
        }
        int updatedCount = 0;
        for (List<BondQuoteDto> group : bySymbol.values()) {
            if (applyBondSafely(group)) {
                updatedCount++;
                instrumentsFetchedCounter.increment();
            }
        }
        return updatedCount;
    }

    /**
     * Soft-delete instruments that a refreshed source no longer reports. When a
     * provider returns its full current universe (the TCMB EVDS3 fetcher now
     * enumerates every active bond), any active instrument it previously quoted
     * but omitted this round is stale — a retired hand-picked symbol or a matured
     * bond — and should drop off the listing. Scoped per source so one provider's
     * refresh never deactivates another's rows. No-op on an empty fetch.
     */
    private void deactivateStale(List<BondQuoteDto> quotes) {
        if (quotes.isEmpty()) return;

        java.util.Set<String> freshSymbols = new java.util.HashSet<>();
        java.util.Set<String> sources = new java.util.HashSet<>();
        for (BondQuoteDto dto : quotes) {
            if (dto.getSymbol() != null) freshSymbols.add(dto.getSymbol());
            sources.add(dto.getSource() != null ? dto.getSource() : activeProviderName);
        }
        if (freshSymbols.isEmpty()) return;

        for (String source : sources) {
            try {
                List<DebtInstrument> stale =
                        instrumentRepo.findActiveManagedBySourceExcluding(source, freshSymbols);
                if (stale.isEmpty()) continue;
                stale.forEach(i -> i.setActive(false));
                instrumentRepo.saveAll(stale);
                log.info("[BOND-REFRESH] Deactivated {} stale '{}' instrument(s): {}",
                        stale.size(), source,
                        stale.stream().map(DebtInstrument::getSymbol).toList());
            } catch (RuntimeException e) {
                log.warn("[BOND-REFRESH] Stale-deactivation for source '{}' failed: {}",
                        source, e.getMessage());
            }
        }
    }

    private boolean applyBondSafely(List<BondQuoteDto> group) {
        try {
            upsertBond(group);
            return true;
        } catch (RuntimeException e) {
            // JPA DataAccessException + constraint violations + null edge cases
            // all surface as RuntimeException; skip the bad bond and let the
            // outer loop move on so one corrupt feed entry doesn't poison the
            // whole batch.
            log.error("[BOND-REFRESH] Failed to upsert bond: {}", group.get(0).getSymbol(), e);
            return false;
        }
    }

    /**
     * Upsert one bond: the instrument once (from the most recent dto) and a quote
     * row per dated dto in the group. A single bad day is skipped without dropping
     * the rest of the bond's history.
     */
    private void upsertBond(List<BondQuoteDto> group) {
        BondQuoteDto latest = group.stream()
            .max(Comparator.comparing(d -> d.getQuoteDate() != null ? d.getQuoteDate() : LocalDate.MIN))
            .orElse(group.get(0));
        DebtInstrument instrument = upsertInstrument(latest);

        // Prefetch the bond's existing quotes over the day span in ONE query so
        // the per-day upserts below don't each fire a SELECT (avoids an N+1 over
        // the ~120-day window). Keyed by "date|source".
        LocalDate min = LocalDate.MAX;
        LocalDate max = LocalDate.MIN;
        for (BondQuoteDto dto : group) {
            LocalDate d = dto.getQuoteDate() != null ? dto.getQuoteDate() : LocalDate.now();
            if (d.isBefore(min)) min = d;
            if (d.isAfter(max)) max = d;
        }
        Map<String, DebtInstrumentQuote> existing = new LinkedHashMap<>();
        for (DebtInstrumentQuote q : quoteRepo.findByInstrumentAndQuoteDateBetween(instrument, min, max)) {
            existing.put(q.getQuoteDate() + "|" + q.getSource(), q);
        }

        int ok = 0;
        for (BondQuoteDto dto : group) {
            try {
                upsertQuote(instrument, dto, existing);
                ok++;
            } catch (RuntimeException e) {
                log.debug("[BOND-REFRESH] quote upsert failed {} @ {}: {}",
                    instrument.getSymbol(), dto.getQuoteDate(), e.getMessage());
            }
        }
        log.debug("[BOND-REFRESH] Upserted {} with {}/{} quote(s)", instrument.getSymbol(), ok, group.size());
    }

    /** Find-or-create the instrument and refresh its (static) fields. */
    private DebtInstrument upsertInstrument(BondQuoteDto dto) {
        DebtInstrument instrument = instrumentRepo.findBySymbol(dto.getSymbol())
            .orElseGet(() -> {
                DebtInstrument newInst = new DebtInstrument();
                newInst.setSymbol(dto.getSymbol());
                newInst.setName(dto.getName());
                newInst.setType(dto.getType());
                return newInst;
            });

        if (dto.getIsin() != null) instrument.setIsin(dto.getIsin());
        if (dto.getName() != null) instrument.setName(dto.getName());
        if (dto.getType() != null) instrument.setType(dto.getType());
        if (dto.getIssuer() != null) instrument.setIssuer(dto.getIssuer());
        if (dto.getCurrency() != null) instrument.setCurrency(dto.getCurrency());
        if (dto.getMaturityDate() != null) instrument.setMaturityDate(dto.getMaturityDate());
        if (dto.getCouponRate() != null) instrument.setCouponRate(dto.getCouponRate());
        if (dto.getCouponType() != null) instrument.setCouponType(dto.getCouponType());
        instrument.setActive(true);

        return instrumentRepo.save(instrument);
    }

    /** Create or update the (instrument, date, source) quote from one dto, using
     *  a prefetched map (date|source → quote) so no per-quote SELECT is issued. */
    private void upsertQuote(DebtInstrument savedInstrument, BondQuoteDto dto, Map<String, DebtInstrumentQuote> existingByKey) {
        final LocalDate quoteDate = dto.getQuoteDate() != null ? dto.getQuoteDate() : LocalDate.now();
        final String source = dto.getSource() != null ? dto.getSource() : activeProviderName;

        DebtInstrumentQuote quote = existingByKey.get(quoteDate + "|" + source);
        if (quote == null) {
            quote = new DebtInstrumentQuote();
            quote.setInstrument(savedInstrument);
            quote.setQuoteDate(quoteDate);
            quote.setSource(source);
        }

        if (dto.getPrice() != null) quote.setPrice(dto.getPrice());
        if (dto.getYieldRate() != null) quote.setYieldRate(dto.getYieldRate());
        if (dto.getCleanPrice() != null) quote.setCleanPrice(dto.getCleanPrice());
        if (dto.getDirtyPrice() != null) quote.setDirtyPrice(dto.getDirtyPrice());
        if (dto.getVolume() != null) quote.setVolume(dto.getVolume());
        if (dto.getChangeRate() != null) quote.setChangeRate(dto.getChangeRate());

        quoteRepo.save(quote);
    }

    /**
     * Aktif provider'ı seçer. Fallback mekanizması ile.
     */
    private BondDataProvider selectProvider() {
        // Try to find configured provider
        Optional<BondDataProvider> configuredProvider = providers.stream()
            .filter(p -> p.getProviderName().equalsIgnoreCase(activeProviderName))
            .filter(BondDataProvider::isEnabled)
            .findFirst();

        if (configuredProvider.isPresent()) {
            return configuredProvider.get();
        }

        // Fallback to any enabled provider
        if (fallbackEnabled) {
            log.warn("[BOND-REFRESH] Configured provider '{}' not available, trying fallback", activeProviderName);
            return providers.stream()
                .filter(BondDataProvider::isEnabled)
                .findFirst()
                .orElse(null);
        }

        return null;
    }

    /**
     * Manuel refresh için public method.
     */
    public String triggerManualRefresh() {
        log.info("[BOND-REFRESH] Manual refresh triggered");
        int count = refreshBondData();
        return String.format("Bond data refresh completed. Updated %d instruments.", count);
    }
}
