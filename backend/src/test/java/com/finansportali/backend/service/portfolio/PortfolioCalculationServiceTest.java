package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.dto.response.portfolio.AllocationByTypeItem;
import com.finansportali.backend.dto.response.portfolio.AllocationItem;
import com.finansportali.backend.dto.response.portfolio.PortfolioSummary;
import com.finansportali.backend.dto.response.portfolio.PortfolioSummaryDetail;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PortfolioPositionRepository;
import com.finansportali.backend.service.MarketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioCalculationServiceTest {

    @Mock private PortfolioPositionRepository positionRepo;
    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private MarketQuoteRepository quoteRepo;
    @Mock private MarketService marketService;
    @Mock private PortfolioCurrencyService currencyService;
    @InjectMocks private PortfolioCalculationService service;

    private static MarketInstrument inst(String symbol, String name, InstrumentType type) {
        MarketInstrument i = new MarketInstrument();
        i.setSymbol(symbol);
        i.setName(name);
        i.setInstrumentType(type);
        return i;
    }

    private static MarketQuote quote(BigDecimal last, BigDecimal changePct) {
        MarketQuote q = new MarketQuote();
        q.setLast(last);
        q.setChangePct(changePct);
        q.setAsOf(Instant.now());
        return q;
    }

    private static PortfolioPosition pos(String symbol, String qty, String avgCost) {
        return new PortfolioPosition("u", symbol, new BigDecimal(qty),
                avgCost == null ? null : new BigDecimal(avgCost));
    }

    // ---------- summary ----------

    @Test
    void summary_empty_portfolio_returns_zero_total() {
        when(positionRepo.findByUserId("u")).thenReturn(List.of());
        PortfolioSummary s = service.summary("u");
        assertThat(s.totalValue()).isEqualByComparingTo("0");
        assertThat(s.positions()).isEmpty();
    }

    @Test
    void summary_calculates_pnl_and_total_value() {
        PortfolioPosition p = pos("THYAO", "10", "100");
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));

        MarketInstrument i = inst("THYAO", "Türk Hava Yolları", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i))
                .thenReturn(Optional.of(quote(new BigDecimal("150"), BigDecimal.ONE)));

        PortfolioSummary s = service.summary("u");

        assertThat(s.totalValue()).isEqualByComparingTo("1500");
        assertThat(s.positions()).hasSize(1);
        var view = s.positions().get(0);
        assertThat(view.symbol()).isEqualTo("THYAO");
        assertThat(view.lastPrice()).isEqualByComparingTo("150");
        assertThat(view.marketValue()).isEqualByComparingTo("1500");
        assertThat(view.pnlAbs()).isEqualByComparingTo("500");     // 1500 - 1000
        assertThat(view.pnlPct()).isEqualByComparingTo("50");      // 500/1000 * 100
    }

    @Test
    void summary_position_without_avgCost_has_null_pnl() {
        PortfolioPosition p = pos("THYAO", "10", null);
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));

        MarketInstrument i = inst("THYAO", "X", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i))
                .thenReturn(Optional.of(quote(new BigDecimal("150"), BigDecimal.ZERO)));

        PortfolioSummary s = service.summary("u");
        assertThat(s.positions().get(0).pnlAbs()).isNull();
        assertThat(s.positions().get(0).pnlPct()).isNull();
    }

    @Test
    void summary_no_quote_uses_zero_price() {
        PortfolioPosition p = pos("THYAO", "10", "100");
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.empty());
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(null)).thenReturn(Optional.empty());

        PortfolioSummary s = service.summary("u");
        assertThat(s.totalValue()).isEqualByComparingTo("0");
        assertThat(s.positions().get(0).pnlPct()).isEqualByComparingTo("-100");  // (0-1000)/1000 * 100
    }

    @Test
    void summary_zero_costValue_keeps_pnlPct_zero() {
        PortfolioPosition p = pos("THYAO", "10", "0");
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.empty());
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(null))
                .thenReturn(Optional.of(quote(new BigDecimal("100"), BigDecimal.ZERO)));

        PortfolioSummary s = service.summary("u");
        assertThat(s.positions().get(0).pnlPct()).isEqualByComparingTo("0");
    }

    // ---------- allocation ----------

    @Test
    void allocation_returns_empty_for_empty_portfolio() {
        when(positionRepo.findByUserId("u")).thenReturn(List.of());
        assertThat(service.allocation("u")).isEmpty();
    }

    @Test
    void allocation_returns_weighted_percentages_sorted_desc() {
        PortfolioPosition a = pos("AAA", "10", "100");
        PortfolioPosition b = pos("BBB", "10", "100");
        when(positionRepo.findByUserId("u")).thenReturn(List.of(a, b));

        MarketInstrument ia = inst("AAA", "A", InstrumentType.STOCK);
        MarketInstrument ib = inst("BBB", "B", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("AAA")).thenReturn(Optional.of(ia));
        when(instrumentRepo.findBySymbol("BBB")).thenReturn(Optional.of(ib));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(ia))
                .thenReturn(Optional.of(quote(new BigDecimal("100"), BigDecimal.ZERO)));   // mv 1000
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(ib))
                .thenReturn(Optional.of(quote(new BigDecimal("300"), BigDecimal.ZERO)));   // mv 3000

        List<AllocationItem> alloc = service.allocation("u");
        assertThat(alloc).hasSize(2);
        assertThat(alloc.get(0).symbol()).isEqualTo("BBB");     // higher first
        assertThat(alloc.get(0).weightPct()).isEqualByComparingTo("75");
        assertThat(alloc.get(1).symbol()).isEqualTo("AAA");
        assertThat(alloc.get(1).weightPct()).isEqualByComparingTo("25");
    }

    // ---------- allocationByType ----------

    @Test
    void allocationByType_returns_empty_for_empty_portfolio() {
        when(positionRepo.findByUserId("u")).thenReturn(List.of());
        assertThat(service.allocationByType("u")).isEmpty();
    }

    @Test
    void allocationByType_groups_positions_by_instrument_type() {
        PortfolioPosition a = pos("THYAO", "10", "100");
        PortfolioPosition b = pos("XAUUSD", "1", "1000");
        when(positionRepo.findByUserId("u")).thenReturn(List.of(a, b));

        MarketInstrument ia = inst("THYAO", "Türk Hava", InstrumentType.STOCK);
        MarketInstrument ib = inst("XAUUSD", "Altın", InstrumentType.COMMODITY);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(ia));
        when(instrumentRepo.findBySymbol("XAUUSD")).thenReturn(Optional.of(ib));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(ia))
                .thenReturn(Optional.of(quote(new BigDecimal("100"), BigDecimal.ZERO)));     // 1000
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(ib))
                .thenReturn(Optional.of(quote(new BigDecimal("3000"), BigDecimal.ZERO)));    // 3000

        List<AllocationByTypeItem> alloc = service.allocationByType("u");
        assertThat(alloc).hasSize(2);
        assertThat(alloc.get(0).type()).isEqualTo("COMMODITY");
        assertThat(alloc.get(0).weightPct()).isEqualByComparingTo("75");
        assertThat(alloc.get(1).type()).isEqualTo("STOCK");
    }

    // ---------- calculatePortfolioSummaryDetail ----------

    @Test
    void detail_empty_portfolio_returns_zero_fields_and_empty_list() {
        when(positionRepo.findByUserId("u")).thenReturn(List.of());

        PortfolioSummaryDetail d = service.calculatePortfolioSummaryDetail("u");
        assertThat(d.totalInvested()).isEqualByComparingTo("0");
        assertThat(d.totalCurrentValue()).isEqualByComparingTo("0");
        assertThat(d.totalChangeValue()).isEqualByComparingTo("0");
        assertThat(d.totalChangePercent()).isEqualByComparingTo("0");
        assertThat(d.positions()).isEmpty();
    }

    @Test
    void detail_skips_position_when_instrument_unknown() {
        PortfolioPosition p = pos("UNKNOWN", "10", "100");
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        when(instrumentRepo.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        PortfolioSummaryDetail d = service.calculatePortfolioSummaryDetail("u");
        assertThat(d.positions()).isEmpty();
        assertThat(d.totalInvested()).isEqualByComparingTo("0");
    }

    @Test
    void detail_skips_position_when_current_price_zero() {
        PortfolioPosition p = pos("THYAO", "10", "100");
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));
        MarketInstrument i = inst("THYAO", "T", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i)).thenReturn(Optional.empty());

        PortfolioSummaryDetail d = service.calculatePortfolioSummaryDetail("u");
        assertThat(d.positions()).isEmpty();
    }

    @Test
    void detail_calculates_invested_current_and_change() {
        PortfolioPosition p = pos("THYAO", "10", "100");
        p.setPurchaseDate(java.time.LocalDate.of(2026, 1, 1));
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));

        MarketInstrument i = inst("THYAO", "Türk Hava", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i))
                .thenReturn(Optional.of(quote(new BigDecimal("150"), new BigDecimal("2"))));

        when(currencyService.getInstrumentCurrency("THYAO", InstrumentType.STOCK)).thenReturn("TRY");

        PortfolioSummaryDetail d = service.calculatePortfolioSummaryDetail("u");
        assertThat(d.totalInvested()).isEqualByComparingTo("1000");
        assertThat(d.totalCurrentValue()).isEqualByComparingTo("1500");
        assertThat(d.totalChangeValue()).isEqualByComparingTo("500");
        assertThat(d.totalChangePercent()).isEqualByComparingTo("50");
        assertThat(d.positions()).hasSize(1);
        var detail = d.positions().get(0);
        assertThat(detail.getSymbol()).isEqualTo("THYAO");
        assertThat(detail.getCurrency()).isEqualTo("TRY");
        assertThat(detail.getDailyChangeValue()).isEqualByComparingTo("30");   // 1500 * 0.02
    }

    @Test
    void detail_falls_back_to_today_when_purchase_date_null() {
        PortfolioPosition p = pos("THYAO", "10", "100");
        when(positionRepo.findByUserId("u")).thenReturn(List.of(p));

        MarketInstrument i = inst("THYAO", "T", InstrumentType.STOCK);
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i))
                .thenReturn(Optional.of(quote(new BigDecimal("100"), BigDecimal.ZERO)));
        when(currencyService.getInstrumentCurrency(any(), any())).thenReturn("TRY");

        PortfolioSummaryDetail d = service.calculatePortfolioSummaryDetail("u");
        assertThat(d.positions().get(0).getBuyDate()).isEqualTo(java.time.LocalDate.now());
    }
}
