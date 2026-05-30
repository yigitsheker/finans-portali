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
        TechnicalAnalysisService ta = new TechnicalAnalysisService();
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
        InvestmentFund f = new InvestmentFund(code, name, "Hisse", "Garanti Portföy",
                new BigDecimal(unitPrice), new BigDecimal("1000000"), LocalDate.of(2026, 1, 1));
        return f;
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
}
