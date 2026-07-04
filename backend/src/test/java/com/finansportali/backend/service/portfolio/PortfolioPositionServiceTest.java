package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.dto.request.SellPositionRequest;
import com.finansportali.backend.dto.request.UpsertPositionRequest;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.repository.InvestmentFundRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PortfolioPositionRepository;
import com.finansportali.backend.service.MarketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPositionServiceTest {

    @Mock private PortfolioPositionRepository positionRepo;
    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private MarketQuoteRepository quoteRepo;
    @Mock private MarketService marketService;
    @Mock private com.finansportali.backend.repository.PortfolioTransactionRepository txRepo;
    @Mock private InvestmentFundRepository fundRepo;
    @InjectMocks private PortfolioPositionService service;

    private static MarketInstrument inst(String symbol) {
        MarketInstrument i = new MarketInstrument();
        i.setSymbol(symbol);
        return i;
    }

    // ---------- upsert ----------

    @Test
    void upsert_throws_when_symbol_blank() {
        assertThatThrownBy(() -> service.upsert("u",
                new UpsertPositionRequest("", BigDecimal.ONE, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol");
    }

    @Test
    void upsert_throws_when_quantity_null() {
        assertThatThrownBy(() -> service.upsert("u",
                new UpsertPositionRequest("THYAO", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void upsert_throws_when_symbol_unknown() {
        when(instrumentRepo.findBySymbol("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert("u",
                new UpsertPositionRequest("XXX", BigDecimal.ONE, new BigDecimal("10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown symbol");

        verify(marketService).seedIfEmpty();
        verify(positionRepo, never()).save(any());
    }

    @Test
    void upsert_creates_new_position_with_purchase_date() {
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(inst("THYAO")));
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.empty());

        service.upsert("u", new UpsertPositionRequest("THYAO", new BigDecimal("10"), new BigDecimal("100")));

        ArgumentCaptor<PortfolioPosition> cap = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepo).save(cap.capture());
        PortfolioPosition saved = cap.getValue();
        assertThat(saved.getSymbol()).isEqualTo("THYAO");
        assertThat(saved.getQuantity()).isEqualByComparingTo("10");
        assertThat(saved.getAvgCost()).isEqualByComparingTo("100");
        assertThat(saved.getPurchaseDate()).isNotNull();
    }

    @Test
    void upsert_adds_to_existing_quantity_and_computes_weighted_avg() {
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(inst("THYAO")));
        PortfolioPosition existing = new PortfolioPosition("u", "THYAO",
                new BigDecimal("10"), new BigDecimal("100"));
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.of(existing));

        // Add 10 more at 200 → total 20 at avg 150
        service.upsert("u", new UpsertPositionRequest("THYAO", new BigDecimal("10"), new BigDecimal("200")));

        assertThat(existing.getQuantity()).isEqualByComparingTo("20");
        BigDecimal expected = new BigDecimal("150").setScale(6, RoundingMode.HALF_UP);
        assertThat(existing.getAvgCost()).isEqualByComparingTo(expected);
        verify(positionRepo).save(existing);
    }

    @Test
    void upsert_keeps_existing_avgCost_when_request_avgCost_null_or_zero() {
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(inst("THYAO")));
        PortfolioPosition existing = new PortfolioPosition("u", "THYAO",
                new BigDecimal("5"), new BigDecimal("100"));
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.of(existing));

        service.upsert("u", new UpsertPositionRequest("THYAO", new BigDecimal("5"), null));

        assertThat(existing.getQuantity()).isEqualByComparingTo("10");
        assertThat(existing.getAvgCost()).isEqualByComparingTo("100");
    }

    @Test
    void upsert_sets_avgCost_when_existing_avgCost_was_zero() {
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(inst("THYAO")));
        PortfolioPosition existing = new PortfolioPosition("u", "THYAO",
                new BigDecimal("5"), BigDecimal.ZERO);
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.of(existing));

        service.upsert("u", new UpsertPositionRequest("THYAO", new BigDecimal("5"), new BigDecimal("200")));

        assertThat(existing.getAvgCost()).isEqualByComparingTo("200");
    }

    // ---------- list / clear ----------

    @Test
    void list_returns_repository_results() {
        List<PortfolioPosition> rows = List.of(new PortfolioPosition("u", "THYAO", BigDecimal.ONE, BigDecimal.ONE));
        when(positionRepo.findByUserId("u")).thenReturn(rows);
        assertThat(service.list("u")).isEqualTo(rows);
    }

    @Test
    void clear_delegates_to_repo() {
        service.clear("u");
        verify(positionRepo).deleteByUserId("u");
    }

    // ---------- deleteBySymbol ----------

    @Test
    void deleteBySymbol_throws_on_blank_symbol() {
        assertThatThrownBy(() -> service.deleteBySymbol("u", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol");
    }

    @Test
    void deleteBySymbol_throws_when_no_rows_deleted() {
        when(positionRepo.deleteByUserIdAndSymbol("u", "THYAO")).thenReturn(0L);
        assertThatThrownBy(() -> service.deleteBySymbol("u", "THYAO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Position not found");
    }

    @Test
    void deleteBySymbol_succeeds_when_repository_deleted_a_row() {
        when(positionRepo.deleteByUserIdAndSymbol("u", "THYAO")).thenReturn(1L);
        service.deleteBySymbol("u", "THYAO");
    }

    // ---------- sell ----------

    @Test
    void sell_throws_on_blank_symbol() {
        assertThatThrownBy(() -> service.sell("u", new SellPositionRequest("", BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol");
    }

    @Test
    void sell_throws_on_zero_or_negative_quantity() {
        assertThatThrownBy(() -> service.sell("u", new SellPositionRequest("THYAO", BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("> 0");
    }

    @Test
    void sell_throws_when_position_not_found() {
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sell("u", new SellPositionRequest("THYAO", BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Position not found");
    }

    @Test
    void sell_throws_when_quantity_exceeds_position() {
        PortfolioPosition pos = new PortfolioPosition("u", "THYAO", new BigDecimal("5"), new BigDecimal("100"));
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.of(pos));

        assertThatThrownBy(() -> service.sell("u", new SellPositionRequest("THYAO", new BigDecimal("10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fazla");
    }

    @Test
    void sell_partial_returns_proceeds_and_updates_position() {
        PortfolioPosition pos = new PortfolioPosition("u", "THYAO", new BigDecimal("10"), new BigDecimal("100"));
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.of(pos));

        MarketInstrument i = inst("THYAO");
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));

        MarketQuote q = new MarketQuote();
        q.setLast(new BigDecimal("150"));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i)).thenReturn(Optional.of(q));

        BigDecimal proceeds = service.sell("u", new SellPositionRequest("THYAO", new BigDecimal("4")));

        assertThat(proceeds).isEqualByComparingTo("600");      // 4 * 150
        assertThat(pos.getQuantity()).isEqualByComparingTo("6");
        verify(positionRepo).save(pos);
        verify(positionRepo, never()).deleteByUserIdAndSymbol(anyString(), anyString());
    }

    @Test
    void sell_full_deletes_position() {
        PortfolioPosition pos = new PortfolioPosition("u", "THYAO", new BigDecimal("10"), new BigDecimal("100"));
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.of(pos));

        MarketInstrument i = inst("THYAO");
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i)).thenReturn(Optional.empty());

        BigDecimal proceeds = service.sell("u", new SellPositionRequest("THYAO", new BigDecimal("10")));

        // Fallback to avgCost → 10 * 100
        assertThat(proceeds).isEqualByComparingTo("1000");
        verify(positionRepo).deleteByUserIdAndSymbol("u", "THYAO");
        verify(positionRepo, never()).save(any());
    }

    @Test
    void sell_falls_back_to_zero_when_no_quote_and_no_avgCost() {
        PortfolioPosition pos = new PortfolioPosition("u", "THYAO", new BigDecimal("10"), null);
        when(positionRepo.findByUserIdAndSymbol("u", "THYAO")).thenReturn(Optional.of(pos));

        MarketInstrument i = inst("THYAO");
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(i));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(i)).thenReturn(Optional.empty());

        BigDecimal proceeds = service.sell("u", new SellPositionRequest("THYAO", new BigDecimal("4")));

        assertThat(proceeds).isEqualByComparingTo("0");
    }
}
