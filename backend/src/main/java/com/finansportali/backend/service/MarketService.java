package com.finansportali.backend.service;

import com.finansportali.backend.domain.MarketCandle;
import com.finansportali.backend.domain.MarketInstrument;
import com.finansportali.backend.domain.MarketQuote;
import com.finansportali.backend.dto.MarketHistoryPoint;
import com.finansportali.backend.dto.MarketSummaryItem;
import com.finansportali.backend.repo.MarketCandleRepository;
import com.finansportali.backend.repo.MarketInstrumentRepository;
import com.finansportali.backend.repo.MarketQuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class MarketService {

    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketCandleRepository candleRepo;

    // Tek constructor yeterli (Spring bunu kullanır)
    public MarketService(MarketInstrumentRepository instrumentRepo,
                         MarketQuoteRepository quoteRepo,
                         MarketCandleRepository candleRepo) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.candleRepo = candleRepo;
    }

    public void seedIfEmpty() {
        Instant now = Instant.now();

        // 1) Enstrümanları "varsa al, yoksa oluştur"
        MarketInstrument usdtry = instrumentRepo.findBySymbol("USDTRY")
                .orElseGet(() -> instrumentRepo.save(new MarketInstrument("USDTRY", "USD/TRY", "FX")));
        MarketInstrument eurtry = instrumentRepo.findBySymbol("EURTRY")
                .orElseGet(() -> instrumentRepo.save(new MarketInstrument("EURTRY", "EUR/TRY", "FX")));
        MarketInstrument xu100 = instrumentRepo.findBySymbol("XU100")
                .orElseGet(() -> instrumentRepo.save(new MarketInstrument("XU100", "BIST 100", "INDEX")));
        MarketInstrument xauusd = instrumentRepo.findBySymbol("XAUUSD")
                .orElseGet(() -> instrumentRepo.save(new MarketInstrument("XAUUSD", "Gold (XAU/USD)", "COMMODITY")));
        MarketInstrument btcusd = instrumentRepo.findBySymbol("BTCUSD")
                .orElseGet(() -> instrumentRepo.save(new MarketInstrument("BTCUSD", "Bitcoin (BTC/USD)", "CRYPTO")));

        // 2) Quote yoksa seed et (opsiyonel kontrol)
        if (quoteRepo.count() == 0) {
            quoteRepo.save(new MarketQuote(usdtry, bd("32.4500"), bd("0.1200"), bd("0.37"), now));
            quoteRepo.save(new MarketQuote(eurtry, bd("35.1800"), bd("-0.0500"), bd("-0.14"), now));
            quoteRepo.save(new MarketQuote(xu100, bd("10250.20"), bd("75.30"), bd("0.74"), now));
            quoteRepo.save(new MarketQuote(xauusd, bd("2035.55"), bd("12.10"), bd("0.60"), now));
            quoteRepo.save(new MarketQuote(btcusd, bd("42500.00"), bd("-350.00"), bd("-0.82"), now));
        }

        // 3) Candle yoksa seed et (asıl kritik kısım)
        if (candleRepo.count() == 0) {
            seedCandles(usdtry, bd("32.00"));
            seedCandles(eurtry, bd("35.00"));
            seedCandles(xu100, bd("10000.00"));
            seedCandles(xauusd, bd("2000.00"));
            seedCandles(btcusd, bd("40000.00"));
        }
    }

    @Cacheable(cacheNames = "marketSummary")
    public List<MarketSummaryItem> summary() {
        seedIfEmpty();

        List<MarketSummaryItem> out = new ArrayList<>();
        for (MarketInstrument inst : instrumentRepo.findAll()) {
            quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst).ifPresent(q -> out.add(
                    new MarketSummaryItem(
                            inst.getSymbol(),
                            inst.getName(),
                            inst.getType(),
                            q.getLast(),
                            q.getChangeAbs(),
                            q.getChangePct(),
                            q.getAsOf()
                    )
            ));
        }
        return out;
    }

    @Cacheable(cacheNames = "marketHistory", key = "#symbol + ':' + #period")
    public List<MarketHistoryPoint> history(String symbol, String period) {
        seedIfEmpty();

        MarketInstrument inst = instrumentRepo.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));

        int days = switch ((period == null ? "30D" : period).toUpperCase()) {
            case "7D" -> 7;
            case "30D" -> 30;
            case "1Y" -> 365;
            default -> 30;
        };

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        return candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(inst, start, end)
                .stream()
                .map(c -> new MarketHistoryPoint(c.getDay(), c.getClose()))
                .toList();
    }

    private void seedCandles(MarketInstrument inst, BigDecimal base) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(30);

        // Aynı enstrüman için candle zaten varsa tekrar seed etme
        if (!candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(inst, start, today).isEmpty()) return;

        Random r = new Random(inst.getSymbol().hashCode());
        BigDecimal price = base;

        for (int i = 30; i >= 0; i--) {
            LocalDate day = today.minusDays(i);

            // Günlük küçük dalgalanma: -1% .. +1%
            double pct = (r.nextDouble() - 0.5) * 0.02;
            price = price.multiply(BigDecimal.valueOf(1.0 + pct));

            candleRepo.save(new MarketCandle(inst, day, price));
        }
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
