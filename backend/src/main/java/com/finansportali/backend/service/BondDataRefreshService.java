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
import java.util.List;
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
        int updatedCount = 0;
        for (BondQuoteDto dto : quotes) {
            if (applyQuoteSafely(dto)) {
                updatedCount++;
                instrumentsFetchedCounter.increment();
            }
        }
        return updatedCount;
    }

    private boolean applyQuoteSafely(BondQuoteDto dto) {
        try {
            upsertInstrumentAndQuote(dto);
            return true;
        } catch (RuntimeException e) {
            // JPA DataAccessException + constraint violations + null edge cases
            // all surface as RuntimeException; skip the bad row and let the
            // outer loop move on so one corrupt feed entry doesn't poison the
            // whole batch.
            log.error("[BOND-REFRESH] Failed to upsert bond: {}", dto.getSymbol(), e);
            return false;
        }
    }

    /**
     * Enstrüman ve fiyat verisini upsert eder.
     */
    private void upsertInstrumentAndQuote(BondQuoteDto dto) {
        // Find or create instrument
        DebtInstrument instrument = instrumentRepo.findBySymbol(dto.getSymbol())
            .orElseGet(() -> {
                DebtInstrument newInst = new DebtInstrument();
                newInst.setSymbol(dto.getSymbol());
                newInst.setName(dto.getName());
                newInst.setType(dto.getType());
                return newInst;
            });

        // Update instrument fields
        if (dto.getIsin() != null) instrument.setIsin(dto.getIsin());
        if (dto.getName() != null) instrument.setName(dto.getName());
        if (dto.getType() != null) instrument.setType(dto.getType());
        if (dto.getIssuer() != null) instrument.setIssuer(dto.getIssuer());
        if (dto.getCurrency() != null) instrument.setCurrency(dto.getCurrency());
        if (dto.getMaturityDate() != null) instrument.setMaturityDate(dto.getMaturityDate());
        if (dto.getCouponRate() != null) instrument.setCouponRate(dto.getCouponRate());
        if (dto.getCouponType() != null) instrument.setCouponType(dto.getCouponType());
        instrument.setActive(true);

        final DebtInstrument savedInstrument = instrumentRepo.save(instrument);

        // Create or update quote
        final LocalDate quoteDate = dto.getQuoteDate() != null ? dto.getQuoteDate() : LocalDate.now();
        final String source = dto.getSource() != null ? dto.getSource() : activeProviderName;

        Optional<DebtInstrumentQuote> existingQuote = quoteRepo.findByInstrumentAndQuoteDateAndSource(
            savedInstrument, quoteDate, source
        );

        DebtInstrumentQuote quote = existingQuote.orElseGet(() -> {
            DebtInstrumentQuote newQuote = new DebtInstrumentQuote();
            newQuote.setInstrument(savedInstrument);
            newQuote.setQuoteDate(quoteDate);
            newQuote.setSource(source);
            return newQuote;
        });

        // Update quote fields
        if (dto.getPrice() != null) quote.setPrice(dto.getPrice());
        if (dto.getYieldRate() != null) quote.setYieldRate(dto.getYieldRate());
        if (dto.getCleanPrice() != null) quote.setCleanPrice(dto.getCleanPrice());
        if (dto.getDirtyPrice() != null) quote.setDirtyPrice(dto.getDirtyPrice());
        if (dto.getVolume() != null) quote.setVolume(dto.getVolume());
        if (dto.getChangeRate() != null) quote.setChangeRate(dto.getChangeRate());

        quoteRepo.save(quote);

        log.debug("[BOND-REFRESH] Upserted: {} - {} (yield: {}%)", 
            savedInstrument.getSymbol(), savedInstrument.getName(), quote.getYieldRate());
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
