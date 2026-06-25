package com.finansportali.backend.service.analysis;

import com.finansportali.backend.dto.response.analysis.AnalysisDetailDto;
import com.finansportali.backend.dto.response.analysis.AnalysisInstrumentDto;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.entity.InflationDataPoint;
import com.finansportali.backend.entity.InvestmentFund;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.service.ExchangeRateService;
import com.finansportali.backend.service.InflationService;
import com.finansportali.backend.service.InvestmentFundService;
import com.finansportali.backend.service.MarketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.finansportali.backend.entity.MarketCandle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Aggregator-level tests for {@link InstrumentAnalysisService}. Uses a real
 * {@link RiskProfileService} + {@link TechnicalAnalysisService} (both are
 * pure / cheap), and mocks every repo & data-source service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstrumentAnalysisServiceTest {

    @Mock private MarketService marketService;
    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private MarketCandleRepository candleRepo;
    @Mock private InvestmentFundService fundService;
    @Mock private InflationService inflationService;
    @Mock private ExchangeRateService exchangeRateService;

    private InstrumentAnalysisService service;

    @BeforeEach
    void setUp() {
        RiskProfileService risk = new RiskProfileService();
        TechnicalSignalService ta = new TechnicalSignalService();
        service = new InstrumentAnalysisService(
                marketService, instrumentRepo, candleRepo, fundService,
                inflationService, exchangeRateService, risk, ta);
        // Default empty state for everything — individual tests stub what they need.
        when(marketService.summary()).thenReturn(List.of());
        when(exchangeRateService.getLatestRates()).thenReturn(List.of());
        when(fundService.getAllFunds()).thenReturn(List.of());
        when(inflationService.getLatest(anyString())).thenReturn(Optional.empty());
        when(instrumentRepo.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(any(), any(), any()))
                .thenReturn(List.of());
    }

    private static MarketSummaryItem summary(String symbol, String name, String type,
                                             BigDecimal last, BigDecimal pct) {
        return new MarketSummaryItem(symbol, name, type, last, BigDecimal.ZERO, pct,
                Instant.now(), false, null, null);
    }

    private static InvestmentFund fund(String code, String name, String unitPrice) {
        return new InvestmentFund(code, name, "Hisse", "Garanti Portföy",
                new BigDecimal(unitPrice), new BigDecimal("1000000"), LocalDate.of(2026, 1, 1));
    }

    private static InflationDataPoint inflation(String country, String cpi, String yearly) {
        InflationDataPoint p = new InflationDataPoint(LocalDate.of(2026, 1, 1), country);
        p.setCpiIndex(new BigDecimal(cpi));
        p.setCpiYearlyChange(new BigDecimal(yearly));
        p.setCpiMonthlyChange(new BigDecimal("2.0"));
        return p;
    }

    // ── getAllInstruments() ─────────────────────────────────────────────

    @Test
    void all_lists_empty_returns_empty_list() {
        assertThat(service.getAllInstruments()).isEmpty();
    }

    @Test
    void market_summary_items_are_mapped_to_dtos() {
        when(marketService.summary()).thenReturn(List.of(
                summary("AAPL", "Apple", "STOCK", new BigDecimal("180"), new BigDecimal("1.5")),
                summary("BTCUSD", "Bitcoin", "CRYPTO", new BigDecimal("50000"), new BigDecimal("3.0"))));

        List<AnalysisInstrumentDto> out = service.getAllInstruments();
        assertThat(out).hasSize(2);
        AnalysisInstrumentDto apple = out.get(0);
        assertThat(apple.getSymbol()).isEqualTo("AAPL");
        assertThat(apple.getCategory()).isEqualTo("STOCK");
        assertThat(apple.getCurrency()).isEqualTo("USD");
        assertThat(apple.getRiskLevel()).isEqualTo("MEDIUM");
        AnalysisInstrumentDto btc = out.get(1);
        assertThat(btc.getCategory()).isEqualTo("CRYPTO");
        assertThat(btc.getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void tcmb_fx_rows_are_added_when_not_already_seen() {
        ExchangeRate r = new ExchangeRate("USD", "US Dollar",
                new BigDecimal("32.50"), new BigDecimal("32.70"),
                new BigDecimal("32.40"), new BigDecimal("32.80"),
                LocalDate.of(2026, 1, 5), "TCMB");
        when(exchangeRateService.getLatestRates()).thenReturn(List.of(r));

        List<AnalysisInstrumentDto> out = service.getAllInstruments();
        assertThat(out).extracting(AnalysisInstrumentDto::getSymbol).contains("USDTRY");
        AnalysisInstrumentDto usd = out.stream().filter(d -> "USDTRY".equals(d.getSymbol())).findFirst().orElseThrow();
        assertThat(usd.getCategory()).isEqualTo("FX");
        assertThat(usd.getCurrency()).isEqualTo("TRY");
        // mid of 32.50 / 32.70 = 32.60
        assertThat(usd.getValue()).isEqualByComparingTo("32.60");
    }

    @Test
    void tcmb_fx_skips_null_or_missing_codes() {
        ExchangeRate broken = new ExchangeRate(null, "?",
                new BigDecimal("1"), new BigDecimal("1"),
                new BigDecimal("1"), new BigDecimal("1"),
                LocalDate.of(2026, 1, 1), "TCMB");
        when(exchangeRateService.getLatestRates()).thenReturn(List.of(broken));
        assertThat(service.getAllInstruments()).isEmpty();
    }

    @Test
    void fund_with_null_unit_price_is_skipped() {
        InvestmentFund f = fund("AFA", "Ak Fon", "1");
        f.setUnitPrice(null);
        when(fundService.getAllFunds()).thenReturn(List.of(f));
        assertThat(service.getAllInstruments()).isEmpty();
    }

    @Test
    void funds_become_FUND_category_rows() {
        InvestmentFund f = fund("AFA", "Ak Fon", "12.34");
        f.setYearlyReturn(new BigDecimal("40"));
        f.setMonthlyReturn(new BigDecimal("3"));
        f.setWeeklyReturn(new BigDecimal("1"));
        f.setRiskLevel("3");
        when(fundService.getAllFunds()).thenReturn(List.of(f));

        List<AnalysisInstrumentDto> out = service.getAllInstruments();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getCategory()).isEqualTo("FUND");
        assertThat(out.get(0).getCurrency()).isEqualTo("TRY");
        // SPK risk level 3 falls into the MEDIUM bucket (<=5).
        assertThat(out.get(0).getRiskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void fund_risk_low_when_spk_level_is_one() {
        InvestmentFund f = fund("LOW", "Low Risk", "10");
        f.setRiskLevel("1");
        when(fundService.getAllFunds()).thenReturn(List.of(f));
        assertThat(service.getAllInstruments().get(0).getRiskLevel()).isEqualTo("LOW");
    }

    @Test
    void fund_risk_high_when_spk_level_is_seven() {
        InvestmentFund f = fund("HI", "High Risk", "10");
        f.setRiskLevel("7");
        when(fundService.getAllFunds()).thenReturn(List.of(f));
        assertThat(service.getAllInstruments().get(0).getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void fund_risk_falls_back_to_category_default_when_label_invalid() {
        InvestmentFund f = fund("X", "Y", "10");
        f.setRiskLevel("not-a-number");
        when(fundService.getAllFunds()).thenReturn(List.of(f));
        assertThat(service.getAllInstruments().get(0).getRiskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void inflation_rows_added_for_tr_and_us_when_present() {
        when(inflationService.getLatest("TR"))
                .thenReturn(Optional.of(inflation("TR", "150", "65")));
        when(inflationService.getLatest("US"))
                .thenReturn(Optional.of(inflation("US", "310", "3.5")));

        List<AnalysisInstrumentDto> out = service.getAllInstruments();
        assertThat(out).extracting(AnalysisInstrumentDto::getSymbol)
                .contains("TR-CPI", "US-CPI");
        AnalysisInstrumentDto tr = out.stream().filter(d -> "TR-CPI".equals(d.getSymbol())).findFirst().orElseThrow();
        assertThat(tr.getCategory()).isEqualTo("INFLATION_TR");
        assertThat(tr.getRiskLevel()).isEqualTo("LOW");
        // Inflation rows skip real-return adjustment.
        assertThat(tr.getRealChangeYearly()).isNull();
    }

    @Test
    void applies_real_return_to_TRY_rows_using_tr_cpi() {
        when(inflationService.getLatest("TR"))
                .thenReturn(Optional.of(inflation("TR", "150", "65")));
        // BIST stock row (TRY) with 100% nominal yearly.
        when(marketService.summary()).thenReturn(List.of(
                summary("THYAO", "Türk Hava Yolları", "BIST", new BigDecimal("250"),
                        new BigDecimal("0.5"))));
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.empty()); // no candle history

        List<AnalysisInstrumentDto> out = service.getAllInstruments();
        AnalysisInstrumentDto thyao = out.get(0);
        assertThat(thyao.getCurrency()).isEqualTo("TRY");
        // With null yearly (no candles), realChangeYearly stays null too.
        assertThat(thyao.getChangeYearly()).isNull();
        assertThat(thyao.getRealChangeYearly()).isNull();
    }

    @Test
    void exception_in_market_summary_is_swallowed() {
        when(marketService.summary()).thenThrow(new RuntimeException("upstream down"));
        // Fund still appears.
        when(fundService.getAllFunds())
                .thenReturn(List.of(fund("X", "Y", "10")));

        List<AnalysisInstrumentDto> out = service.getAllInstruments();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getCategory()).isEqualTo("FUND");
    }

    @Test
    void exception_in_fx_fetch_is_swallowed() {
        when(exchangeRateService.getLatestRates()).thenThrow(new RuntimeException("tcmb 500"));
        // Should not throw — returns whatever else aggregated.
        assertThat(service.getAllInstruments()).isEmpty();
    }

    @Test
    void exception_in_funds_fetch_is_swallowed() {
        when(fundService.getAllFunds()).thenThrow(new RuntimeException("tefas 500"));
        assertThat(service.getAllInstruments()).isEmpty();
    }

    @Test
    void index_symbol_with_xu_prefix_gets_TRY_currency() {
        when(marketService.summary()).thenReturn(List.of(
                summary("XU100", "BIST 100", "INDEX", new BigDecimal("10000"), BigDecimal.ZERO)));
        AnalysisInstrumentDto dto = service.getAllInstruments().get(0);
        assertThat(dto.getCurrency()).isEqualTo("TRY");
        assertThat(dto.getCategory()).isEqualTo("INDEX");
    }

    @Test
    void index_symbol_without_xu_prefix_gets_USD_currency() {
        when(marketService.summary()).thenReturn(List.of(
                summary("SPX", "S&P 500", "INDEX", new BigDecimal("5000"), BigDecimal.ZERO)));
        assertThat(service.getAllInstruments().get(0).getCurrency()).isEqualTo("USD");
    }

    // ── getDetail(symbol) ───────────────────────────────────────────────

    @Test
    void getDetail_null_symbol_returns_empty() {
        assertThat(service.getDetail(null)).isEmpty();
    }

    @Test
    void getDetail_blank_symbol_returns_empty() {
        assertThat(service.getDetail("   ")).isEmpty();
    }

    @Test
    void getDetail_unknown_symbol_returns_empty() {
        when(instrumentRepo.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());
        when(fundService.getFundByCode("UNKNOWN")).thenReturn(Optional.empty());
        assertThat(service.getDetail("unknown")).isEmpty();
    }

    @Test
    void getDetail_for_market_instrument_returns_present() {
        MarketInstrument inst = new MarketInstrument("AAPL", "Apple",
                com.finansportali.backend.entity.InstrumentType.STOCK,
                com.finansportali.backend.entity.MarketDataProvider.YAHOO,
                "AAPL", false);
        when(instrumentRepo.findBySymbol("AAPL")).thenReturn(Optional.of(inst));
        when(marketService.summary()).thenReturn(List.of(
                summary("AAPL", "Apple", "STOCK", new BigDecimal("180"), new BigDecimal("1.5"))));
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(any(), any(), any()))
                .thenReturn(List.of());

        Optional<AnalysisDetailDto> opt = service.getDetail("aapl");
        assertThat(opt).isPresent();
        AnalysisDetailDto d = opt.get();
        assertThat(d.getSummary()).isNotNull();
        assertThat(d.getSummary().getSymbol()).isEqualTo("AAPL");
        assertThat(d.getTrend()).isNotNull();
        assertThat(d.getVolatility()).isNotNull();
        assertThat(d.getRiskNote()).isNotBlank();
        assertThat(d.getSeries()).isNotNull();
    }

    @Test
    void getDetail_for_fund_returns_present_without_series() {
        when(instrumentRepo.findBySymbol("AFA")).thenReturn(Optional.empty());
        InvestmentFund f = fund("AFA", "Ak Fon", "12.34");
        f.setYearlyReturn(new BigDecimal("40"));
        when(fundService.getFundByCode("AFA")).thenReturn(Optional.of(f));

        Optional<AnalysisDetailDto> opt = service.getDetail("AFA");
        assertThat(opt).isPresent();
        assertThat(opt.get().getSummary().getCategory()).isEqualTo("FUND");
        assertThat(opt.get().getSeries()).isEmpty();
    }

    @Test
    void latestUpdate_returns_a_non_null_instant() {
        assertThat(service.latestUpdate()).isNotNull();
    }

    // ── buildShortNote(...) private helper ───────────────────────────────

    private AnalysisInstrumentDto dtoWith(BigDecimal weekly, BigDecimal yearly, String risk, String currency) {
        AnalysisInstrumentDto d = new AnalysisInstrumentDto();
        d.setChangeWeekly(weekly);
        d.setChangeYearly(yearly);
        d.setRiskLevel(risk);
        d.setCurrency(currency);
        return d;
    }

    @Test
    void buildShortNote_positive_branch_for_weekly_above_3() {
        AnalysisInstrumentDto d = dtoWith(new BigDecimal("4.5"), null, null, null);
        String note = ReflectionTestUtils.invokeMethod(service, "buildShortNote", d);
        assertThat(note).contains("momentum pozitif");
    }

    @Test
    void buildShortNote_negative_branch_for_weekly_below_minus_3() {
        AnalysisInstrumentDto d = dtoWith(new BigDecimal("-5"), null, null, null);
        String note = ReflectionTestUtils.invokeMethod(service, "buildShortNote", d);
        assertThat(note).contains("baskı sürüyor");
    }

    @Test
    void buildShortNote_flat_branch_between_thresholds() {
        AnalysisInstrumentDto d = dtoWith(new BigDecimal("1"), null, null, null);
        String note = ReflectionTestUtils.invokeMethod(service, "buildShortNote", d);
        assertThat(note).contains("yatay seyir");
    }

    @Test
    void buildShortNote_handles_null_dto_and_null_weekly() {
        String nullDto = ReflectionTestUtils.invokeMethod(service, "buildShortNote", (Object) null);
        assertThat(nullDto).contains("Kısa vadeli veri yetersiz");
        AnalysisInstrumentDto d = dtoWith(null, null, null, null);
        String nullWeekly = ReflectionTestUtils.invokeMethod(service, "buildShortNote", d);
        assertThat(nullWeekly).contains("Haftalık değişim verisi");
    }

    // ── buildLongNote(...) private helper ────────────────────────────────

    @Test
    void buildLongNote_positive_branch_for_yearly_above_30() {
        AnalysisInstrumentDto d = dtoWith(null, new BigDecimal("50"), null, null);
        String note = ReflectionTestUtils.invokeMethod(service, "buildLongNote", d);
        assertThat(note).contains("güçlü pozitif");
    }

    @Test
    void buildLongNote_negative_branch_for_yearly_below_minus_30() {
        AnalysisInstrumentDto d = dtoWith(null, new BigDecimal("-40"), null, null);
        String note = ReflectionTestUtils.invokeMethod(service, "buildLongNote", d);
        assertThat(note).contains("ciddi düşüş");
    }

    @Test
    void buildLongNote_flat_branch_between_thresholds() {
        AnalysisInstrumentDto d = dtoWith(null, new BigDecimal("10"), null, null);
        String note = ReflectionTestUtils.invokeMethod(service, "buildLongNote", d);
        assertThat(note).contains("ılımlı seyir");
    }

    @Test
    void buildLongNote_handles_null_dto_and_null_yearly() {
        String nullDto = ReflectionTestUtils.invokeMethod(service, "buildLongNote", (Object) null);
        assertThat(nullDto).contains("Uzun vadeli veri yetersiz");
        AnalysisInstrumentDto d = dtoWith(null, null, null, null);
        String nullYearly = ReflectionTestUtils.invokeMethod(service, "buildLongNote", d);
        assertThat(nullYearly).contains("Yıllık değişim verisi");
    }

    // ── buildRiskNote(...) private helper ────────────────────────────────

    @Test
    void buildRiskNote_returns_phrase_per_risk_label() {
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "buildRiskNote",
                dtoWith(null, null, "LOW", null))).asString().contains("Düşük risk");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "buildRiskNote",
                dtoWith(null, null, "MEDIUM", null))).asString().contains("Orta seviye");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "buildRiskNote",
                dtoWith(null, null, "HIGH", null))).asString().contains("Yüksek volatilite");
        // Default branch: an unknown label.
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "buildRiskNote",
                dtoWith(null, null, "UNKNOWN", null))).asString().contains("Belirsiz");
    }

    @Test
    void buildRiskNote_returns_data_message_when_dto_null() {
        String note = ReflectionTestUtils.invokeMethod(service, "buildRiskNote", (Object) null);
        assertThat(note).contains("Risk değerlendirmesi");
    }

    // ── applyRealReturn(...) private helper ──────────────────────────────

    @Test
    void applyRealReturn_sets_real_return_for_TRY_row_with_tr_cpi() {
        AnalysisInstrumentDto d = dtoWith(null, new BigDecimal("100"), "MEDIUM", "TRY");
        ReflectionTestUtils.invokeMethod(service, "applyRealReturn", d,
                new BigDecimal("50"), new BigDecimal("3"));
        // r_real = (1 + 1.0) / (1 + 0.5) - 1 = 2/1.5 - 1 ≈ 33.33%
        assertThat(d.getRealChangeYearly()).isNotNull();
        assertThat(d.getRealChangeYearly().doubleValue()).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.05));
        assertThat(d.getBeatsInflation()).isTrue();
    }

    @Test
    void applyRealReturn_is_noop_when_cpi_null() {
        AnalysisInstrumentDto d = dtoWith(null, new BigDecimal("100"), "MEDIUM", "TRY");
        ReflectionTestUtils.invokeMethod(service, "applyRealReturn", d,
                (BigDecimal) null, (BigDecimal) null);
        assertThat(d.getRealChangeYearly()).isNull();
        assertThat(d.getBeatsInflation()).isNull();
    }

    @Test
    void applyRealReturn_skips_inflation_rows() {
        AnalysisInstrumentDto d = dtoWith(null, new BigDecimal("65"), "LOW", null);
        d.setCategory("INFLATION_TR");
        ReflectionTestUtils.invokeMethod(service, "applyRealReturn", d,
                new BigDecimal("65"), new BigDecimal("3"));
        // Inflation rows are skipped — real return stays unset.
        assertThat(d.getRealChangeYearly()).isNull();
    }

    @Test
    void applyRealReturn_handles_negative_real_return() {
        // 10% nominal, 50% CPI → real return is deeply negative.
        AnalysisInstrumentDto d = dtoWith(null, new BigDecimal("10"), "MEDIUM", "TRY");
        ReflectionTestUtils.invokeMethod(service, "applyRealReturn", d,
                new BigDecimal("50"), new BigDecimal("3"));
        assertThat(d.getRealChangeYearly().signum()).isNegative();
        assertThat(d.getBeatsInflation()).isFalse();
    }

    // ── changeFromCandles(...) private helper ────────────────────────────
    // Operates on an already-loaded ascending candle list (no DB query).

    private MarketInstrument testInstrument() {
        return new MarketInstrument("AAPL", "Apple",
                com.finansportali.backend.entity.InstrumentType.STOCK,
                com.finansportali.backend.entity.MarketDataProvider.YAHOO,
                "AAPL", false);
    }

    @Test
    void changeFromCandles_returns_null_when_no_candle_history() {
        BigDecimal result = ReflectionTestUtils.invokeMethod(service, "changeFromCandles",
                List.of(), new BigDecimal("150"), 7);
        assertThat(result).isNull();
    }

    @Test
    void changeFromCandles_computes_positive_pct_when_history_present() {
        MarketCandle c = new MarketCandle(testInstrument(), LocalDate.now().minusDays(7), new BigDecimal("100"));
        BigDecimal result = ReflectionTestUtils.invokeMethod(service, "changeFromCandles",
                List.of(c), new BigDecimal("110"), 7);
        // 110 vs 100 base → +10%.
        assertThat(result).isEqualByComparingTo("10.00");
    }

    @Test
    void changeFromCandles_returns_null_when_base_close_is_zero() {
        MarketCandle c = new MarketCandle(testInstrument(), LocalDate.now().minusDays(7), BigDecimal.ZERO);
        BigDecimal result = ReflectionTestUtils.invokeMethod(service, "changeFromCandles",
                List.of(c), new BigDecimal("110"), 7);
        assertThat(result).isNull();
    }
}
