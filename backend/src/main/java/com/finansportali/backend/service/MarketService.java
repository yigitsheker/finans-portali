package com.finansportali.backend.service;

import com.finansportali.backend.domain.*;
import com.finansportali.backend.dto.MarketHistoryPoint;
import com.finansportali.backend.dto.MarketSummaryItem;
import com.finansportali.backend.repo.MarketCandleRepository;
import com.finansportali.backend.repo.MarketInstrumentRepository;
import com.finansportali.backend.repo.MarketQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketCandleRepository candleRepo;
    private final YahooPriceFetcher yahooPriceFetcher;

    public MarketService(MarketInstrumentRepository instrumentRepo,
                         MarketQuoteRepository quoteRepo,
                         MarketCandleRepository candleRepo,
                         YahooPriceFetcher yahooPriceFetcher) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo      = quoteRepo;
        this.candleRepo     = candleRepo;
        this.yahooPriceFetcher = yahooPriceFetcher;
    }

    // ── Symbol Normalization ─────────────────────────────────────────────────

    /**
     * Central symbol normalization function for Yahoo Finance calls.
     * Converts internal symbols to Yahoo Finance format based on instrument type.
     */
    public String normalizeSymbolForYahoo(String symbol, InstrumentType type) {
        if (symbol == null || type == null) {
            log.warn("Cannot normalize null symbol or type: symbol={}, type={}", symbol, type);
            return symbol;
        }

        String normalized = switch (type) {
            case FX -> {
                // FX pairs: USDTRY → USDTRY=X, EURTRY → EURTRY=X
                yield symbol.endsWith("=X") ? symbol : symbol + "=X";
            }
            case CRYPTO -> {
                // Crypto: BTCUSD → BTC-USD, ETHUSD → ETH-USD
                if (symbol.contains("-")) {
                    yield symbol; // Already in Yahoo format
                }
                // Convert BTCUSD to BTC-USD
                if (symbol.length() >= 6) {
                    String base = symbol.substring(0, 3);
                    String quote = symbol.substring(3);
                    yield base + "-" + quote;
                }
                yield symbol;
            }
            case STOCK -> {
                // US stocks: AAPL → AAPL (no transformation)
                yield symbol;
            }
            case BIST -> {
                // BIST stocks: THYAO → THYAO.IS, GARAN → GARAN.IS
                yield symbol.endsWith(".IS") ? symbol : symbol + ".IS";
            }
            case INDEX -> {
                // Indices: XU100 → XU100.IS
                yield symbol.endsWith(".IS") ? symbol : symbol + ".IS";
            }
            case COMMODITY -> {
                // Commodities: XAUUSD → GC=F (gold futures)
                if ("XAUUSD".equals(symbol)) {
                    yield "GC=F";
                }
                yield symbol;
            }
            case VIOP -> {
                // VIOP futures: No Yahoo data, use symbol as-is for manual data
                yield symbol;
            }
            case BOND -> {
                // Bonds: No Yahoo data, use symbol as-is for manual data
                yield symbol;
            }
            case FUND -> {
                // Funds: No Yahoo data, use symbol as-is for manual data
                yield symbol;
            }
        };

        log.debug("Symbol normalization: {} ({}) → {}", symbol, type, normalized);
        return normalized;
    }

    // ── Seed ────────────────────────────────────────────────────────────────

    /**
     * Veritabanı boşsa enstrümanları ve fallback quote'ları oluşturur.
     * Gerçek veriler PriceRefreshScheduler tarafından doldurulur.
     */
    public void seedIfEmpty() {
        Instant now = Instant.now();

        // FX — Yahoo: "USDTRY=X", "EURTRY=X"
        var usdtry = upsert("USDTRY", "USD/TRY",              InstrumentType.FX,        "USDTRY=X",    false);
        var eurtry = upsert("EURTRY", "EUR/TRY",              InstrumentType.FX,        "EURTRY=X",    false);
        // Emtia — Yahoo: "GC=F" (altın vadeli)
        var xauusd = upsert("XAUUSD", "Gold (XAU/USD)",       InstrumentType.COMMODITY, "GC=F",        false);
        // Endeks — BIST endeksleri gecikmeli
        var xu100  = upsert("XU100",  "BIST 100",             InstrumentType.INDEX,     "XU100.IS",    true);
        var xu050  = upsert("XU050",  "BIST 50",              InstrumentType.INDEX,     "XU050.IS",    true);
        var xu030  = upsert("XU030",  "BIST 30",              InstrumentType.INDEX,     "XU030.IS",    true);
        // Kripto — Yahoo: "BTC-USD"
        var btcusd = upsert("BTCUSD", "Bitcoin (BTC/USD)",    InstrumentType.CRYPTO,    "BTC-USD",     false);
        var ethusd = upsert("ETHUSD", "Ethereum (ETH/USD)",   InstrumentType.CRYPTO,    "ETH-USD",     false);
        var solusd = upsert("SOLUSD", "Solana (SOL/USD)",     InstrumentType.CRYPTO,    "SOL-USD",     false);
        // ABD Hisseleri — Yahoo: doğrudan ticker
        var aapl   = upsert("AAPL",   "Apple Inc.",           InstrumentType.STOCK,     "AAPL",        false);
        var msft   = upsert("MSFT",   "Microsoft Corp.",      InstrumentType.STOCK,     "MSFT",        false);
        var googl  = upsert("GOOGL",  "Alphabet Inc.",        InstrumentType.STOCK,     "GOOGL",       false);
        var amzn   = upsert("AMZN",   "Amazon.com Inc.",      InstrumentType.STOCK,     "AMZN",        false);
        var nvda   = upsert("NVDA",   "NVIDIA Corp.",         InstrumentType.STOCK,     "NVDA",        false);
        var tsla   = upsert("TSLA",   "Tesla Inc.",           InstrumentType.STOCK,     "TSLA",        false);
        var meta   = upsert("META",   "Meta Platforms Inc.",  InstrumentType.STOCK,     "META",        false);
        // BIST Hisseleri — Yahoo: ".IS" uzantısı, gecikmeli/EOD
        var thyao  = upsert("THYAO",  "Türk Hava Yolları",   InstrumentType.BIST,      "THYAO.IS",    true);
        var garan  = upsert("GARAN",  "Garanti BBVA",        InstrumentType.BIST,      "GARAN.IS",    true);
        var asels  = upsert("ASELS",  "Aselsan",             InstrumentType.BIST,      "ASELS.IS",    true);
        var sise   = upsert("SISE",   "Şişe Cam",            InstrumentType.BIST,      "SISE.IS",     true);
        var kchol  = upsert("KCHOL",  "Koç Holding",         InstrumentType.BIST,      "KCHOL.IS",    true);
        var eregl  = upsert("EREGL",  "Ereğli Demir Çelik",  InstrumentType.BIST,      "EREGL.IS",    true);
        var bimas  = upsert("BIMAS",  "BİM Mağazalar",       InstrumentType.BIST,      "BIMAS.IS",    true);
        var akbnk  = upsert("AKBNK",  "Akbank",              InstrumentType.BIST,      "AKBNK.IS",    true);
        var isctr  = upsert("ISCTR",  "İş Bankası",          InstrumentType.BIST,      "ISCTR.IS",    true);
        var tuprs  = upsert("TUPRS",  "Tüpraş",              InstrumentType.BIST,      "TUPRS.IS",    true);
        var sahol  = upsert("SAHOL",  "Sabancı Holding",     InstrumentType.BIST,      "SAHOL.IS",    true);
        var petkm  = upsert("PETKM",  "Petkim",              InstrumentType.BIST,      "PETKM.IS",    true);
        var tcell  = upsert("TCELL",  "Turkcell",            InstrumentType.BIST,      "TCELL.IS",    true);
        var vakbn  = upsert("VAKBN",  "Vakıfbank",           InstrumentType.BIST,      "VAKBN.IS",    true);
        var enkai  = upsert("ENKAI",  "Enka İnşaat",         InstrumentType.BIST,      "ENKAI.IS",    true);
        var kozal  = upsert("KOZAL",  "Koza Altın",          InstrumentType.BIST,      "KOZAL.IS",    true);
        var ttkom  = upsert("TTKOM",  "Türk Telekom",        InstrumentType.BIST,      "TTKOM.IS",    true);
        var pgsus  = upsert("PGSUS",  "Pegasus",             InstrumentType.BIST,      "PGSUS.IS",    true);
        var froto  = upsert("FROTO",  "Ford Otosan",         InstrumentType.BIST,      "FROTO.IS",    true);
        var toaso  = upsert("TOASO",  "Tofaş Oto",           InstrumentType.BIST,      "TOASO.IS",    true);
        var halkb  = upsert("HALKB",  "Halkbank",            InstrumentType.BIST,      "HALKB.IS",    true);
        var arclk  = upsert("ARCLK",  "Arçelik",             InstrumentType.BIST,      "ARCLK.IS",    true);
        var kozaa  = upsert("KOZAA",  "Koza Anadolu Metal",  InstrumentType.BIST,      "KOZAA.IS",    true);
        var tavhl  = upsert("TAVHL",  "TAV Havalimanları",   InstrumentType.BIST,      "TAVHL.IS",    true);
        var soda   = upsert("SODA",   "Soda Sanayii",        InstrumentType.BIST,      "SODA.IS",     true);

        // VİOP Vadeli İşlem Sözleşmeleri
        var xu030f = upsert("XU030F", "BIST 30 Vadeli",      InstrumentType.VIOP,      "XU030F",      true);
        var xu100f = upsert("XU100F", "BIST 100 Vadeli",     InstrumentType.VIOP,      "XU100F",      true);
        var usdtryf= upsert("USDTRYF","USD/TRY Vadeli",      InstrumentType.VIOP,      "USDTRYF",     false);
        var eurtryf= upsert("EURTRYF","EUR/TRY Vadeli",      InstrumentType.VIOP,      "EURTRYF",     false);
        var goldtryf=upsert("GOLDTRYF","Altın/TRY Vadeli",   InstrumentType.VIOP,      "GOLDTRYF",    false);

        // Tahvil ve Bonolar
        var tr2y   = upsert("TR2Y",   "2 Yıl Devlet Tahvili",InstrumentType.BOND,      "TR2Y",        true);
        var tr5y   = upsert("TR5Y",   "5 Yıl Devlet Tahvili",InstrumentType.BOND,      "TR5Y",        true);
        var tr10y  = upsert("TR10Y",  "10 Yıl Devlet Tahvili",InstrumentType.BOND,     "TR10Y",       true);
        var us2y   = upsert("US2Y",   "ABD 2 Yıl Tahvili",   InstrumentType.BOND,      "^IRX",        false);
        var us10y  = upsert("US10Y",  "ABD 10 Yıl Tahvili",  InstrumentType.BOND,      "^TNX",        false);

        // Yatırım Fonları (Popüler Türk Fonları)
        var ykbhis = upsert("YKBHIS", "YKB A.B.D. Hisse Senedi Fonu", InstrumentType.FUND, "YKBHIS", true);
        var isbalt = upsert("ISBALT", "İş Bankası Altın Fonu",        InstrumentType.FUND, "ISBALT", true);
        var akbtek = upsert("AKBTEK", "Akbank Teknoloji Sektör Fonu", InstrumentType.FUND, "AKBTEK", true);
        var garpar = upsert("GARPAR", "Garanti Para Piyasası Fonu",   InstrumentType.FUND, "GARPAR", true);
        var yapkre = upsert("YAPKRE", "Yapı Kredi Kira Sertifikası Fonu", InstrumentType.FUND, "YAPKRE", true);


        // Fallback quote'lar — test edilmiş gerçek değerlere yakın (Yahoo refresh gelene kadar)
        // Her enstrüman için ayrı kontrol yap
        seedQuoteIfMissing(usdtry, "44.75",    "0.05",   "0.11",  now);
        seedQuoteIfMissing(eurtry, "52.70",    "0.11",   "0.21",  now);
        seedQuoteIfMissing(xauusd, "4820.00",  "-5.00",  "-0.10", now);
        seedQuoteIfMissing(xu100,  "14200.00", "2.00",   "0.01",  now);
        seedQuoteIfMissing(xu050,  "9850.00",  "1.50",   "0.02",  now);
        seedQuoteIfMissing(xu030,  "8920.00",  "1.20",   "0.01",  now);
        seedQuoteIfMissing(btcusd, "73900.00", "-290.00","-0.39", now);
        seedQuoteIfMissing(ethusd, "2316.00",  "-7.00",  "-0.30", now);
        seedQuoteIfMissing(solusd, "83.00",    "-0.70",  "-0.84", now);
        seedQuoteIfMissing(aapl,   "258.83",   "-1.65",  "-0.63", now);
        seedQuoteIfMissing(msft,   "393.00",   "22.00",  "5.93",  now);
        seedQuoteIfMissing(googl,  "165.00",   "0.80",   "0.49",  now);
        seedQuoteIfMissing(amzn,   "185.00",   "1.10",   "0.60",  now);
        seedQuoteIfMissing(nvda,   "196.50",   "7.87",   "4.17",  now);
        seedQuoteIfMissing(tsla,   "250.00",   "-5.00",  "-1.96", now);
        seedQuoteIfMissing(meta,   "560.00",   "3.00",   "0.54",  now);
        seedQuoteIfMissing(thyao,  "322.00",   "-2.00",  "-0.62", now);
        seedQuoteIfMissing(garan,  "139.50",   "-0.50",  "-0.36", now);
        seedQuoteIfMissing(asels,  "412.00",   "0.25",   "0.06",  now);
        seedQuoteIfMissing(sise,   "52.85",    "0.45",   "0.86",  now);
        seedQuoteIfMissing(kchol,  "198.30",   "1.80",   "0.92",  now);
        seedQuoteIfMissing(eregl,  "56.40",    "-0.60",  "-1.05", now);
        seedQuoteIfMissing(bimas,  "478.95",   "2.15",   "0.45",  now);
        seedQuoteIfMissing(akbnk,  "72.35",    "-0.25",  "-0.34", now);
        seedQuoteIfMissing(isctr,  "9.47",     "0.03",   "0.32",  now);
        seedQuoteIfMissing(tuprs,  "178.40",   "-1.27",  "-0.71", now);
        seedQuoteIfMissing(sahol,  "89.60",    "0.80",   "0.90",  now);
        seedQuoteIfMissing(petkm,  "72.25",    "-0.35",  "-0.48", now);
        seedQuoteIfMissing(tcell,  "101.50",   "0.50",   "0.49",  now);
        seedQuoteIfMissing(vakbn,  "40.12",    "-0.18",  "-0.45", now);
        seedQuoteIfMissing(enkai,  "134.70",   "1.20",   "0.90",  now);
        seedQuoteIfMissing(kozal,  "672.35",   "4.85",   "0.73",  now);
        seedQuoteIfMissing(ttkom,  "271.40",   "-2.60",  "-0.95", now);
        seedQuoteIfMissing(pgsus,  "382.00",   "3.00",   "0.79",  now);
        seedQuoteIfMissing(froto,  "1426.00",  "12.00",  "0.85",  now);
        seedQuoteIfMissing(toaso,  "284.50",   "-1.50",  "-0.52", now);
        seedQuoteIfMissing(halkb,  "18.93",    "0.07",   "0.37",  now);
        seedQuoteIfMissing(arclk,  "134.20",   "-0.80",  "-0.59", now);
        seedQuoteIfMissing(kozaa,  "89.45",    "0.65",   "0.73",  now);
        seedQuoteIfMissing(tavhl,  "1892.00",  "15.00",  "0.80",  now);
        seedQuoteIfMissing(soda,   "45.78",    "-0.22",  "-0.48", now);
        
        // VİOP Vadeli İşlem Sözleşmeleri
        seedQuoteIfMissing(xu030f, "11850.00", "25.00",  "0.21",  now);
        seedQuoteIfMissing(xu100f, "14350.00", "30.00",  "0.21",  now);
        seedQuoteIfMissing(usdtryf,"44.85",    "0.10",   "0.22",  now);
        seedQuoteIfMissing(eurtryf,"52.85",    "0.15",   "0.28",  now);
        seedQuoteIfMissing(goldtryf,"4850.00", "10.00",  "0.21",  now);
        
        // Tahvil ve Bonolar (Getiri oranları %)
        seedQuoteIfMissing(tr2y,   "48.50",    "0.25",   "0.52",  now);
        seedQuoteIfMissing(tr5y,   "47.80",    "0.15",   "0.31",  now);
        seedQuoteIfMissing(tr10y,  "46.90",    "0.10",   "0.21",  now);
        seedQuoteIfMissing(us2y,   "4.25",     "-0.05",  "-1.16", now);
        seedQuoteIfMissing(us10y,  "4.45",     "-0.03",  "-0.67", now);
        
        // Yatırım Fonları (Birim Pay Değeri)
        seedQuoteIfMissing(ykbhis, "0.285",    "0.002",  "0.71",  now);
        seedQuoteIfMissing(isbalt, "0.198",    "0.001",  "0.51",  now);
        seedQuoteIfMissing(akbtek, "0.342",    "0.003",  "0.89",  now);
        seedQuoteIfMissing(garpar, "0.156",    "0.000",  "0.00",  now);
        seedQuoteIfMissing(yapkre, "0.189",    "0.001",  "0.53",  now);

        // Fallback candle'lar — gerçek değerlere yakın başlangıç noktaları
        // Her enstrüman için ayrı kontrol yap
        seedCandlesIfMissing(usdtry, "44.00");   seedCandlesIfMissing(eurtry, "52.00");
        seedCandlesIfMissing(xauusd, "4700.00"); seedCandlesIfMissing(xu100,  "14000.00");
        seedCandlesIfMissing(xu050,  "9800.00"); seedCandlesIfMissing(xu030,  "8900.00");
        seedCandlesIfMissing(btcusd, "70000.00");seedCandlesIfMissing(ethusd, "2200.00");
        seedCandlesIfMissing(solusd, "80.00");   seedCandlesIfMissing(aapl,   "255.00");
        seedCandlesIfMissing(msft,   "380.00");  seedCandlesIfMissing(googl,  "160.00");
        seedCandlesIfMissing(amzn,   "180.00");  seedCandlesIfMissing(nvda,   "185.00");
        seedCandlesIfMissing(tsla,   "250.00");  seedCandlesIfMissing(meta,   "550.00");
        seedCandlesIfMissing(thyao,  "315.00");  seedCandlesIfMissing(garan,  "135.00");
        seedCandlesIfMissing(asels,  "405.00");  seedCandlesIfMissing(sise,   "46.00");
        seedCandlesIfMissing(kchol,  "180.00");  seedCandlesIfMissing(eregl,  "43.00");
        seedCandlesIfMissing(bimas,  "510.00");  seedCandlesIfMissing(akbnk,  "70.00");
        seedCandlesIfMissing(isctr,  "18.00");   seedCandlesIfMissing(tuprs,  "190.00");
        seedCandlesIfMissing(sahol,  "88.00");   seedCandlesIfMissing(petkm,  "71.00");
        seedCandlesIfMissing(tcell,  "100.00");  seedCandlesIfMissing(vakbn,  "39.50");
        seedCandlesIfMissing(enkai,  "132.00");  seedCandlesIfMissing(kozal,  "660.00");
        seedCandlesIfMissing(ttkom,  "268.00");  seedCandlesIfMissing(pgsus,  "375.00");
        seedCandlesIfMissing(froto,  "1400.00"); seedCandlesIfMissing(toaso,  "280.00");
        seedCandlesIfMissing(halkb,  "18.50");   seedCandlesIfMissing(arclk,  "132.00");
        seedCandlesIfMissing(kozaa,  "88.00");   seedCandlesIfMissing(tavhl,  "1850.00");
        seedCandlesIfMissing(soda,   "45.00");
        
        // VİOP Vadeli İşlem Sözleşmeleri
        seedCandlesIfMissing(xu030f, "11800.00"); seedCandlesIfMissing(xu100f, "14300.00");
        seedCandlesIfMissing(usdtryf,"44.70");    seedCandlesIfMissing(eurtryf,"52.60");
        seedCandlesIfMissing(goldtryf,"4800.00");
        
        // Tahvil ve Bonolar
        seedCandlesIfMissing(tr2y,   "48.00");    seedCandlesIfMissing(tr5y,   "47.50");
        seedCandlesIfMissing(tr10y,  "46.70");    seedCandlesIfMissing(us2y,   "4.30");
        seedCandlesIfMissing(us10y,  "4.50");
        
        // Yatırım Fonları
        seedCandlesIfMissing(ykbhis, "0.280");    seedCandlesIfMissing(isbalt, "0.195");
        seedCandlesIfMissing(akbtek, "0.335");    seedCandlesIfMissing(garpar, "0.156");
        seedCandlesIfMissing(yapkre, "0.186");
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public List<MarketInstrument> instruments() {
        seedIfEmpty();
        return instrumentRepo.findAll();
    }

    /** Tek sembol için anlık fiyat (portföy modal'ı için) */
    public Map<String, Object> latestPrice(String symbol) {
        seedIfEmpty();
        MarketInstrument inst = instrumentRepo.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));

        BigDecimal last = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                .map(MarketQuote::getLast)
                .orElse(BigDecimal.ZERO);

        return Map.of(
                "symbol",  inst.getSymbol(),
                "price",   last,
                "name",    inst.getName(),
                "type",    inst.getInstrumentType() != null ? inst.getInstrumentType().name() : "UNKNOWN",
                "delayed", inst.isDelayed()
        );
    }

    @Cacheable(cacheNames = "marketSummary")
    public List<MarketSummaryItem> summary() {
        seedIfEmpty();
        List<MarketSummaryItem> out = new ArrayList<>();
        for (MarketInstrument inst : instrumentRepo.findAll()) {
            // instrument_type null olan eski satırları atla
            if (inst.getInstrumentType() == null) continue;
            quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst).ifPresent(q ->
                    out.add(new MarketSummaryItem(
                            inst.getSymbol(),
                            inst.getName(),
                            inst.getInstrumentType().name(),
                            q.getLast(),
                            q.getChangeAbs(),
                            q.getChangePct(),
                            q.getAsOf(),
                            inst.isDelayed(),
                            inst.isDelayed() ? "Gecikmeli" : null
                    ))
            );
        }
        return out;
    }

    @Cacheable(cacheNames = "marketHistory", key = "#symbol + ':' + #period")
    public List<MarketHistoryPoint> history(String symbol, String period) {
        seedIfEmpty();
        
        MarketInstrument inst = instrumentRepo.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));

        // Normalize symbol for Yahoo Finance
        String yahooSymbol = normalizeSymbolForYahoo(inst.getSymbol(), inst.getInstrumentType());
        
        // Map UI periods to Yahoo Finance range and interval
        YahooRangeConfig config = mapPeriodToYahooRange(period);
        
        log.info("Fetching chart data: symbol={} yahooSymbol={} period={} range={} interval={}", 
                symbol, yahooSymbol, period, config.range(), config.interval());

        try {
            // Fetch fresh historical data from Yahoo Finance
            List<YahooPriceFetcher.DayClose> yahooData = yahooPriceFetcher.fetchHistory(
                    yahooSymbol, config.range(), config.interval());

            if (yahooData.isEmpty()) {
                log.warn("No Yahoo data received for symbol={} yahooSymbol={} range={} interval={}", 
                        symbol, yahooSymbol, config.range(), config.interval());
                return List.of();
            }

            // Clean and validate data
            List<YahooPriceFetcher.DayClose> cleanedData = cleanHistoricalData(yahooData, symbol);
            
            // Convert to MarketHistoryPoint DTOs
            List<MarketHistoryPoint> result = cleanedData.stream()
                    .map(d -> new MarketHistoryPoint(d.day(), d.close(), d.label()))
                    .toList();

            log.info("Chart data processed: symbol={} yahooSymbol={} period={} -> {} clean points", 
                    symbol, yahooSymbol, period, result.size());

            // Log first and last points for debugging
            if (!result.isEmpty()) {
                MarketHistoryPoint first = result.get(0);
                MarketHistoryPoint last = result.get(result.size() - 1);
                log.info("Chart range: {} (close={}) to {} (close={})", 
                        first.label(), first.close(), last.label(), last.close());
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to fetch Yahoo chart data for symbol={} yahooSymbol={}: {}", 
                    symbol, yahooSymbol, e.getMessage(), e);
            
            // Fallback to database candles if Yahoo fails
            log.info("Falling back to database candles for symbol={}", symbol);
            return fallbackToDatabaseHistory(inst, period);
        }
    }

    /**
     * Map UI period to Yahoo Finance range and interval parameters.
     */
    private YahooRangeConfig mapPeriodToYahooRange(String period) {
        return switch ((period == null ? "30D" : period).toUpperCase()) {
            case "1D", "1G" -> new YahooRangeConfig("1d", "5m");   // Intraday 5-minute
            case "5D", "5G" -> new YahooRangeConfig("5d", "1h");   // 5 days, hourly
            case "30D", "1A" -> new YahooRangeConfig("1mo", "1d"); // 1 month, daily
            case "1Y" -> new YahooRangeConfig("1y", "1d");         // 1 year, daily
            default -> new YahooRangeConfig("1mo", "1d");          // Default to 1 month
        };
    }

    /**
     * Clean and validate historical data from Yahoo Finance.
     */
    private List<YahooPriceFetcher.DayClose> cleanHistoricalData(
            List<YahooPriceFetcher.DayClose> rawData, String symbol) {
        
        List<YahooPriceFetcher.DayClose> cleaned = new ArrayList<>();
        int nullCount = 0;
        int zeroCount = 0;
        int outlierCount = 0;

        for (int i = 0; i < rawData.size(); i++) {
            YahooPriceFetcher.DayClose candle = rawData.get(i);
            
            // Skip null candles
            if (candle == null || candle.close() == null) {
                nullCount++;
                continue;
            }

            // Skip zero or negative prices
            if (candle.close().compareTo(BigDecimal.ZERO) <= 0) {
                zeroCount++;
                continue;
            }

            // Outlier detection: check for suspicious spikes
            if (!cleaned.isEmpty()) {
                YahooPriceFetcher.DayClose prev = cleaned.get(cleaned.size() - 1);
                double prevPrice = prev.close().doubleValue();
                double currPrice = candle.close().doubleValue();
                
                if (prevPrice > 0) {
                    double changePct = Math.abs((currPrice - prevPrice) / prevPrice) * 100;
                    if (changePct > 40) {
                        log.warn("Suspicious price spike detected for {}: {} -> {} ({}% change) at {}", 
                                symbol, prevPrice, currPrice, String.format("%.1f", changePct), candle.label());
                        outlierCount++;
                        // Still include the point but log it for investigation
                    }
                }
            }

            cleaned.add(candle);
        }

        if (nullCount > 0) log.debug("Removed {} null candles for {}", nullCount, symbol);
        if (zeroCount > 0) log.debug("Removed {} zero/negative candles for {}", zeroCount, symbol);
        if (outlierCount > 0) log.warn("Found {} potential outliers for {}", outlierCount, symbol);

        log.debug("Data cleaning for {}: {} raw -> {} clean candles", symbol, rawData.size(), cleaned.size());
        return cleaned;
    }

    /**
     * Fallback to database candles when Yahoo Finance fails.
     */
    private List<MarketHistoryPoint> fallbackToDatabaseHistory(MarketInstrument inst, String period) {
        int days = switch ((period == null ? "30D" : period).toUpperCase()) {
            case "1D", "1G" -> 2;
            case "5D", "5G" -> 5;
            case "30D", "1A" -> 30;
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

    /**
     * Configuration record for Yahoo Finance range and interval parameters.
     */
    private record YahooRangeConfig(String range, String interval) {}

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MarketInstrument upsert(String symbol, String name, InstrumentType type,
                                    String providerSymbol, boolean delayed) {
        return instrumentRepo.findBySymbol(symbol).orElseGet(() -> {
            // Use centralized symbol normalization instead of hardcoded providerSymbol
            String normalizedSymbol = normalizeSymbolForYahoo(symbol, type);
            log.info("Creating instrument: symbol={} type={} yahooSymbol={} (provided: {})", 
                    symbol, type, normalizedSymbol, providerSymbol);
            
            return instrumentRepo.save(new MarketInstrument(
                    symbol, name, type,
                    MarketDataProvider.YAHOO, normalizedSymbol, delayed));
        });
    }

    private void seedQuote(MarketInstrument inst, String last, String changeAbs,
                           String changePct, Instant now) {
        quoteRepo.save(new MarketQuote(inst, bd(last), bd(changeAbs), bd(changePct), now));
    }

    /**
     * Sadece quote yoksa seed et (mevcut quote'ları korur)
     */
    private void seedQuoteIfMissing(MarketInstrument inst, String last, String changeAbs,
                                    String changePct, Instant now) {
        // Bu enstrüman için quote var mı kontrol et
        boolean hasQuote = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst).isPresent();
        if (!hasQuote) {
            log.info("Seeding missing quote for: {}", inst.getSymbol());
            seedQuote(inst, last, changeAbs, changePct, now);
        }
    }

    private void seedCandles(MarketInstrument inst, String base) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(30);
        Random rnd = new Random();
        BigDecimal basePrice = bd(base);

        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            double variation = (rnd.nextDouble() - 0.5) * 0.04;
            BigDecimal close = basePrice.multiply(BigDecimal.valueOf(1 + variation));
            candleRepo.save(new MarketCandle(inst, d, close));
        }
    }

    /**
     * Sadece candle yoksa seed et (mevcut candle'ları korur)
     */
    private void seedCandlesIfMissing(MarketInstrument inst, String base) {
        // Bu enstrüman için candle var mı kontrol et
        long candleCount = candleRepo.countByInstrument(inst);
        if (candleCount == 0) {
            log.info("Seeding missing candles for: {}", inst.getSymbol());
            seedCandles(inst, base);
        }
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
