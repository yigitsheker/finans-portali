package com.finansportali.backend.service.market;

import com.finansportali.backend.dto.response.market.MarketHistoryPoint;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketCandle;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.service.client.market.YahooPriceFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketHistoryServiceTest {

    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private MarketCandleRepository candleRepo;
    @Mock private YahooPriceFetcher yahooPriceFetcher;
    @Mock private MarketInstrumentService instrumentService;
    @InjectMocks private MarketHistoryService service;

    private static MarketInstrument inst(String symbol, InstrumentType type) {
        MarketInstrument i = new MarketInstrument();
        i.setSymbol(symbol);
        i.setInstrumentType(type);
        return i;
    }

    private static YahooPriceFetcher.DayClose day(LocalDate d, String close, String label, long ts) {
        return new YahooPriceFetcher.DayClose(d, new BigDecimal(close), ts, label);
    }

    @Test
    void getHistory_throws_when_symbol_unknown() {
        when(instrumentRepo.findBySymbol("XX")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHistory("XX", "1Y"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getHistory_uses_database_for_FUND_type() {
        MarketInstrument i = inst("AFA", InstrumentType.FUND);
        when(instrumentRepo.findBySymbol("AFA")).thenReturn(Optional.of(i));
        MarketCandle c = new MarketCandle(i, LocalDate.now(), new BigDecimal("1.5"));
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(eq(i), any(), any()))
                .thenReturn(List.of(c));

        List<MarketHistoryPoint> hist = service.getHistory("AFA", "1Y");
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).close()).isEqualByComparingTo("1.5");
    }

    @Test
    void getHistory_uses_database_for_BOND_type() {
        MarketInstrument i = inst("TR2YT", InstrumentType.BOND);
        when(instrumentRepo.findBySymbol("TR2YT")).thenReturn(Optional.of(i));
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(eq(i), any(), any()))
                .thenReturn(List.of());

        assertThat(service.getHistory("TR2YT", "5D")).isEmpty();
    }

    @Test
    void getHistory_uses_database_for_VIOP_type() {
        MarketInstrument i = inst("F_AKBNK0526", InstrumentType.VIOP);
        when(instrumentRepo.findBySymbol("F_AKBNK0526")).thenReturn(Optional.of(i));
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(eq(i), any(), any()))
                .thenReturn(List.of());

        assertThat(service.getHistory("F_AKBNK0526", "30D")).isEmpty();
    }

    @Test
    void getHistory_uses_yahoo_for_stock_and_filters_nulls_and_zero() {
        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(instrumentService.normalizeSymbolForYahoo("THYAO", InstrumentType.STOCK))
                .thenReturn("THYAO.IS");

        YahooPriceFetcher.DayClose valid = day(LocalDate.of(2026, 5, 1), "100", "May 1", 1714521600);
        YahooPriceFetcher.DayClose negative = day(LocalDate.of(2026, 5, 2), "-1", "May 2", 1714608000);
        when(yahooPriceFetcher.fetchHistory(eq("THYAO.IS"), anyString(), anyString()))
                .thenReturn(java.util.Arrays.asList(valid, null, negative));

        List<MarketHistoryPoint> hist = service.getHistory("THYAO", "1Y");
        assertThat(hist).hasSize(1);   // only the valid day
        assertThat(hist.get(0).close()).isEqualByComparingTo("100");
    }

    @Test
    void getHistory_keeps_outliers_but_logs_them() {
        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(instrumentService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("THYAO.IS");

        YahooPriceFetcher.DayClose a = day(LocalDate.of(2026, 5, 1), "100", "May 1", 1);
        YahooPriceFetcher.DayClose spike = day(LocalDate.of(2026, 5, 2), "200", "May 2", 2);
        when(yahooPriceFetcher.fetchHistory(anyString(), anyString(), anyString()))
                .thenReturn(List.of(a, spike));

        List<MarketHistoryPoint> hist = service.getHistory("THYAO", "1Y");
        assertThat(hist).hasSize(2);   // outliers logged, still kept
    }

    @Test
    void getHistory_falls_back_to_database_when_yahoo_returns_empty() {
        // Yahoo's chart endpoint 404s for tickers it doesn't carry (e.g.
        // KOZAA.IS, SODA.IS) — the fetcher swallows that into an empty list
        // rather than throwing. Without a fallback the sparkline would just
        // be missing on the user's list page. Our cached MarketCandles fill
        // the gap.
        MarketInstrument i = inst("KOZAA", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("KOZAA")).thenReturn(Optional.of(i));
        when(instrumentService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("KOZAA.IS");
        when(yahooPriceFetcher.fetchHistory(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        MarketCandle c = new MarketCandle(i, LocalDate.now(), new BigDecimal("89.45"));
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(eq(i), any(), any()))
                .thenReturn(List.of(c));

        var result = service.getHistory("KOZAA", "30D");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualByComparingTo("89.45");
    }

    @Test
    void getHistory_returns_empty_when_yahoo_AND_db_are_both_empty() {
        // Truly-unknown ticker: nothing on Yahoo, nothing cached locally.
        MarketInstrument i = inst("XXXXX", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("XXXXX")).thenReturn(Optional.of(i));
        when(instrumentService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("XXXXX.IS");
        when(yahooPriceFetcher.fetchHistory(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(eq(i), any(), any()))
                .thenReturn(List.of());

        assertThat(service.getHistory("XXXXX", "30D")).isEmpty();
    }

    @Test
    void getHistory_falls_back_to_database_on_yahoo_exception() {
        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(instrumentService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("THYAO.IS");
        when(yahooPriceFetcher.fetchHistory(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("yahoo down"));
        MarketCandle c = new MarketCandle(i, LocalDate.now(), new BigDecimal("99"));
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(eq(i), any(), any()))
                .thenReturn(List.of(c));

        List<MarketHistoryPoint> hist = service.getHistory("THYAO", "1Y");
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).close()).isEqualByComparingTo("99");
    }

    @Test
    void getHistory_handles_null_period_with_default_30D() {
        MarketInstrument i = inst("THYAO", InstrumentType.FUND);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(eq(i), any(), any()))
                .thenReturn(List.of());
        assertThat(service.getHistory("THYAO", null)).isEmpty();
    }

    @Test
    void getHistory_handles_5D_and_1D_periods() {
        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(instrumentService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("THYAO.IS");
        when(yahooPriceFetcher.fetchHistory(anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        service.getHistory("THYAO", "1D");
        service.getHistory("THYAO", "5D");
        service.getHistory("THYAO", "1Y");
        // Each call uses different range/interval — exercising mapPeriodToYahooRange branches.
    }
}
