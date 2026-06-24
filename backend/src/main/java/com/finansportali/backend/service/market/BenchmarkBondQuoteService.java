package com.finansportali.backend.service.market;

import com.finansportali.backend.entity.*;
import com.finansportali.backend.repository.DebtInstrumentQuoteRepository;
import com.finansportali.backend.repository.DebtInstrumentRepository;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the watchlist's TR2Y/TR5Y/TR10Y "benchmark" rows onto the real
 * government-bond universe TCMB EVDS3 already populates via
 * {@link com.finansportali.backend.service.BondDataRefreshService}.
 *
 * <p>Yahoo Finance carries no Turkish government bond data, so these three
 * watchlist entries used to be permanently frozen demo values (a static
 * seed price that {@code PriceRefreshScheduler} could never refresh because
 * "TR2Y" isn't a real ticker). Instead, for each tenor we pick the active
 * bond whose maturity is closest to today + N years and republish its
 * latest TCMB-derived yield as the watchlist quote — the same convention
 * US2Y/US10Y already use (those surface Yahoo's ^IRX/^TNX yield indices as
 * the "price", not a bond price).
 */
@Component
@ConditionalOnProperty(name = "app.bonds.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class BenchmarkBondQuoteService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkBondQuoteService.class);

    private static final Map<String, Integer> TENOR_YEARS = Map.of(
            "TR2Y", 2,
            "TR5Y", 5,
            "TR10Y", 10
    );

    private final DebtInstrumentRepository debtInstrumentRepo;
    private final DebtInstrumentQuoteRepository debtQuoteRepo;
    private final MarketInstrumentRepository marketInstrumentRepo;
    private final MarketQuoteRepository marketQuoteRepo;
    private final MarketCandleRepository marketCandleRepo;
    private final CacheManager cacheManager;

    @Value("${app.bonds.benchmark-history-days:120}")
    private int historyDays;

    public BenchmarkBondQuoteService(DebtInstrumentRepository debtInstrumentRepo,
                                     DebtInstrumentQuoteRepository debtQuoteRepo,
                                     MarketInstrumentRepository marketInstrumentRepo,
                                     MarketQuoteRepository marketQuoteRepo,
                                     MarketCandleRepository marketCandleRepo,
                                     CacheManager cacheManager) {
        this.debtInstrumentRepo = debtInstrumentRepo;
        this.debtQuoteRepo = debtQuoteRepo;
        this.marketInstrumentRepo = marketInstrumentRepo;
        this.marketQuoteRepo = marketQuoteRepo;
        this.marketCandleRepo = marketCandleRepo;
        this.cacheManager = cacheManager;
    }

    /** Her çift saatten 10 dakika sonra — TCMB tahvil refresh'i bittikten sonra çalışır. */
    @Scheduled(cron = "${app.bonds.benchmark-refresh-cron:0 10 0/2 * * ?}")
    public void scheduledRefresh() {
        refreshBenchmarks();
    }

    /** Startup'tan 100 saniye sonra bir kez — ilk tahvil refresh'i (5s + fetch süresi) tamamlandıktan sonra. */
    @Scheduled(initialDelay = 100_000, fixedDelay = Long.MAX_VALUE)
    public void initialRefresh() {
        refreshBenchmarks();
    }

    @Transactional
    public void refreshBenchmarks() {
        List<DebtInstrument> bonds = debtInstrumentRepo.findByActiveTrueAndType(DebtInstrumentType.GOVERNMENT_BOND);
        if (bonds.isEmpty()) {
            log.debug("[BENCHMARK-BOND] henüz aktif devlet tahvili yok — atlanıyor");
            return;
        }

        LocalDate today = LocalDate.now();
        boolean any = false;
        for (Map.Entry<String, Integer> tenor : TENOR_YEARS.entrySet()) {
            if (refreshOne(tenor.getKey(), tenor.getValue(), bonds, today)) any = true;
        }
        if (any) evictAllCaches();
    }

    private boolean refreshOne(String symbol, int years, List<DebtInstrument> bonds, LocalDate today) {
        Optional<MarketInstrument> instOpt = marketInstrumentRepo.findBySymbol(symbol);
        if (instOpt.isEmpty()) {
            log.warn("[BENCHMARK-BOND] {} market_instruments'da seed edilmemiş — atlanıyor", symbol);
            return false;
        }

        DebtInstrument closest = closestMaturity(bonds, today.plusYears(years));
        if (closest == null) return false;

        List<DebtInstrumentQuote> last2 = debtQuoteRepo.findTop2ByInstrument(closest);
        if (last2.isEmpty() || last2.get(0).getYieldRate() == null) return false;

        BigDecimal latestYield = last2.get(0).getYieldRate();
        BigDecimal previousYield = last2.size() > 1 ? last2.get(1).getYieldRate() : null;

        MarketInstrument inst = instOpt.get();
        MarketQuote quote = MarketQuote.fromPreviousClose(
                inst, latestYield, previousYield, Instant.now(), MarketDataProvider.TCMB);
        marketQuoteRepo.save(quote);

        upsertCandle(inst, today, latestYield);
        backfillHistory(inst, closest, today);

        log.info("[BENCHMARK-BOND] {} -> %{} (kaynak: {}, vade: {})",
                symbol, latestYield, closest.getSymbol(), closest.getMaturityDate());
        return true;
    }

    /** Aktif tahviller arasından vadesi hedef tarihe en yakın olanı bulur. */
    private DebtInstrument closestMaturity(List<DebtInstrument> bonds, LocalDate target) {
        DebtInstrument best = null;
        long bestDiff = Long.MAX_VALUE;
        for (DebtInstrument bond : bonds) {
            if (bond.getMaturityDate() == null) continue;
            long diff = Math.abs(ChronoUnit.DAYS.between(bond.getMaturityDate(), target));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = bond;
            }
        }
        return best;
    }

    /** Seçilen tahvilin geçmiş getirilerini watchlist mum verisine aktarır (Geçmişten grafiği için). */
    private void backfillHistory(MarketInstrument inst, DebtInstrument source, LocalDate today) {
        LocalDate from = today.minusDays(historyDays);
        for (DebtInstrumentQuote q : debtQuoteRepo.findHistoricalQuotes(source, from, today)) {
            if (q.getYieldRate() != null) {
                upsertCandle(inst, q.getQuoteDate(), q.getYieldRate());
            }
        }
    }

    private void upsertCandle(MarketInstrument inst, LocalDate day, BigDecimal close) {
        MarketCandle candle = marketCandleRepo.findByInstrumentAndDay(inst, day)
                .orElseGet(() -> new MarketCandle(inst, day, close));
        candle.setClose(close);
        marketCandleRepo.save(candle);
    }

    private void evictAllCaches() {
        for (String name : List.of("marketSummary", "marketHistory", "yahooChart")) {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }
}
