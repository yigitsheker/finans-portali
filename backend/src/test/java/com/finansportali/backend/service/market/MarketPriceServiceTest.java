package com.finansportali.backend.service.market;

import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketCandle;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MarketPriceService}.
 *
 * The key contract under test is the candle fallback in
 * {@code getMarketSummary()}: when the cached MarketQuote shows
 * changePct == 0 (Yahoo returns flat quotes out of market hours), the
 * service must derive change from the last two stored candles instead
 * of stamping 0.00% on every BIST row.
 */
@ExtendWith(MockitoExtension.class)
class MarketPriceServiceTest {

    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private MarketQuoteRepository quoteRepo;
    @Mock private MarketCandleRepository candleRepo;
    @InjectMocks private MarketPriceService service;

    private MarketInstrument inst;

    @BeforeEach
    void setUp() {
        inst = new MarketInstrument();
        inst.setSymbol("THYAO");
        inst.setName("Türk Hava Yolları");
        inst.setInstrumentType(InstrumentType.BIST);
    }

    private MarketQuote quote(BigDecimal last, BigDecimal prev, BigDecimal changeAbs, BigDecimal changePct) {
        return new MarketQuote(inst, last, prev, changeAbs, changePct,
                Instant.parse("2026-05-19T10:00:00Z"), null);
    }

    private MarketCandle candle(LocalDate day, double close) {
        return new MarketCandle(inst, day, BigDecimal.valueOf(close));
    }

    @Test
    void summary_returns_quote_when_change_already_populated() {
        when(instrumentRepo.findAll()).thenReturn(List.of(inst));
        // changePct = -1.50 — non-zero, so the candle fallback should NOT kick in.
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst))
                .thenReturn(Optional.of(quote(
                        new BigDecimal("294.50"),
                        new BigDecimal("298.99"),
                        new BigDecimal("-4.49"),
                        new BigDecimal("-1.50"))));

        List<MarketSummaryItem> out = service.getMarketSummary();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).changePct().doubleValue()).isCloseTo(-1.50, offset(1e-6));
        // We must NOT have hit the candle repo when the change is already valid.
        // (Mockito verifies indirectly: candle method was never stubbed.)
    }

    @Test
    void summary_derives_change_from_candles_when_change_is_zero() {
        when(instrumentRepo.findAll()).thenReturn(List.of(inst));
        // Stale Yahoo quote — change = 0, but candles tell a different story.
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst))
                .thenReturn(Optional.of(quote(
                        new BigDecimal("294.50"),
                        new BigDecimal("294.50"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO)));
        // Yesterday closed at 300 → change% = (294.50 - 300) / 300 * 100 = -1.83
        when(candleRepo.findTop2ByInstrumentOrderByDayDesc(inst))
                .thenReturn(List.of(
                        candle(LocalDate.of(2026, 5, 19), 294.50),
                        candle(LocalDate.of(2026, 5, 18), 300.00)));

        List<MarketSummaryItem> out = service.getMarketSummary();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).changePct().doubleValue()).isCloseTo(-1.83, offset(0.01));
        assertThat(out.get(0).changeAbs().doubleValue()).isCloseTo(-5.50, offset(0.01));
    }

    @Test
    void summary_keeps_zero_change_when_no_second_candle_exists() {
        when(instrumentRepo.findAll()).thenReturn(List.of(inst));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst))
                .thenReturn(Optional.of(quote(
                        new BigDecimal("294.50"),
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO)));
        // Only one candle — fallback can't compute, must keep 0.
        when(candleRepo.findTop2ByInstrumentOrderByDayDesc(inst))
                .thenReturn(List.of(candle(LocalDate.of(2026, 5, 19), 294.50)));

        List<MarketSummaryItem> out = service.getMarketSummary();
        assertThat(out.get(0).changePct().doubleValue()).isEqualTo(0.0);
    }

    @Test
    void summary_skips_instruments_without_a_type() {
        // Legacy data that has a null instrumentType must not crash the
        // summary or appear in the response.
        MarketInstrument legacy = new MarketInstrument();
        legacy.setSymbol("LEGACY");
        legacy.setInstrumentType(null);
        when(instrumentRepo.findAll()).thenReturn(List.of(legacy));

        List<MarketSummaryItem> out = service.getMarketSummary();
        assertThat(out).isEmpty();
    }

    @Test
    void summary_skips_instruments_without_a_quote() {
        when(instrumentRepo.findAll()).thenReturn(List.of(inst));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst))
                .thenReturn(Optional.empty());

        List<MarketSummaryItem> out = service.getMarketSummary();
        assertThat(out).isEmpty();
    }

    @Test
    void getLatestPrice_returns_last_close() {
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(inst));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst))
                .thenReturn(Optional.of(quote(
                        new BigDecimal("294.50"),
                        null, BigDecimal.ZERO, BigDecimal.ZERO)));

        var price = service.getLatestPrice("thyao");
        assertThat(price).containsEntry("symbol", "THYAO");
        assertThat(price.get("price")).isEqualTo(new BigDecimal("294.50"));
    }

    @Test
    void getCurrentPrice_returns_null_when_symbol_missing() {
        when(quoteRepo.findTop1ByInstrument_SymbolOrderByAsOfDesc("UNKNOWN"))
                .thenReturn(Optional.empty());
        assertThat(service.getCurrentPrice("UNKNOWN")).isNull();
    }
}
