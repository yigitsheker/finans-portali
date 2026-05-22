package com.finansportali.backend.service;

import com.finansportali.backend.entity.HistoricalPrice;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.HistoricalPriceRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalPriceServiceTest {

    @Mock private HistoricalPriceRepository historicalPriceRepo;
    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private YahooPriceFetcher yahooPriceFetcher;
    @Mock private MarketService marketService;
    @InjectMocks private HistoricalPriceService service;

    private static MarketInstrument inst(String symbol, InstrumentType type) {
        MarketInstrument i = new MarketInstrument();
        i.setSymbol(symbol);
        i.setInstrumentType(type);
        return i;
    }

    private static HistoricalPrice hp(String symbol, LocalDate date, String close) {
        HistoricalPrice h = new HistoricalPrice();
        h.setSymbol(symbol);
        h.setPriceDate(date);
        h.setClosePrice(new BigDecimal(close));
        h.setAdjustedClosePrice(new BigDecimal(close));
        return h;
    }

    // ---------- getHistoricalPrices ----------

    @Test
    void getHistoricalPrices_uses_cache_when_sufficient() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        // 8 expected days; 5 cached → 62.5% → > 50% threshold → don't fetch
        List<HistoricalPrice> cached = List.of(
                hp("THYAO", from, "100"),
                hp("THYAO", from.plusDays(1), "101"),
                hp("THYAO", from.plusDays(2), "102"),
                hp("THYAO", from.plusDays(3), "103"),
                hp("THYAO", from.plusDays(4), "104")
        );
        when(historicalPriceRepo.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc("THYAO", from, to))
                .thenReturn(cached);

        List<HistoricalPrice> result = service.getHistoricalPrices("THYAO", from, to);
        assertThat(result).hasSize(5);
        verify(yahooPriceFetcher, never()).fetchHistory(anyString(), anyString(), anyString());
    }

    @Test
    void getHistoricalPrices_fetches_when_cache_under_threshold() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        // 8 expected, 1 cached → 12.5% → fetch
        when(historicalPriceRepo.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc("THYAO", from, to))
                .thenReturn(List.of(hp("THYAO", from, "100")));

        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(marketService.normalizeSymbolForYahoo("THYAO", InstrumentType.STOCK))
                .thenReturn("THYAO.IS");

        YahooPriceFetcher.DayClose d1 = new YahooPriceFetcher.DayClose(from.plusDays(1), new BigDecimal("101"));
        when(yahooPriceFetcher.fetchHistory(eq("THYAO.IS"), anyString(), eq("1d")))
                .thenReturn(List.of(d1));
        when(historicalPriceRepo.existsBySymbolAndPriceDate(anyString(), any())).thenReturn(false);

        service.getHistoricalPrices("THYAO", from, to);
        verify(yahooPriceFetcher).fetchHistory(eq("THYAO.IS"), anyString(), eq("1d"));
        verify(historicalPriceRepo, times(1)).save(any(HistoricalPrice.class));
    }

    // ---------- getClosePriceOnOrBefore ----------

    @Test
    void getClosePriceOnOrBefore_returns_adjusted_close_when_present() {
        HistoricalPrice h = hp("THYAO", LocalDate.now(), "100");
        h.setAdjustedClosePrice(new BigDecimal("99.5"));
        when(historicalPriceRepo.findLatestPriceOnOrBefore("THYAO", LocalDate.now()))
                .thenReturn(Optional.of(h));
        assertThat(service.getClosePriceOnOrBefore("THYAO", LocalDate.now()))
                .contains(new BigDecimal("99.5"));
    }

    @Test
    void getClosePriceOnOrBefore_falls_back_to_close_when_adjusted_null() {
        HistoricalPrice h = hp("THYAO", LocalDate.now(), "100");
        h.setAdjustedClosePrice(null);
        when(historicalPriceRepo.findLatestPriceOnOrBefore("THYAO", LocalDate.now()))
                .thenReturn(Optional.of(h));
        assertThat(service.getClosePriceOnOrBefore("THYAO", LocalDate.now()))
                .contains(new BigDecimal("100"));
    }

    @Test
    void getClosePriceOnOrBefore_returns_empty_when_no_history() {
        when(historicalPriceRepo.findLatestPriceOnOrBefore(anyString(), any()))
                .thenReturn(Optional.empty());
        assertThat(service.getClosePriceOnOrBefore("THYAO", LocalDate.now())).isEmpty();
    }

    // ---------- fetchAndCacheFromYahoo ----------

    @Test
    void fetchAndCacheFromYahoo_logs_and_returns_when_yahoo_empty() {
        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(marketService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("THYAO.IS");
        when(yahooPriceFetcher.fetchHistory(anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        service.fetchAndCacheFromYahoo("THYAO", LocalDate.now().minusDays(7), LocalDate.now());
        verify(historicalPriceRepo, never()).save(any());
    }

    @Test
    void fetchAndCacheFromYahoo_swallows_exception_when_symbol_unknown() {
        when(instrumentRepo.findBySymbol("XXX")).thenReturn(Optional.empty());
        // Method-level try/catch swallows; assert it doesn't bubble up.
        service.fetchAndCacheFromYahoo("XXX", LocalDate.now().minusDays(7), LocalDate.now());
    }

    @Test
    void fetchAndCacheFromYahoo_skips_existing_rows() {
        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(marketService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("THYAO.IS");

        LocalDate d = LocalDate.now().minusDays(5);
        YahooPriceFetcher.DayClose row = new YahooPriceFetcher.DayClose(d, new BigDecimal("100"));
        when(yahooPriceFetcher.fetchHistory(anyString(), anyString(), anyString()))
                .thenReturn(List.of(row, row));
        when(historicalPriceRepo.existsBySymbolAndPriceDate("THYAO", d)).thenReturn(true);

        service.fetchAndCacheFromYahoo("THYAO", LocalDate.now().minusDays(7), LocalDate.now());
        verify(historicalPriceRepo, never()).save(any());
    }

    @Test
    void fetchAndCacheFromYahoo_selects_5y_range_for_very_old_from_date() {
        MarketInstrument i = inst("THYAO", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(marketService.normalizeSymbolForYahoo(anyString(), any())).thenReturn("THYAO.IS");
        when(yahooPriceFetcher.fetchHistory(anyString(), eq("5y"), eq("1d")))
                .thenReturn(List.of());

        service.fetchAndCacheFromYahoo("THYAO",
                LocalDate.now().minusYears(3), LocalDate.now());

        verify(yahooPriceFetcher).fetchHistory(eq("THYAO.IS"), eq("5y"), eq("1d"));
    }
}
