package com.finansportali.backend.mapper;

import com.finansportali.backend.dto.response.portfolio.PortfolioPositionDetail;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.PortfolioPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Unit tests for {@link PortfolioMapper}. The mapper computes invested
 * amount / current value / total-change and infers currency from the
 * symbol + instrument type. Each test pins one branch of the currency
 * heuristic plus one numeric invariant.
 */
class PortfolioMapperTest {

    private final PortfolioMapper mapper = new PortfolioMapper();

    private MarketInstrument instr(String symbol, String name, InstrumentType type) {
        MarketInstrument m = new MarketInstrument();
        m.setSymbol(symbol);
        m.setName(name);
        m.setInstrumentType(type);
        return m;
    }

    private PortfolioPosition position(String symbol, double qty, double avgCost) {
        PortfolioPosition p = new PortfolioPosition("user-1", symbol, BigDecimal.valueOf(qty), BigDecimal.valueOf(avgCost));
        p.setPurchaseDate(LocalDate.of(2026, 1, 1));
        return p;
    }

    @Test
    void bist_position_resolves_to_try_currency() {
        PortfolioPosition pos = position("THYAO", 10, 100.0);
        MarketInstrument inst = instr("THYAO", "Türk Hava Yolları", InstrumentType.BIST);

        PortfolioPositionDetail d = mapper.toPositionDetail(
                pos, inst, new BigDecimal("294.50"), new BigDecimal("-1.50"));

        assertThat(d.getCurrency()).isEqualTo("TRY");
        assertThat(d.getType()).isEqualTo("BIST");
    }

    @Test
    void us_stock_resolves_to_usd_currency() {
        PortfolioPosition pos = position("AAPL", 5, 150.0);
        MarketInstrument inst = instr("AAPL", "Apple", InstrumentType.STOCK);

        PortfolioPositionDetail d = mapper.toPositionDetail(
                pos, inst, new BigDecimal("200.00"), new BigDecimal("0.50"));

        assertThat(d.getCurrency()).isEqualTo("USD");
        assertThat(d.getType()).isEqualTo("STOCK");
    }

    @Test
    void crypto_resolves_to_usd_currency() {
        PortfolioPosition pos = position("BTCUSD", 0.5, 50000);
        MarketInstrument inst = instr("BTCUSD", "Bitcoin", InstrumentType.CRYPTO);

        PortfolioPositionDetail d = mapper.toPositionDetail(
                pos, inst, new BigDecimal("70000"), new BigDecimal("2.00"));

        assertThat(d.getCurrency()).isEqualTo("USD");
    }

    @Test
    void invested_and_current_and_change_are_consistent() {
        PortfolioPosition pos = position("THYAO", 10, 100.0);
        MarketInstrument inst = instr("THYAO", "Türk Hava Yolları", InstrumentType.BIST);

        PortfolioPositionDetail d = mapper.toPositionDetail(
                pos, inst, new BigDecimal("120"), new BigDecimal("1.50"));

        // invested = 100 × 10 = 1000
        assertThat(d.getInvestedAmount()).isEqualByComparingTo("1000");
        // current = 120 × 10 = 1200
        assertThat(d.getCurrentValue()).isEqualByComparingTo("1200");
        // totalChangeValue = 1200 − 1000 = 200
        assertThat(d.getTotalChangeValue()).isEqualByComparingTo("200");
        // totalChangePercent = 200 / 1000 × 100 = 20%
        assertThat(d.getTotalChangePercent().doubleValue()).isCloseTo(20.0, offset(0.001));
        // dailyChangeValue = 1200 × 1.50% = 18
        assertThat(d.getDailyChangeValue().doubleValue()).isCloseTo(18.0, offset(0.001));
    }

    @Test
    void zero_invested_amount_avoids_division_by_zero_in_percent() {
        // avgCost = 0 → invested = 0; the % computation must guard.
        PortfolioPosition pos = position("THYAO", 10, 0.0);
        MarketInstrument inst = instr("THYAO", "Türk Hava Yolları", InstrumentType.BIST);

        PortfolioPositionDetail d = mapper.toPositionDetail(
                pos, inst, new BigDecimal("120"), new BigDecimal("0.0"));

        assertThat(d.getTotalChangePercent()).isEqualByComparingTo("0");
    }

    @Test
    void missing_purchase_date_falls_back_to_today() {
        PortfolioPosition pos = position("THYAO", 10, 100.0);
        pos.setPurchaseDate(null); // no buy date recorded
        MarketInstrument inst = instr("THYAO", "Türk Hava Yolları", InstrumentType.BIST);

        PortfolioPositionDetail d = mapper.toPositionDetail(
                pos, inst, new BigDecimal("120"), new BigDecimal("0.0"));

        // Today, ignoring time-of-day skew.
        assertThat(d.getBuyDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void null_instrument_type_falls_back_to_stock() {
        PortfolioPosition pos = position("XYZ", 10, 50);
        MarketInstrument inst = instr("XYZ", "Unknown", null);

        PortfolioPositionDetail d = mapper.toPositionDetail(
                pos, inst, new BigDecimal("60"), new BigDecimal("0.0"));

        assertThat(d.getType()).isEqualTo("STOCK");
    }
}
