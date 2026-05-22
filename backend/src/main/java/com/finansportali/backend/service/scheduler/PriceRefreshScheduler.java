package com.finansportali.backend.service.scheduler;

import com.finansportali.backend.entity.*;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.service.MarketService;
import com.finansportali.backend.service.PriceAlertService;
import com.finansportali.backend.service.client.market.YahooPriceFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
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
    private final YahooPriceFetcher          yahoo;
    private final MarketService              marketService;
    private final PriceAlertService          priceAlertService;
    private final CacheManager               cacheManager;

    public PriceRefreshScheduler(MarketInstrumentRepository instrumentRepo,
                                 MarketQuoteRepository quoteRepo,
                                 MarketCandleRepository candleRepo,
                                 YahooPriceFetcher yahoo,
                                 MarketService marketService,
                                 PriceAlertService priceAlertService,
                                 CacheManager cacheManager) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo      = quoteRepo;
        this.candleRepo     = candleRepo;
        this.yahoo          = yahoo;
        this.marketService  = marketService;
        this.priceAlertService = priceAlertService;
        this.cacheManager   = cacheManager;
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

    /** Admin endpoint'ten manuel tetikleme */
    public void refreshAll() {
        List<MarketInstrument> instruments = instrumentRepo.findAll();
        int updated = 0;
        int skipped = 0;
        int failed  = 0;

        for (MarketInstrument inst : instruments) {
            // Use centralized symbol normalization
            String yahooSym = marketService.normalizeSymbolForYahoo(inst.getSymbol(), inst.getInstrumentType());
            
            if (yahooSym == null || yahooSym.isBlank()) {
                log.debug("[Scheduler] Skipping {} — no provider symbol", inst.getSymbol());
                skipped++;
                continue;
            }

            try {
                // Her instrument için ayrı transaction
                boolean ok = refreshInstrumentInTransaction(inst, yahooSym);
                if (ok) updated++; else failed++;
            } catch (Exception e) {
                log.error("[Scheduler] Unexpected error for {}: {}", inst.getSymbol(), e.getMessage());
                failed++;
            }

            // Yahoo rate limit yok ama nezaket olarak küçük bekleme
            sleep(300);
        }

        evictAllCaches();
        
        // Fiyat güncellemesi sonrası alarm kontrolü yap (ayrı transaction)
        log.info("=== Fiyat alarmları kontrol ediliyor ===");
        try {
            checkAlertsInTransaction();
            log.info("=== Alarm kontrolü tamamlandı ===");
        } catch (Exception e) {
            log.error("Alarm kontrolü sırasında hata: {}", e.getMessage(), e);
        }
        
        log.info("=== Güncelleme tamamlandı. Güncellenen: {}, Başarısız: {}, Atlanan: {} ===",
                updated, failed, skipped);
    }

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

    @Transactional
    public void checkAlertsInTransaction() {
        priceAlertService.checkAllAlerts();
    }

    private boolean refreshInstrument(MarketInstrument inst, String yahooSym) {
        // 1) Anlık quote çek
        var quoteOpt = yahoo.fetchQuote(yahooSym);
        if (quoteOpt.isEmpty()) {
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

        // 3) Bugünün candle'ını güncelle
        LocalDate today = LocalDate.now();
        candleRepo.deleteByInstrumentAndDay(inst, today);
        candleRepo.save(new MarketCandle(inst, today, q.last()));

        // 4) Tarihsel candle eksikse 1 yıllık veri çek
        LocalDate yearAgo = today.minusDays(365);
        long existingCount = candleRepo
                .findByInstrumentAndDayBetweenOrderByDayAsc(inst, yearAgo, today)
                .size();

        if (existingCount < 30) {
            log.info("[Scheduler] Tarihsel veri eksik, {} için 1y veri çekiliyor...", inst.getSymbol());
            List<YahooPriceFetcher.DayClose> history = yahoo.fetchDailyHistory(yahooSym, "1y");
            for (YahooPriceFetcher.DayClose dc : history) {
                candleRepo.deleteByInstrumentAndDay(inst, dc.day());
                candleRepo.save(new MarketCandle(inst, dc.day(), dc.close()));
            }
            log.info("[Scheduler] {} için {} candle kaydedildi", inst.getSymbol(), history.size());
            sleep(300);
        }

        log.info("[Scheduler] Güncellendi: {} → {} ({})",
                inst.getSymbol(), q.last(), q.currency() != null ? q.currency() : "?");
        return true;
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
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
