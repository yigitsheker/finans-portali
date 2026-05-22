package com.finansportali.backend.service;

import com.finansportali.backend.dto.response.bond.BondDetailDto;
import com.finansportali.backend.dto.response.bond.BondHistoryPointDto;
import com.finansportali.backend.dto.response.bond.BondListItemDto;
import com.finansportali.backend.dto.response.bond.BondSummaryDto;
import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentQuote;
import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.repository.DebtInstrumentQuoteRepository;
import com.finansportali.backend.repository.DebtInstrumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtInstrumentServiceTest {

    @Mock private DebtInstrumentRepository instrumentRepo;
    @Mock private DebtInstrumentQuoteRepository quoteRepo;
    @InjectMocks private DebtInstrumentService service;

    private static DebtInstrument bond(Long id, String symbol, String name,
                                       DebtInstrumentType type, LocalDate maturity) {
        DebtInstrument b = new DebtInstrument(symbol, name, type);
        b.setId(id);
        b.setMaturityDate(maturity);
        b.setIsin("ISIN-" + symbol);
        b.setIssuer("Hazine");
        b.setCurrency("TRY");
        b.setCouponRate(new BigDecimal("8.50"));
        b.setCouponType("Sabit");
        return b;
    }

    private static DebtInstrumentQuote q(BigDecimal price, BigDecimal yield, BigDecimal change) {
        DebtInstrumentQuote x = new DebtInstrumentQuote();
        x.setPrice(price);
        x.setYieldRate(yield);
        x.setCleanPrice(price);
        x.setDirtyPrice(price);
        x.setChangeRate(change);
        x.setVolume(BigDecimal.TEN);
        x.setSource("TCMB");
        x.setCreatedAt(LocalDateTime.now());
        return x;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listBonds_returns_dtos_with_latest_quote() {
        DebtInstrument b = bond(1L, "TR2YT", "2Y TRY Bond",
                DebtInstrumentType.GOVERNMENT_BOND, LocalDate.now().plusYears(2));
        when(instrumentRepo.findAll(any(Specification.class))).thenReturn(List.of(b));
        when(quoteRepo.findLatestByInstrument(b))
                .thenReturn(Optional.of(q(new BigDecimal("100"), new BigDecimal("12"), new BigDecimal("0.5"))));

        List<BondListItemDto> list = service.listBonds(null, null, null, null, null);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getSymbol()).isEqualTo("TR2YT");
        assertThat(list.get(0).getLatestPrice()).isEqualByComparingTo("100");
        assertThat(list.get(0).getLatestYieldRate()).isEqualByComparingTo("12");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listBonds_handles_bond_without_quote() {
        DebtInstrument b = bond(1L, "TR2YT", "2Y", DebtInstrumentType.GOVERNMENT_BOND, LocalDate.now());
        when(instrumentRepo.findAll(any(Specification.class))).thenReturn(List.of(b));
        when(quoteRepo.findLatestByInstrument(b)).thenReturn(Optional.empty());

        List<BondListItemDto> list = service.listBonds(null, null, null, null, null);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getLatestPrice()).isNull();
    }

    @Test
    void getBondDetail_throws_when_not_found() {
        when(instrumentRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBondDetail(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getBondDetail_maps_all_fields_and_computes_days_to_maturity() {
        LocalDate maturity = LocalDate.now().plusDays(365);
        DebtInstrument b = bond(1L, "TR2YT", "2Y", DebtInstrumentType.GOVERNMENT_BOND, maturity);
        when(instrumentRepo.findById(1L)).thenReturn(Optional.of(b));
        when(quoteRepo.findLatestByInstrument(b))
                .thenReturn(Optional.of(q(new BigDecimal("100"), new BigDecimal("12"), BigDecimal.ZERO)));

        BondDetailDto dto = service.getBondDetail(1L);
        assertThat(dto.getSymbol()).isEqualTo("TR2YT");
        assertThat(dto.getIsin()).isEqualTo("ISIN-TR2YT");
        assertThat(dto.getDaysToMaturity()).isEqualTo(365L);
        assertThat(dto.getCouponRate()).isEqualByComparingTo("8.50");
        assertThat(dto.getLatestPrice()).isEqualByComparingTo("100");
    }

    @Test
    void getBondDetail_keeps_null_days_when_no_maturity() {
        DebtInstrument b = bond(1L, "TR2YT", "2Y", DebtInstrumentType.GOVERNMENT_BOND, null);
        when(instrumentRepo.findById(1L)).thenReturn(Optional.of(b));
        when(quoteRepo.findLatestByInstrument(b)).thenReturn(Optional.empty());

        BondDetailDto dto = service.getBondDetail(1L);
        assertThat(dto.getDaysToMaturity()).isNull();
    }

    @Test
    void getBondHistory_throws_when_bond_not_found() {
        when(instrumentRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBondHistory(99L, LocalDate.now(), LocalDate.now()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getBondHistory_maps_quotes_to_dtos() {
        DebtInstrument b = bond(1L, "TR2YT", "2Y", DebtInstrumentType.GOVERNMENT_BOND, LocalDate.now());
        when(instrumentRepo.findById(1L)).thenReturn(Optional.of(b));

        DebtInstrumentQuote qq = q(new BigDecimal("99"), new BigDecimal("13"), BigDecimal.ZERO);
        qq.setQuoteDate(LocalDate.of(2026, 4, 1));
        when(quoteRepo.findHistoricalQuotes(b, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1)))
                .thenReturn(List.of(qq));

        List<BondHistoryPointDto> hist = service.getBondHistory(1L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1));
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).getDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(hist.get(0).getYieldRate()).isEqualByComparingTo("13");
    }

    @Test
    void getSummary_aggregates_yields_and_maturity_bounds() {
        DebtInstrument b1 = bond(1L, "A", "Bond A", DebtInstrumentType.GOVERNMENT_BOND,
                LocalDate.of(2027, 1, 1));
        DebtInstrument b2 = bond(2L, "B", "Bond B", DebtInstrumentType.GOVERNMENT_BOND,
                LocalDate.of(2026, 6, 1));
        when(instrumentRepo.findByActiveTrue()).thenReturn(List.of(b1, b2));
        when(quoteRepo.findLatestByInstrument(b1))
                .thenReturn(Optional.of(q(new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ZERO)));
        when(quoteRepo.findLatestByInstrument(b2))
                .thenReturn(Optional.of(q(new BigDecimal("100"), new BigDecimal("20"), BigDecimal.ZERO)));
        when(quoteRepo.findLatestQuoteDate()).thenReturn(Optional.of(LocalDate.of(2026, 5, 1)));

        BondSummaryDto s = service.getSummary();
        assertThat(s.getTotalInstruments()).isEqualTo(2);
        assertThat(s.getAverageYield()).isEqualByComparingTo("15");
        assertThat(s.getHighestYield()).isEqualByComparingTo("20");
        assertThat(s.getNearestMaturity()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(s.getFarthestMaturity()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void getSummary_skips_yield_aggregation_for_bonds_without_quote() {
        DebtInstrument b = bond(1L, "A", "Bond A", DebtInstrumentType.GOVERNMENT_BOND,
                LocalDate.of(2026, 6, 1));
        when(instrumentRepo.findByActiveTrue()).thenReturn(List.of(b));
        when(quoteRepo.findLatestByInstrument(b)).thenReturn(Optional.empty());
        when(quoteRepo.findLatestQuoteDate()).thenReturn(Optional.empty());

        BondSummaryDto s = service.getSummary();
        assertThat(s.getTotalInstruments()).isEqualTo(1);
        assertThat(s.getAverageYield()).isNull();
        assertThat(s.getLastUpdateDate()).isNull();
    }
}
