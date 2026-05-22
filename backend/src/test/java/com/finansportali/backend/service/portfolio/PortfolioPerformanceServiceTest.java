package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.dto.response.portfolio.PortfolioPerformanceResponse;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PortfolioPositionRepository;
import com.finansportali.backend.service.HistoricalPriceService;
import com.finansportali.backend.service.MarketService;
import com.finansportali.backend.service.client.market.YahooPriceFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioPerformanceServiceTest {

    @Mock private PortfolioPositionRepository positionRepo;
    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private MarketQuoteRepository quoteRepo;
    @Mock private MarketService marketService;
    @Mock private HistoricalPriceService historicalPriceService;
    @Mock private YahooPriceFetcher yahooPriceFetcher;
    @Mock private PortfolioCurrencyService currencyService;
    @InjectMocks private PortfolioPerformanceService service;

    private static MarketInstrument inst(String symbol, InstrumentType type) {
        MarketInstrument i = new MarketInstrument();
        i.setSymbol(symbol);
        i.setInstrumentType(type);
        return i;
    }

    private static PortfolioPosition pos(String symbol, String qty, String avg, LocalDate buyDate) {
        PortfolioPosition p = new PortfolioPosition("u", symbol,
                new BigDecimal(qty), avg == null ? null : new BigDecimal(avg));
        p.setPurchaseDate(buyDate);
        return p;
    }

    private static MarketQuote quote(BigDecimal last) {
        MarketQuote q = new MarketQuote();
        q.setLast(last);
        return q;
    }

    @Test
    void empty_portfolio_returns_empty_response() {
        when(positionRepo.findByUserId("u")).thenReturn(List.of());

        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "1M");
        assertThat(r.points()).isEmpty();
        assertThat(r.range()).isEqualTo("1M");
        assertThat(r.source()).isEqualTo("EMPTY");
    }

    @Test
    void positions_bought_today_use_intraday_fallback() {
        PortfolioPosition p = pos("THYAO", "10", "100", LocalDate.now());
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(currencyService.getUsdTryRate()).thenReturn(BigDecimal.ONE);
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("TRY");

        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(marketService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("THYAO.IS");
        // No intraday data → fall through to buy/current.
        when(yahooPriceFetcher.fetchIntraday(anyString(), anyString())).thenReturn(List.of());
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i))
                .thenReturn(Optional.of(quote(new BigDecimal("110"))));

        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "1M");
        // Range is preserved (1M, not 1D — buy/current uses range argument as-is).
        assertThat(r.source()).isEqualTo("BUY_CURRENT_FALLBACK");
        assertThat(r.points()).hasSize(2);
    }

    @Test
    void range_1D_uses_intraday_path_and_falls_back_when_no_data() {
        PortfolioPosition p = pos("THYAO", "10", "100", LocalDate.now().minusDays(5));
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(currencyService.getUsdTryRate()).thenReturn(BigDecimal.ONE);
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("TRY");

        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(marketService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("THYAO.IS");
        when(yahooPriceFetcher.fetchIntraday(anyString(), anyString())).thenReturn(List.of());
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i)).thenReturn(Optional.empty());

        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "1D");
        assertThat(r.range()).isEqualTo("1D");
        assertThat(r.granularity()).isEqualTo("INTRADAY");
        assertThat(r.source()).isEqualTo("BUY_CURRENT_FALLBACK");
    }

    @Test
    void historical_range_falls_back_when_no_close_data() {
        PortfolioPosition p = pos("THYAO", "10", "100", LocalDate.now().minusMonths(2));
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(currencyService.getUsdTryRate()).thenReturn(BigDecimal.ONE);
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("TRY");

        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(historicalPriceService.getHistoricalPrices(anyString(), any(), any()))
                .thenReturn(List.of());
        when(historicalPriceService.getClosePriceOnOrBefore(anyString(), any()))
                .thenReturn(Optional.empty());          // no historical → fallback path
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i))
                .thenReturn(Optional.of(quote(new BigDecimal("120"))));

        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "1M");
        assertThat(r.source()).isEqualTo("BUY_CURRENT_FALLBACK");
    }

    @Test
    void historical_range_uses_yahoo_daily_when_data_is_present() {
        PortfolioPosition p = pos("THYAO", "10", "100", LocalDate.now().minusMonths(2));
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(currencyService.getUsdTryRate()).thenReturn(BigDecimal.ONE);
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("TRY");

        when(instrumentRepo.findBySymbol("THYAO"))
                .thenReturn(Optional.of(inst("THYAO", InstrumentType.STOCK)));
        when(historicalPriceService.getClosePriceOnOrBefore(anyString(), any()))
                .thenReturn(Optional.of(new BigDecimal("110")));

        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "1M");
        assertThat(r.source()).isEqualTo("YAHOO_DAILY");
        assertThat(r.granularity()).isEqualTo("DAILY");
        assertThat(r.points()).isNotEmpty();
        // Every value = 10 × 110 = 1100, multiplier = 1
        assertThat(r.points().get(0).value()).isEqualByComparingTo("1100");
    }

    @Test
    void usd_position_is_multiplied_by_usd_try_rate() {
        PortfolioPosition p = pos("AAPL", "5", "200", LocalDate.now().minusMonths(2));
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(currencyService.getUsdTryRate()).thenReturn(new BigDecimal("30"));
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("USD");
        when(instrumentRepo.findBySymbol("AAPL"))
                .thenReturn(Optional.of(inst("AAPL", InstrumentType.STOCK)));
        when(historicalPriceService.getClosePriceOnOrBefore(anyString(), any()))
                .thenReturn(Optional.of(new BigDecimal("210")));

        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "1M");
        // 5 * 210 * 30 = 31_500
        assertThat(r.points().get(0).value()).isEqualByComparingTo("31500");
    }

    @Test
    void range_ALL_uses_earliest_buy_date_as_start() {
        LocalDate buyDate = LocalDate.now().minusYears(2);
        PortfolioPosition p = pos("THYAO", "10", "100", buyDate);
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(currencyService.getUsdTryRate()).thenReturn(BigDecimal.ONE);
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("TRY");
        when(instrumentRepo.findBySymbol("THYAO"))
                .thenReturn(Optional.of(inst("THYAO", InstrumentType.STOCK)));
        when(historicalPriceService.getClosePriceOnOrBefore(anyString(), any()))
                .thenReturn(Optional.of(new BigDecimal("110")));

        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "ALL");
        assertThat(r.startDate()).isEqualTo(buyDate);
    }

    @Test
    void unknown_range_defaults_to_one_month() {
        PortfolioPosition p = pos("THYAO", "10", "100", LocalDate.now().minusMonths(3));
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(currencyService.getUsdTryRate()).thenReturn(BigDecimal.ONE);
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("TRY");
        when(instrumentRepo.findBySymbol("THYAO"))
                .thenReturn(Optional.of(inst("THYAO", InstrumentType.STOCK)));
        when(historicalPriceService.getClosePriceOnOrBefore(anyString(), any()))
                .thenReturn(Optional.of(new BigDecimal("110")));

        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "UNKNOWN");
        // unknown → 1M default; range string preserved as-is
        assertThat(r.range()).isEqualTo("UNKNOWN");
    }

    @Test
    void historical_lookup_failure_is_swallowed() {
        PortfolioPosition p = pos("THYAO", "10", "100", LocalDate.now().minusMonths(2));
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(currencyService.getUsdTryRate()).thenReturn(BigDecimal.ONE);
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("TRY");
        when(instrumentRepo.findBySymbol("THYAO"))
                .thenReturn(Optional.of(inst("THYAO", InstrumentType.STOCK)));

        when(historicalPriceService.getHistoricalPrices(anyString(), any(), any()))
                .thenThrow(new RuntimeException("network"));
        when(historicalPriceService.getClosePriceOnOrBefore(anyString(), any()))
                .thenReturn(Optional.of(new BigDecimal("110")));

        // Should not propagate the exception.
        PortfolioPerformanceResponse r = service.calculatePortfolioPerformance("u", "1M");
        assertThat(r).isNotNull();
    }
}
