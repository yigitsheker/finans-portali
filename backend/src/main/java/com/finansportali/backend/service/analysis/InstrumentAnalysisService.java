package com.finansportali.backend.service.analysis;

import com.finansportali.backend.dto.response.analysis.AnalysisDetailDto;
import com.finansportali.backend.dto.response.analysis.AnalysisInstrumentDto;
import com.finansportali.backend.dto.response.analysis.PricePointDto;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.entity.InflationDataPoint;
import com.finansportali.backend.entity.InvestmentFund;
import com.finansportali.backend.entity.MarketCandle;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.service.ExchangeRateService;
import com.finansportali.backend.service.InflationService;
import com.finansportali.backend.service.InvestmentFundService;
import com.finansportali.backend.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Aggregates the cross-asset view used by the Analysis page. Pulls daily
 * data from {@link MarketService} (stocks/crypto/FX/commodities), folds in
 * fund returns from {@link InvestmentFundService}, and adds latest TR + US
 * inflation rows. Weekly/monthly/yearly changes for market instruments are
 * derived from {@link MarketCandle} history when available.
 */
@Service
public class InstrumentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentAnalysisService.class);

    private static final String CAT_STOCK = "STOCK";
    private static final String CAT_CRYPTO = "CRYPTO";
    private static final String CAT_COMMODITY = "COMMODITY";
    private static final String CAT_INDEX = "INDEX";

    private final MarketService marketService;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketCandleRepository candleRepo;
    private final InvestmentFundService fundService;
    private final InflationService inflationService;
    private final ExchangeRateService exchangeRateService;
    private final RiskProfileService riskProfile;
    private final TechnicalAnalysisService ta;

    public InstrumentAnalysisService(MarketService marketService,
                                     MarketInstrumentRepository instrumentRepo,
                                     MarketCandleRepository candleRepo,
                                     InvestmentFundService fundService,
                                     InflationService inflationService,
                                     ExchangeRateService exchangeRateService,
                                     RiskProfileService riskProfile,
                                     TechnicalAnalysisService ta) {
        this.marketService = marketService;
        this.instrumentRepo = instrumentRepo;
        this.candleRepo = candleRepo;
        this.fundService = fundService;
        this.inflationService = inflationService;
        this.exchangeRateService = exchangeRateService;
        this.riskProfile = riskProfile;
        this.ta = ta;
    }

    /**
     * Builds the full cross-asset table for the Analysis page: market
     * instruments, TCMB FX, funds and inflation rows, de-duplicated and
     * each stamped with its inflation-adjusted (real) yearly return.
     */
    public List<AnalysisInstrumentDto> getAllInstruments() {
        List<AnalysisInstrumentDto> out = new ArrayList<>();
        // De-dup set keyed by "<CATEGORY>:<SYMBOL>" so the TCMB FX feed
        // (which includes USDTRY / EURTRY) doesn't add rows that the market
        // summary already produced.
        java.util.Set<String> seen = new java.util.HashSet<>();

        // Pull the latest TR + US CPI yearly figures up-front so each
        // instrument can be tagged with its inflation-adjusted yearly
        // return. TRY-denominated rows lean on TR TÜFE; everything else
        // (USD, EUR, …) uses US CPI as the proxy.
        BigDecimal trCpi = latestYearlyInflation("TR");
        BigDecimal usCpi = latestYearlyInflation("US");

        // 1) Stocks/crypto/FX/commodities from MarketService summary
        try {
            for (MarketSummaryItem item : marketService.summary()) {
                AnalysisInstrumentDto dto = toMarketDto(item);
                out.add(dto);
                seen.add(dto.getCategory() + ":" + dto.getSymbol());
            }
        } catch (RuntimeException e) {
            log.warn("[Analysis] market summary failed: {}", e.getMessage());
        }

        // 2) TCMB FX rates — adds the long-tail of currencies (GBP, JPY,
        //    CHF, CNY, SAR, RUB, …) that the market summary doesn't carry.
        try {
            for (ExchangeRate r : exchangeRateService.getLatestRates()) {
                AnalysisInstrumentDto dto = toTcmbFxDto(r);
                if (dto == null) continue;
                if (seen.add(dto.getCategory() + ":" + dto.getSymbol())) {
                    out.add(dto);
                }
            }
        } catch (RuntimeException e) {
            log.warn("[Analysis] TCMB FX fetch failed: {}", e.getMessage());
        }

        // 3) Investment funds — TEFAS data already carries weekly/monthly/yearly returns
        try {
            for (InvestmentFund f : fundService.getAllFunds()) {
                if (f.getUnitPrice() == null) continue;
                out.add(toFundDto(f));
            }
        } catch (RuntimeException e) {
            log.warn("[Analysis] funds fetch failed: {}", e.getMessage());
        }

        // 4) Inflation — one row each for TR and US, latest available month
        addInflationRow(out, "TR");
        addInflationRow(out, "US");

        // Post-pass: stamp real (inflation-adjusted) yearly return on every
        // row. Done here, after every category contributes, so callers see
        // a uniform shape regardless of which sub-builder produced the row.
        for (AnalysisInstrumentDto dto : out) {
            applyRealReturn(dto, trCpi, usCpi);
        }

        return out;
    }

    /**
     * Fills realChangeYearly + beatsInflation using the currency-appropriate
     * CPI reference. Skips inflation rows themselves (they ARE the
     * reference; comparing them to themselves is meaningless).
     */
    private void applyRealReturn(AnalysisInstrumentDto dto, BigDecimal trCpi, BigDecimal usCpi) {
        if (dto == null) return;
        if ("INFLATION_TR".equals(dto.getCategory()) || "INFLATION_US".equals(dto.getCategory())) return;
        BigDecimal nominal = dto.getChangeYearly();
        if (nominal == null) return;
        BigDecimal cpi = "TRY".equals(dto.getCurrency()) ? trCpi : usCpi;
        if (cpi == null) return;
        // r_real = (1 + r_nom/100) / (1 + r_cpi/100) - 1, scaled back to %.
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal numerator = BigDecimal.ONE.add(nominal.divide(hundred, 8, RoundingMode.HALF_UP));
        BigDecimal denominator = BigDecimal.ONE.add(cpi.divide(hundred, 8, RoundingMode.HALF_UP));
        if (denominator.signum() == 0) return;
        BigDecimal real = numerator.divide(denominator, 8, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(hundred)
                .setScale(2, RoundingMode.HALF_UP);
        dto.setRealChangeYearly(real);
        dto.setBeatsInflation(real.signum() > 0);
    }

    private BigDecimal latestYearlyInflation(String country) {
        try {
            return inflationService.getLatest(country)
                    .map(p -> p.getCpiYearlyChange())
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("[Analysis] latest CPI lookup failed for {}: {}", country, e.getMessage());
            return null;
        }
    }

    /**
     * Detail view for a single symbol — resolves it as a market instrument
     * first (covers FX/crypto/commodities), then falls back to funds.
     * Empty when the symbol is blank or unknown.
     */
    public Optional<AnalysisDetailDto> getDetail(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        String upper = symbol.toUpperCase(Locale.ROOT);

        // Market instrument path (also covers FX, crypto, commodities).
        Optional<MarketInstrument> instOpt = instrumentRepo.findBySymbol(upper);
        if (instOpt.isPresent()) {
            return Optional.of(buildMarketDetail(instOpt.get()));
        }

        // Fund path.
        return fundService.getFundByCode(upper).map(this::buildFundDetail);
    }

    // ── Market instruments ───────────────────────────────────────────────

    private AnalysisInstrumentDto toMarketDto(MarketSummaryItem item) {
        String category = mapMarketCategory(item.type());
        BigDecimal daily = item.changePct();
        BigDecimal weekly = null;
        BigDecimal monthly = null;
        BigDecimal yearly = null;

        // Pull weekly/monthly/yearly changes from candle history when we have
        // a backing instrument row. Missing values stay null — the table
        // shows "—".
        Optional<MarketInstrument> inst = instrumentRepo.findBySymbol(item.symbol());
        if (inst.isPresent() && item.last() != null) {
            BigDecimal last = item.last();
            weekly = changeOverDays(inst.get(), last, 7);
            monthly = changeOverDays(inst.get(), last, 30);
            yearly = changeOverDays(inst.get(), last, 365);
        }

        AnalysisInstrumentDto dto = new AnalysisInstrumentDto();
        dto.setSymbol(item.symbol());
        dto.setName(item.name());
        dto.setCategory(category);
        dto.setValue(item.last());
        // Currency selection drives which CPI the row gets compared
        // against. We branch on the RAW item.type() (BIST vs STOCK), not
        // the unified category — the older "isBistSymbol" length heuristic
        // mis-classified US tickers like AAPL/TSLA as TRY and was breaking
        // the "beats inflation" filter.
        dto.setCurrency(currencyForType(item.type(), item.symbol()));
        dto.setChangeDaily(daily);
        dto.setChangeWeekly(weekly);
        dto.setChangeMonthly(monthly);
        dto.setChangeYearly(yearly);
        dto.setRiskLevel(riskProfile.classify(category, yearly));
        dto.setShortTermSignal(ta.shortTermSignal(weekly, monthly));
        dto.setLongTermSignal(ta.longTermSignal(monthly, yearly));
        dto.setUpdatedAt(item.asOf());
        return dto;
    }

    private BigDecimal changeOverDays(MarketInstrument inst, BigDecimal latest, int daysBack) {
        try {
            LocalDate target = LocalDate.now().minusDays(daysBack);
            // Search forward FROM target up to +14 days. The price refresh
            // scheduler only stores ~365 days of history (Yahoo "1y"), so
            // for the yearly window the search must look forward — the
            // earlier `[target-7, target]` window fell entirely OUTSIDE the
            // stored range, which is why every Yıllık cell showed "—". The
            // 14-day buffer absorbs weekends, holidays and partial gaps.
            List<MarketCandle> candles = candleRepo
                    .findByInstrumentAndDayBetweenOrderByDayAsc(inst, target, target.plusDays(14));
            if (candles.isEmpty()) return null;
            BigDecimal base = candles.get(0).getClose();
            if (base == null || base.signum() == 0) return null;
            return latest.subtract(base)
                    .divide(base, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String mapMarketCategory(String type) {
        if (type == null) return "OTHER";
        return switch (type) {
            case CAT_STOCK, "BIST" -> CAT_STOCK;
            case CAT_CRYPTO -> CAT_CRYPTO;
            case "FX" -> "FX";
            case CAT_COMMODITY -> CAT_COMMODITY;
            case CAT_INDEX -> CAT_INDEX;
            default -> type;
        };
    }

    /**
     * Picks the trading currency from the raw MarketSummaryItem type, which
     * still distinguishes BIST from STOCK before mapMarketCategory collapses
     * them. BIST indexes (XU100/XU050/XU030) are denominated in TRY too, so
     * we catch them via the symbol prefix when type=INDEX. Replaces an
     * earlier `currencyForCategory` that leaned on a 4-5-letter-uppercase
     * heuristic — that one wrongly tagged AAPL/TSLA/NVDA as TRY and broke
     * the inflation-comparison column.
     */
    private String currencyForType(String type, String symbol) {
        if (type == null) return "TRY";
        return switch (type) {
            case "BIST" -> "TRY";
            case CAT_STOCK -> "USD";              // US equities in our seed list
            case CAT_INDEX -> symbol != null && symbol.startsWith("XU") ? "TRY" : "USD";
            case CAT_CRYPTO, "FX", CAT_COMMODITY -> "USD";
            default -> "TRY";
        };
    }

    // ── TCMB FX ──────────────────────────────────────────────────────────

    /**
     * Maps a TCMB exchange-rate row to a generic FX analysis row. Symbol
     * uses the {currency}TRY convention (USD→USDTRY, EUR→EURTRY, GBP→
     * GBPTRY …) so it lines up with what the market summary already emits.
     * Value is the mid of buying/selling rates. Change percentages stay
     * null — historical day-over-day TCMB tracking would need a separate
     * series; the table renders "—" gracefully when nulls.
     */
    private AnalysisInstrumentDto toTcmbFxDto(ExchangeRate r) {
        if (r == null || r.getCurrencyCode() == null) return null;
        BigDecimal mid = midOf(r.getBuyingRate(), r.getSellingRate());
        if (mid == null) return null;

        AnalysisInstrumentDto dto = new AnalysisInstrumentDto();
        dto.setSymbol(r.getCurrencyCode().toUpperCase(Locale.ROOT) + "TRY");
        String name = r.getCurrencyName();
        dto.setName((name != null && !name.isBlank() ? name : r.getCurrencyCode()) + "/TRY");
        dto.setCategory("FX");
        dto.setValue(mid);
        dto.setCurrency("TRY");
        dto.setChangeDaily(null);
        dto.setChangeWeekly(null);
        dto.setChangeMonthly(null);
        dto.setChangeYearly(null);
        dto.setRiskLevel(riskProfile.classify("FX", null));
        dto.setShortTermSignal(TechnicalAnalysisService.NEUTRAL);
        dto.setLongTermSignal(TechnicalAnalysisService.NEUTRAL);
        if (r.getRateDate() != null) {
            dto.setUpdatedAt(r.getRateDate().atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        return dto;
    }

    private BigDecimal midOf(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    // ── Funds ────────────────────────────────────────────────────────────

    private AnalysisInstrumentDto toFundDto(InvestmentFund f) {
        AnalysisInstrumentDto dto = new AnalysisInstrumentDto();
        dto.setSymbol(f.getFundCode());
        dto.setName(f.getFundName());
        dto.setCategory("FUND");
        dto.setValue(f.getUnitPrice());
        dto.setCurrency("TRY");
        dto.setChangeDaily(f.getDailyReturn());
        dto.setChangeWeekly(f.getWeeklyReturn());
        dto.setChangeMonthly(f.getMonthlyReturn());
        dto.setChangeYearly(f.getYearlyReturn());
        dto.setRiskLevel(fundRisk(f));
        dto.setShortTermSignal(ta.shortTermSignal(f.getWeeklyReturn(), f.getMonthlyReturn()));
        dto.setLongTermSignal(ta.longTermSignal(f.getMonthlyReturn(), f.getYearlyReturn()));
        if (f.getPriceDate() != null) {
            dto.setUpdatedAt(f.getPriceDate().atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        return dto;
    }

    /** Fund risk: prefer the SPK 1-7 label when available, fall back to category default. */
    private String fundRisk(InvestmentFund f) {
        String r = f.getRiskLevel();
        if (r == null) return riskProfile.classify("FUND", f.getYearlyReturn());
        try {
            int level = Integer.parseInt(r.trim());
            if (level <= 2) return RiskProfileService.LOW;
            if (level <= 5) return RiskProfileService.MEDIUM;
            return RiskProfileService.HIGH;
        } catch (NumberFormatException e) {
            return riskProfile.classify("FUND", f.getYearlyReturn());
        }
    }

    // ── Inflation ────────────────────────────────────────────────────────

    private void addInflationRow(List<AnalysisInstrumentDto> out, String country) {
        try {
            Optional<InflationDataPoint> latest = inflationService.getLatest(country);
            if (latest.isEmpty()) return;
            InflationDataPoint p = latest.get();
            AnalysisInstrumentDto dto = new AnalysisInstrumentDto();
            dto.setSymbol(country.equals("TR") ? "TR-CPI" : "US-CPI");
            dto.setName(country.equals("TR") ? "Türkiye TÜFE" : "ABD CPI");
            dto.setCategory(country.equals("TR") ? "INFLATION_TR" : "INFLATION_US");
            dto.setValue(p.getCpiIndex());
            dto.setCurrency(null);
            dto.setChangeDaily(null);
            dto.setChangeWeekly(null);
            dto.setChangeMonthly(p.getCpiMonthlyChange());
            dto.setChangeYearly(p.getCpiYearlyChange());
            dto.setRiskLevel(RiskProfileService.LOW);
            dto.setShortTermSignal(TechnicalAnalysisService.NEUTRAL);
            dto.setLongTermSignal(TechnicalAnalysisService.NEUTRAL);
            if (p.getPeriodDate() != null) {
                dto.setUpdatedAt(p.getPeriodDate().atStartOfDay(ZoneOffset.UTC).toInstant());
            }
            out.add(dto);
        } catch (RuntimeException e) {
            log.warn("[Analysis] inflation row failed for {}: {}", country, e.getMessage());
        }
    }

    // ── Detail builders ──────────────────────────────────────────────────

    private AnalysisDetailDto buildMarketDetail(MarketInstrument inst) {
        // Re-derive summary from current market data via summary() — guarantees
        // the detail view is consistent with the table row the user clicked.
        AnalysisInstrumentDto summary = marketService.summary().stream()
                .filter(i -> i.symbol().equalsIgnoreCase(inst.getSymbol()))
                .findFirst()
                .map(this::toMarketDto)
                .orElse(null);

        AnalysisDetailDto d = new AnalysisDetailDto();
        d.setSummary(summary);
        BigDecimal weekly  = summary != null ? summary.getChangeWeekly() : null;
        BigDecimal monthly = summary != null ? summary.getChangeMonthly() : null;
        BigDecimal yearly  = summary != null ? summary.getChangeYearly() : null;
        d.setTrend(ta.trend(weekly, monthly));
        d.setVolatility(ta.volatility(monthly, yearly));
        d.setShortTermNote(buildShortNote(summary));
        d.setLongTermNote(buildLongNote(summary));
        d.setRiskNote(buildRiskNote(summary));
        d.setSeries(loadSeries(inst, 30));
        return d;
    }

    private AnalysisDetailDto buildFundDetail(InvestmentFund f) {
        AnalysisInstrumentDto summary = toFundDto(f);
        AnalysisDetailDto d = new AnalysisDetailDto();
        d.setSummary(summary);
        d.setTrend(ta.trend(summary.getChangeWeekly(), summary.getChangeMonthly()));
        d.setVolatility(ta.volatility(summary.getChangeMonthly(), summary.getChangeYearly()));
        d.setShortTermNote(buildShortNote(summary));
        d.setLongTermNote(buildLongNote(summary));
        d.setRiskNote(buildRiskNote(summary));
        d.setSeries(List.of()); // fund history not yet wired to the analysis page
        return d;
    }

    private List<PricePointDto> loadSeries(MarketInstrument inst, int days) {
        List<MarketCandle> candles = candleRepo
                .findByInstrumentAndDayBetweenOrderByDayAsc(inst, LocalDate.now().minusDays(days), LocalDate.now());
        List<PricePointDto> out = new ArrayList<>(candles.size());
        for (MarketCandle c : candles) {
            out.add(new PricePointDto(c.getDay(), c.getClose()));
        }
        return out;
    }

    private String buildShortNote(AnalysisInstrumentDto s) {
        if (s == null) return "Kısa vadeli veri yetersiz.";
        BigDecimal w = s.getChangeWeekly();
        if (w == null) return "Haftalık değişim verisi henüz mevcut değil.";
        double wv = w.doubleValue();
        if (wv > 3) return String.format(Locale.US, "Son hafta %+.2f%% — kısa vadeli momentum pozitif.", wv);
        if (wv < -3) return String.format(Locale.US, "Son hafta %+.2f%% — kısa vadeli baskı sürüyor.", wv);
        return String.format(Locale.US, "Son hafta %+.2f%% — kısa vadede yatay seyir.", wv);
    }

    private String buildLongNote(AnalysisInstrumentDto s) {
        if (s == null) return "Uzun vadeli veri yetersiz.";
        BigDecimal y = s.getChangeYearly();
        if (y == null) return "Yıllık değişim verisi henüz mevcut değil.";
        double yv = y.doubleValue();
        if (yv > 30) return String.format(Locale.US, "Yıllık %+.2f%% — uzun vadeli trend güçlü pozitif.", yv);
        if (yv < -30) return String.format(Locale.US, "Yıllık %+.2f%% — uzun vadede ciddi düşüş.", yv);
        return String.format(Locale.US, "Yıllık %+.2f%% — uzun vadede ılımlı seyir.", yv);
    }

    private String buildRiskNote(AnalysisInstrumentDto s) {
        if (s == null) return "Risk değerlendirmesi için yeterli veri yok.";
        return switch (s.getRiskLevel()) {
            case RiskProfileService.HIGH -> "Yüksek volatilite — kısa vadede sert hareketlere açık.";
            case RiskProfileService.MEDIUM -> "Orta seviye risk — dengeli portföylerde yer bulabilir.";
            case RiskProfileService.LOW -> "Düşük risk — koruma amaçlı pozisyonlara uygun.";
            default -> "Belirsiz risk profili.";
        };
    }

    /** Last close timestamp helper for the chatbot if it needs context. */
    public Instant latestUpdate() {
        return Instant.now();
    }
}
