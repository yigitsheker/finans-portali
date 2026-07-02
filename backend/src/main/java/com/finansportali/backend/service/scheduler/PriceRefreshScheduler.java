package com.finansportali.backend.service.scheduler;

import com.finansportali.backend.entity.*;
import com.finansportali.backend.repository.ExchangeRateRepository;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.service.MarketService;
import com.finansportali.backend.service.PriceAlertService;
import com.finansportali.backend.service.client.market.YahooPriceFetcher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Piyasa verilerini GÜNDE 1 KEZ Yahoo Finance üzerinden günceller.
 *
 * Zamanlama:
 *   - Startup'ta 60 saniye sonra ilk yükleme
 *   - Her gün 18:00 UTC'de periyodik güncelleme
 *
 * Güncelleme sonrası cache temizlenir.
 * Tek bir sembol başarısız olursa diğerleri etkilenmez.
 */
@Component
public class PriceRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshScheduler.class);

    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository      quoteRepo;
    private final MarketCandleRepository     candleRepo;
    private final ExchangeRateRepository     exchangeRateRepo;
    private final YahooPriceFetcher          yahoo;
    private final MarketService              marketService;
    private final PriceAlertService          priceAlertService;
    private final CacheManager               cacheManager;

    // Self-reference via Spring proxy. We need this because refreshAll()
    // calls refreshInstrumentInTransaction(...) from the same bean — a
    // plain `this.refreshInstrumentInTransaction(...)` skips the AOP
    // proxy, which means @Transactional is silently ignored and downstream
    // @Modifying deletes (candleRepo.deleteByInstrumentAndDay) blow up
    // with "No EntityManager with actual transaction". @Lazy breaks the
    // circular constructor dependency.
    @Autowired
    @Lazy
    private PriceRefreshScheduler self;

    // Business metrics — outcome counters from refreshAll() + duration timer.
    private final Counter instrumentsUpdatedCounter;
    private final Counter instrumentsFailedCounter;
    private final Counter instrumentsSkippedCounter;
    private final Counter alertCheckFailureCounter;
    private final Timer refreshDurationTimer;

    public PriceRefreshScheduler(MarketInstrumentRepository instrumentRepo,
                                 MarketQuoteRepository quoteRepo,
                                 MarketCandleRepository candleRepo,
                                 ExchangeRateRepository exchangeRateRepo,
                                 YahooPriceFetcher yahoo,
                                 MarketService marketService,
                                 PriceAlertService priceAlertService,
                                 CacheManager cacheManager,
                                 MeterRegistry meterRegistry) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo      = quoteRepo;
        this.candleRepo     = candleRepo;
        this.exchangeRateRepo = exchangeRateRepo;
        this.yahoo          = yahoo;
        this.marketService  = marketService;
        this.priceAlertService = priceAlertService;
        this.cacheManager   = cacheManager;

        this.instrumentsUpdatedCounter = Counter.builder("price_instruments_updated_total")
                .description("Market instruments whose price was refreshed successfully")
                .register(meterRegistry);
        this.instrumentsFailedCounter = Counter.builder("price_instruments_failed_total")
                .description("Market instruments that failed to refresh")
                .register(meterRegistry);
        this.instrumentsSkippedCounter = Counter.builder("price_instruments_skipped_total")
                .description("Market instruments skipped (no provider symbol)")
                .register(meterRegistry);
        this.alertCheckFailureCounter = Counter.builder("price_alert_check_failure_total")
                .description("Post-refresh alert evaluation failures")
                .register(meterRegistry);
        this.refreshDurationTimer = Timer.builder("price_refresh_duration_seconds")
                .description("Duration of a full price refresh cycle (all instruments + alert check)")
                .register(meterRegistry);
    }

    /** Günlük periyodik güncelleme — 18:00 UTC */
    @Scheduled(cron = "0 0 18 * * *")
    public void refreshDaily() {
        log.info("=== Günlük fiyat güncellemesi başladı (Yahoo Finance) ===");
        refreshAll();
    }

    /**
     * Startup refresh — 60 saniye bekler, bir kez çalışır.
     * fixedDelay = Long.MAX_VALUE → tekrar çalışmaz.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    public void refreshOnStartup() {
        log.info("=== Startup fiyat yüklemesi başladı (Yahoo Finance) ===");
        refreshAll();
    }

    /**
     * Intraday güncelleme — gecikmeli OLMAYAN (delayed=false) enstrümanlar için
     * 15 dakikada bir. Kripto / ABD hisseleri / endeksler gibi sık çekilebilen
     * veriler near-live olur. BIST gibi delayed=true enstrümanlar buna DAHİL
     * DEĞİLDİR; onlar yalnızca günlük 18:00 UTC işinde güncellenir.
     */
    @Scheduled(initialDelay = 90_000, fixedDelay = 15 * 60 * 1000L)
    public void refreshIntraday() {
        refreshDurationTimer.record(() -> doRefresh(i -> !i.isDelayed(), "intraday (gecikmeli olmayan)"));
    }

    /** Admin endpoint'ten manuel tetikleme — tüm enstrümanlar */
    public void refreshAll() {
        refreshDurationTimer.record(() -> doRefresh(i -> true, "tüm enstrümanlar"));
    }

    private void doRefresh(java.util.function.Predicate<MarketInstrument> filter, String label) {
        List<MarketInstrument> instruments = instrumentRepo.findAll().stream().filter(filter).toList();
        int updated = 0;
        int skipped = 0;
        int failed  = 0;

        for (MarketInstrument inst : instruments) {
            // Use centralized symbol normalization
            String yahooSym = marketService.normalizeSymbolForYahoo(inst.getSymbol(), inst.getInstrumentType());

            if (yahooSym == null || yahooSym.isBlank()) {
                log.debug("[Scheduler] Skipping {} — no provider symbol", inst.getSymbol());
                skipped++;
                instrumentsSkippedCounter.increment();
                continue;
            }

            try {
                // Her instrument için ayrı transaction — `self` proxy
                // sayesinde @Transactional gerçekten devreye girer.
                boolean ok = self.refreshInstrumentInTransaction(inst, yahooSym);
                if (ok) {
                    updated++;
                    instrumentsUpdatedCounter.increment();
                } else {
                    failed++;
                    instrumentsFailedCounter.increment();
                }
            } catch (Exception e) {
                log.error("[Scheduler] Unexpected error for {}: {}", inst.getSymbol(), e.getMessage());
                failed++;
                instrumentsFailedCounter.increment();
            }

            // Yahoo rate limit yok ama nezaket olarak küçük bekleme
            sleep(300);
        }

        evictAllCaches();

        // Fiyat güncellemesi sonrası alarm kontrolü yap (ayrı transaction)
        log.info("=== Fiyat alarmları kontrol ediliyor ===");
        try {
            self.checkAlertsInTransaction();
            log.info("=== Alarm kontrolü tamamlandı ===");
        } catch (Exception e) {
            log.error("Alarm kontrolü sırasında hata: {}", e.getMessage(), e);
            alertCheckFailureCounter.increment();
        }

        log.info("=== [{}] Güncelleme tamamlandı. Güncellenen: {}, Başarısız: {}, Atlanan: {} ===",
                label, updated, failed, skipped);
    }

    /**
     * Refreshes one instrument inside its own transaction (invoked via the
     * {@code self} proxy so {@code @Transactional} actually applies). Updates
     * the provider symbol if it drifted, then fetches and persists the quote
     * and candles. Returns false when the upstream quote could not be fetched.
     */
    @Transactional
    public boolean refreshInstrumentInTransaction(MarketInstrument inst, String yahooSym) {
        // Update provider symbol if it's different from normalized
        if (!yahooSym.equals(inst.getProviderSymbol())) {
            log.info("[Scheduler] Updating provider symbol for {}: {} -> {}", 
                    inst.getSymbol(), inst.getProviderSymbol(), yahooSym);
            inst.setProviderSymbol(yahooSym);
            instrumentRepo.save(inst);
        }
        
        return refreshInstrument(inst, yahooSym);
    }

    /** Runs the post-refresh price-alert evaluation in its own transaction. */
    @Transactional
    public void checkAlertsInTransaction() {
        priceAlertService.checkAllAlerts();
    }

    private boolean refreshInstrument(MarketInstrument inst, String yahooSym) {
        // 1) Anlık quote çek
        var quoteOpt = yahoo.fetchQuote(yahooSym);
        if (quoteOpt.isEmpty()) {
            // Yahoo bazı TCMB dövizlerini (DKK, NOK, SEK, SAR, KWD ...) parite
            // olarak sunmuyor (CODETRY=X → 404). Bu FX enstrümanları için TCMB
            // satış kuruna düş, böylece portföyde fiyatlanabilir olurlar.
            if (inst.getInstrumentType() == InstrumentType.FX && tcmbFxFallback(inst)) {
                return true;
            }
            log.warn("[Scheduler] Quote alınamadı: {} ({})", inst.getSymbol(), yahooSym);
            return false;
        }

        var q   = quoteOpt.get();
        Instant now = Instant.now();

        // 2) Quote kaydet
        MarketQuote quote = MarketQuote.fromPreviousClose(
                inst, q.last(), q.previousClose(), now, MarketDataProvider.YAHOO);
        // Yahoo'dan gelen change/pct daha doğru — override et
        quote.setChangeAbs(q.changeAbs());
        quote.setChangePct(q.changePct());
        // Volume optional — Yahoo'nun veremediği enstrümanlar için null kalır
        quote.setVolume(q.volume());
        quoteRepo.save(quote);

        // 3) Bugünün candle'ını güncelle. Upsert: aynı (instrument, day)
        // satırı tekrar tekrar refresh'lerde geliyor, delete+insert dener
        // yerine var olanı update ediyoruz — aksi halde Hibernate'in
        // insert-before-delete sıralaması (instrument_id, day) unique
        // constraint'i kırıyor.
        LocalDate today = LocalDate.now();
        upsertCandle(inst, today, q.last());

        // 4) Tarihsel candle eksikse 1 yıllık veri çek. Eski mantık "son
        //    yıl içinde 30'dan az candle varsa 1y fetch" idi; bu, sadece
        //    son ayın candle'larına sahip enstrümanları (49 candle) bile
        //    "yeterli" sayıyordu ve Analiz sayfasındaki Yıllık kolonunu
        //    boş bırakıyordu. Artık tetiği DERİNLİĞE bağladık: yıl önceki
        //    ±30 gün aralığında hiç candle yoksa demek ki gerçekten 1
        //    yıllık geçmişimiz yok — fetch.
        LocalDate yearAgo = today.minusDays(365);
        boolean hasYearOldCandle = !candleRepo
                .findByInstrumentAndDayBetweenOrderByDayAsc(inst, yearAgo.minusDays(30), yearAgo.plusDays(30))
                .isEmpty();

        if (!hasYearOldCandle) {
            log.info("[Scheduler] Tarihsel veri eksik (yıllık kapsam yok), {} için 1y veri çekiliyor...", inst.getSymbol());
            List<YahooPriceFetcher.DayClose> history = yahoo.fetchDailyHistory(yahooSym, "1y");
            for (YahooPriceFetcher.DayClose dc : history) {
                upsertCandle(inst, dc.day(), dc.close());
            }
            log.info("[Scheduler] {} için {} candle kaydedildi", inst.getSymbol(), history.size());
            sleep(300);
        }

        log.info("[Scheduler] Güncellendi: {} → {} ({})",
                inst.getSymbol(), q.last(), q.currency() != null ? q.currency() : "?");
        return true;
    }

    /**
     * Yahoo'nun parite olarak sunmadığı dövizler için TCMB satış kurundan
     * quote + candle yazar. Sembol "XXXTRY" → currencyCode "XXX". Fiyat yoksa
     * (TCMB'de o döviz yoksa) false döner ve normal "başarısız" akışı devam eder.
     */
    private boolean tcmbFxFallback(MarketInstrument inst) {
        String symbol = inst.getSymbol();
        if (symbol == null || !symbol.endsWith("TRY") || symbol.length() <= 3) return false;
        String code = symbol.substring(0, symbol.length() - 3);
        List<ExchangeRate> rates = exchangeRateRepo.findByCurrencyCodeOrderByRateDateDesc(code);
        if (rates.isEmpty()) return false;
        java.math.BigDecimal last = rates.get(0).getSellingRate();
        if (last == null || last.signum() == 0) return false;
        MarketQuote quote = MarketQuote.fromPreviousClose(
                inst, last, null, Instant.now(), MarketDataProvider.TCMB);
        quoteRepo.save(quote);
        upsertCandle(inst, LocalDate.now(), last);
        log.info("[Scheduler] TCMB kurundan güncellendi: {} → {} (TRY)", inst.getSymbol(), last);
        return true;
    }

    /**
     * Idempotent candle write. Looks for an existing row for
     * (instrument, day); if present updates close+day, otherwise inserts a
     * fresh one. Avoids the delete+insert pattern's unique-constraint race
     * inside a single transaction.
     */
    private void upsertCandle(MarketInstrument inst, LocalDate day, java.math.BigDecimal close) {
        MarketCandle candle = candleRepo.findByInstrumentAndDay(inst, day)
                .orElseGet(() -> new MarketCandle(inst, day, close));
        candle.setClose(close);
        candleRepo.save(candle);
    }

    private void evictAllCaches() {
        for (String name : List.of("marketSummary", "marketHistory", "yahooChart")) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
                log.debug("[Scheduler] Cache temizlendi: {}", name);
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            // Preserve interrupt status so a shutting-down scheduler pool
            // sees the cancellation on the next tick (Sonar S2142).
            Thread.currentThread().interrupt();
        }
    }
}
