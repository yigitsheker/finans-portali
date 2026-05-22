package com.finansportali.backend.service;

import com.finansportali.backend.dto.response.market.MarketHistoryPoint;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.service.market.MarketDataSeedService;
import com.finansportali.backend.service.market.MarketHistoryService;
import com.finansportali.backend.service.market.MarketInstrumentService;
import com.finansportali.backend.service.market.MarketPriceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MarketService is a thin facade. Each test verifies that the right
 * sub-service receives the call and that {@code seedIfEmpty()} fires
 * for the methods that document it.
 */
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock private MarketInstrumentService instrumentService;
    @Mock private MarketPriceService priceService;
    @Mock private MarketHistoryService historyService;
    @Mock private MarketDataSeedService seedService;
    @InjectMocks private MarketService service;

    @Test
    void getCurrentPrice_delegates_to_priceService() {
        when(priceService.getCurrentPrice("THYAO")).thenReturn(new BigDecimal("300"));
        assertThat(service.getCurrentPrice("THYAO")).isEqualByComparingTo("300");
    }

    @Test
    void getInstrumentBySymbol_delegates_to_instrumentService() {
        MarketInstrument i = new MarketInstrument();
        when(instrumentService.getInstrumentBySymbol("THYAO")).thenReturn(Optional.of(i));
        assertThat(service.getInstrumentBySymbol("THYAO")).contains(i);
    }

    @Test
    void getAllInstruments_delegates_to_priceService() {
        List<MarketSummaryItem> rows = List.of();
        when(priceService.getAllInstrumentsWithPrices()).thenReturn(rows);
        assertThat(service.getAllInstruments()).isEqualTo(rows);
    }

    @Test
    void normalizeSymbolForYahoo_delegates_to_instrumentService() {
        when(instrumentService.normalizeSymbolForYahoo("THYAO", InstrumentType.STOCK))
                .thenReturn("THYAO.IS");
        assertThat(service.normalizeSymbolForYahoo("THYAO", InstrumentType.STOCK))
                .isEqualTo("THYAO.IS");
    }

    @Test
    void seedIfEmpty_delegates_to_seedService() {
        service.seedIfEmpty();
        verify(seedService).seedIfEmpty();
    }

    @Test
    void instruments_seeds_then_delegates() {
        List<MarketInstrument> rows = List.of();
        when(instrumentService.getAllInstruments()).thenReturn(rows);
        assertThat(service.instruments()).isEqualTo(rows);
        verify(seedService).seedIfEmpty();
    }

    @Test
    void searchInstruments_seeds_then_delegates() {
        List<MarketInstrument> rows = List.of();
        when(instrumentService.searchInstruments("XX")).thenReturn(rows);
        assertThat(service.searchInstruments("XX")).isEqualTo(rows);
        verify(seedService).seedIfEmpty();
    }

    @Test
    void latestPrice_seeds_then_delegates() {
        Map<String, Object> data = Map.of("last", "100");
        when(priceService.getLatestPrice("THYAO")).thenReturn(data);
        assertThat(service.latestPrice("THYAO")).isEqualTo(data);
        verify(seedService).seedIfEmpty();
    }

    @Test
    void summary_seeds_then_delegates() {
        List<MarketSummaryItem> rows = List.of();
        when(priceService.getMarketSummary()).thenReturn(rows);
        assertThat(service.summary()).isEqualTo(rows);
        verify(seedService).seedIfEmpty();
    }

    @Test
    void history_seeds_then_delegates() {
        List<MarketHistoryPoint> rows = List.of();
        when(historyService.getHistory("THYAO", "1M")).thenReturn(rows);
        assertThat(service.history("THYAO", "1M")).isEqualTo(rows);
        verify(seedService).seedIfEmpty();
    }
}
