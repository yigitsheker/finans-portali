package com.finansportali.backend.service.viop;

import com.finansportali.backend.dto.response.viop.ViopPositionView;
import com.finansportali.backend.dto.response.viop.ViopPreviewResult;
import com.finansportali.backend.dto.response.viop.ViopSummary;
import com.finansportali.backend.dto.response.viop.ViopTradeResult;
import com.finansportali.backend.dto.response.viop.ViopTransactionView;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.entity.ViopDirection;
import com.finansportali.backend.entity.ViopPosition;
import com.finansportali.backend.entity.ViopPositionStatus;
import com.finansportali.backend.entity.ViopTransaction;
import com.finansportali.backend.entity.ViopTransactionType;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.ViopContractRepository;
import com.finansportali.backend.repository.ViopPositionRepository;
import com.finansportali.backend.repository.ViopTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@link ViopPositionService} through its net-position open/close/expire
 * orchestration plus the read/summary/preview paths to maximise line & branch
 * coverage. The pure math lives in {@link ViopCalculationService} (covered
 * separately); here we mock it so each branch can be asserted in isolation,
 * with a couple of tests using the real calculator to pin realized P&L.
 */
@ExtendWith(MockitoExtension.class)
class ViopPositionServiceTest {

    private static final String USER = "user-1";
    private static final String SYMBOL = "F_XU0300625";

    @Mock private ViopPositionRepository positionRepo;
    @Mock private ViopTransactionRepository txnRepo;
    @Mock private ViopContractRepository contractRepo;
    @Mock private MarketQuoteRepository marketQuoteRepo;
    @Mock private ViopCalculationService calc;

    private ViopPositionService service;

    @BeforeEach
    void setUp() {
        service = new ViopPositionService(positionRepo, txnRepo, contractRepo, marketQuoteRepo, calc);
        ReflectionTestUtils.setField(service, "marginRate", new BigDecimal("0.10"));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    /** A live INDEX contract maturing far in the future with a usable last price. */
    private ViopContract liveContract() {
        ViopContract c = new ViopContract();
        c.setSymbol(SYMBOL);
        c.setName("BIST 30 Haziran");
        c.setUnderlying("XU030");
        c.setCategory(ViopContract.Category.INDEX);
        YearMonth future = YearMonth.now().plusYears(1);
        c.setMaturityYear(future.getYear());
        c.setMaturityMonth(future.getMonthValue());
        c.setLastPrice(bd("12000"));
        return c;
    }

    private ViopPosition openPosition(ViopDirection dir, String qty, String entry) {
        ViopPosition p = new ViopPosition();
        ReflectionTestUtils.setField(p, "id", 99L);
        p.setUserId(USER);
        p.setContractSymbol(SYMBOL);
        p.setUnderlying("XU030");
        p.setContractType(ViopContract.Category.INDEX.name());
        p.setMaturityDate(LocalDate.now().plusMonths(6));
        p.setDirection(dir);
        p.setQuantity(bd(qty));
        p.setEntryPrice(bd(entry));
        p.setContractSize(bd("10"));
        p.setCurrency("TRY");
        p.setRealizedPnl(BigDecimal.ZERO);
        p.setStatus(ViopPositionStatus.OPEN);
        return p;
    }

    /** Stub the calc methods used by save/recompute/toView with harmless defaults. */
    private void stubValuationCalc() {
        lenient().when(calc.positionSize(any(), any(), any())).thenReturn(bd("240000.00"));
        lenient().when(calc.requiredMargin(any(), any(), any(), any())).thenReturn(bd("24000.00"));
        lenient().when(calc.leverage(any(), any())).thenReturn(bd("10.00"));
        lenient().when(calc.unrealizedPnl(any(), any(), any(), any(), any())).thenReturn(bd("0.00"));
    }

    // ────────────────────────────── open() ──────────────────────────────────

    @Test
    void open_throws_notFound_when_contract_missing() {
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(USER, SYMBOL, ViopDirection.LONG, bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).isEqualTo("Kontrat bulunamadı");
                });
        verify(positionRepo, never()).save(any());
    }

    @Test
    void open_throws_badRequest_when_contract_matured() {
        ViopContract c = liveContract();
        YearMonth past = YearMonth.now().minusMonths(1);
        c.setMaturityYear(past.getYear());
        c.setMaturityMonth(past.getMonthValue());
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.open(USER, SYMBOL, ViopDirection.LONG, bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Vadesi geçmiş kontratta yeni pozisyon açılamaz");
                });
    }

    @Test
    void open_throws_badRequest_when_qty_null() {
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(liveContract()));

        assertThatThrownBy(() -> service.open(USER, SYMBOL, ViopDirection.LONG, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Kontrat adedi sıfırdan büyük olmalı");
                });
    }

    @Test
    void open_throws_badRequest_when_qty_zero() {
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(liveContract()));

        assertThatThrownBy(() -> service.open(USER, SYMBOL, ViopDirection.LONG, BigDecimal.ZERO, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Kontrat adedi sıfırdan büyük olmalı");
                });
    }

    @Test
    void open_throws_badRequest_when_no_price_available() {
        ViopContract c = liveContract();
        c.setLastPrice(null);
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(marketQuoteRepo.findTop1ByInstrument_SymbolOrderByAsOfDesc(SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(USER, SYMBOL, ViopDirection.LONG, bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Kontrat için güncel fiyat bulunamadı");
                });
    }

    @Test
    void open_fresh_long_position_records_long_open_leg() {
        ViopContract c = liveContract();
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.empty());
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> {
            ViopTransaction t = inv.getArgument(0);
            t.setExecutedAt(Instant.now());
            return t;
        });
        stubValuationCalc();

        ViopTradeResult result = service.open(USER, SYMBOL, ViopDirection.LONG, bd("2"), bd("12000"));

        assertThat(result.message()).isEqualTo("Pozisyon güncellendi (simülasyon)");
        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.LONG_OPEN.name());

        // Saved as a fresh OPEN LONG with entry = override price and qty = requested.
        verify(positionRepo).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getStatus() == ViopPositionStatus.OPEN
                        && p.getDirection() == ViopDirection.LONG
                        && p.getQuantity().compareTo(bd("2")) == 0
                        && p.getEntryPrice().compareTo(bd("12000")) == 0
                        && "TRY".equals(p.getCurrency())));
    }

    @Test
    void open_fresh_short_position_records_short_open_leg() {
        ViopContract c = liveContract();
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.empty());
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        stubValuationCalc();

        ViopTradeResult result = service.open(USER, SYMBOL, ViopDirection.SHORT, bd("1"), null);

        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.SHORT_OPEN.name());
        verify(positionRepo).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getDirection() == ViopDirection.SHORT && p.getEntryPrice().compareTo(bd("12000")) == 0));
    }

    @Test
    void open_same_side_adds_with_weighted_average_entry() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "2", "12000");
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        stubValuationCalc();

        // Add 2 more @ 12300 → weighted = (12000*2 + 12300*2)/4 = 12150
        ViopTradeResult result = service.open(USER, SYMBOL, ViopDirection.LONG, bd("2"), bd("12300"));

        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.LONG_OPEN.name());
        assertThat(existing.getQuantity()).isEqualByComparingTo("4");
        assertThat(existing.getEntryPrice()).isEqualByComparingTo("12150.000000");
        // No close leg because same direction.
        verify(calc, never()).realizedPnl(any(), any(), any(), any(), any());
    }

    @Test
    void open_opposite_side_partial_close_keeps_position_open() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "5", "12000");
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.LONG), any(), any(), any(), eq(bd("2"))))
                .thenReturn(bd("600.00"));
        stubValuationCalc();

        // SHORT 2 against LONG 5 → close 2 (partial), 3 remain LONG, no flip.
        ViopTradeResult result = service.open(USER, SYMBOL, ViopDirection.SHORT, bd("2"), bd("12300"));

        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.PARTIAL_CLOSE.name());
        assertThat(existing.getStatus()).isEqualTo(ViopPositionStatus.OPEN);
        assertThat(existing.getQuantity()).isEqualByComparingTo("3");
        assertThat(existing.getRealizedPnl()).isEqualByComparingTo("600.00");
    }

    @Test
    void open_opposite_side_full_close_no_remainder_closes_position() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "3", "12000");
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.LONG), any(), any(), any(), eq(bd("3"))))
                .thenReturn(bd("900.00"));
        stubValuationCalc();

        // SHORT 3 against LONG 3 → full close (LONG_CLOSE), nothing remains.
        ViopTradeResult result = service.open(USER, SYMBOL, ViopDirection.SHORT, bd("3"), bd("12300"));

        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.LONG_CLOSE.name());
        assertThat(existing.getStatus()).isEqualTo(ViopPositionStatus.CLOSED);
        assertThat(existing.getQuantity()).isEqualByComparingTo("0");
        assertThat(existing.getClosedAt()).isNotNull();
        assertThat(existing.getRealizedPnl()).isEqualByComparingTo("900.00");
    }

    @Test
    void open_opposite_side_full_close_short_uses_short_close_type() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.SHORT, "3", "12000");
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.SHORT), any(), any(), any(), eq(bd("3"))))
                .thenReturn(bd("-300.00"));
        stubValuationCalc();

        ViopTradeResult result = service.open(USER, SYMBOL, ViopDirection.LONG, bd("3"), bd("12100"));

        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.SHORT_CLOSE.name());
        assertThat(existing.getStatus()).isEqualTo(ViopPositionStatus.CLOSED);
    }

    @Test
    void open_opposite_side_flip_closes_then_opens_remainder_other_direction() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "2", "12000");
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.LONG), any(), any(), any(), eq(bd("2"))))
                .thenReturn(bd("600.00"));
        stubValuationCalc();

        // SHORT 5 against LONG 2 → close 2 (LONG_CLOSE) + flip 3 to SHORT_OPEN.
        ViopTradeResult result = service.open(USER, SYMBOL, ViopDirection.SHORT, bd("5"), bd("12300"));

        assertThat(result.legs()).hasSize(2);
        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.LONG_CLOSE.name());
        assertThat(result.legs().get(1).type()).isEqualTo(ViopTransactionType.SHORT_OPEN.name());
        // Row flipped to SHORT with the 3 remaining contracts at the new price.
        assertThat(existing.getDirection()).isEqualTo(ViopDirection.SHORT);
        assertThat(existing.getQuantity()).isEqualByComparingTo("3");
        assertThat(existing.getEntryPrice()).isEqualByComparingTo("12300");
        assertThat(existing.getStatus()).isEqualTo(ViopPositionStatus.OPEN);
        // Realized P&L from the close leg accumulated on the row.
        assertThat(existing.getRealizedPnl()).isEqualByComparingTo("600.00");
        verify(positionRepo, times(2)).save(any(ViopPosition.class));
    }

    @Test
    void open_uses_lastPrice_when_no_override() {
        ViopContract c = liveContract();
        c.setLastPrice(bd("13500"));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.empty());
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        stubValuationCalc();

        service.open(USER, SYMBOL, ViopDirection.LONG, bd("1"), null);

        verify(positionRepo).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getEntryPrice().compareTo(bd("13500")) == 0));
    }

    @Test
    void open_falls_back_to_market_quote_when_no_lastPrice() {
        ViopContract c = liveContract();
        c.setLastPrice(null);
        MarketQuote q = new MarketQuote();
        q.setLast(bd("14000"));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(marketQuoteRepo.findTop1ByInstrument_SymbolOrderByAsOfDesc(SYMBOL)).thenReturn(Optional.of(q));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.empty());
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        stubValuationCalc();

        service.open(USER, SYMBOL, ViopDirection.LONG, bd("1"), null);

        verify(positionRepo).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getEntryPrice().compareTo(bd("14000")) == 0));
    }

    @Test
    void open_uses_real_calculator_for_realized_pnl_on_close() {
        // Wire a real calculator to pin the LONG realized P&L formula end-to-end.
        ViopPositionService realCalcSvc = new ViopPositionService(
                positionRepo, txnRepo, contractRepo, marketQuoteRepo, new ViopCalculationService());
        ReflectionTestUtils.setField(realCalcSvc, "marginRate", new BigDecimal("0.10"));

        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "2", "12000");
        existing.setContractSize(bd("10"));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Full close: SHORT 2 vs LONG 2 @ 12300 → (12300-12000)*10*2 = 6000.
        realCalcSvc.open(USER, SYMBOL, ViopDirection.SHORT, bd("2"), bd("12300"));

        assertThat(existing.getRealizedPnl()).isEqualByComparingTo("6000.00");
        assertThat(existing.getStatus()).isEqualTo(ViopPositionStatus.CLOSED);
    }

    // ────────────────────────────── close() ─────────────────────────────────

    @Test
    void close_throws_when_no_open_position() {
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(USER, SYMBOL, bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Açık pozisyon bulunamadı");
                });
    }

    @Test
    void close_throws_when_position_already_closed() {
        ViopPosition closed = openPosition(ViopDirection.LONG, "0", "12000");
        closed.setStatus(ViopPositionStatus.CLOSED);
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.close(USER, SYMBOL, bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Açık pozisyon bulunamadı");
                });
    }

    @Test
    void close_throws_when_qty_null() {
        ViopPosition existing = openPosition(ViopDirection.LONG, "5", "12000");
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.close(USER, SYMBOL, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Kapatılacak adet sıfırdan büyük olmalı");
                });
    }

    @Test
    void close_throws_when_qty_not_positive() {
        ViopPosition existing = openPosition(ViopDirection.LONG, "5", "12000");
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.close(USER, SYMBOL, BigDecimal.ZERO, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Kapatılacak adet sıfırdan büyük olmalı");
                });
    }

    @Test
    void close_throws_when_qty_exceeds_open() {
        ViopPosition existing = openPosition(ViopDirection.LONG, "5", "12000");
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.close(USER, SYMBOL, bd("6"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Kapatılacak adet açık pozisyon adedinden fazla olamaz");
                });
    }

    @Test
    void close_partial_keeps_position_open_with_partial_close_type() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "5", "12000");
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.LONG), any(), any(), any(), eq(bd("2"))))
                .thenReturn(bd("600.00"));
        stubValuationCalc();

        ViopTradeResult result = service.close(USER, SYMBOL, bd("2"), bd("12300"));

        assertThat(result.message()).isEqualTo("Pozisyon kapatıldı (simülasyon)");
        assertThat(result.legs()).hasSize(1);
        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.PARTIAL_CLOSE.name());
        assertThat(existing.getStatus()).isEqualTo(ViopPositionStatus.OPEN);
        assertThat(existing.getQuantity()).isEqualByComparingTo("3");
        assertThat(existing.getRealizedPnl()).isEqualByComparingTo("600.00");
    }

    @Test
    void close_full_long_sets_closed_and_long_close_type() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "3", "12000");
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.LONG), any(), any(), any(), eq(bd("3"))))
                .thenReturn(bd("900.00"));
        stubValuationCalc();

        ViopTradeResult result = service.close(USER, SYMBOL, bd("3"), bd("12300"));

        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.LONG_CLOSE.name());
        assertThat(existing.getStatus()).isEqualTo(ViopPositionStatus.CLOSED);
        assertThat(existing.getQuantity()).isEqualByComparingTo("0");
        assertThat(existing.getClosedAt()).isNotNull();
    }

    @Test
    void close_full_short_sets_closed_and_short_close_type() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.SHORT, "3", "12000");
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.SHORT), any(), any(), any(), eq(bd("3"))))
                .thenReturn(bd("900.00"));
        stubValuationCalc();

        ViopTradeResult result = service.close(USER, SYMBOL, bd("3"), bd("11700"));

        assertThat(result.legs().get(0).type()).isEqualTo(ViopTransactionType.SHORT_CLOSE.name());
        assertThat(existing.getStatus()).isEqualTo(ViopPositionStatus.CLOSED);
    }

    @Test
    void close_uses_contract_lastPrice_when_no_override() {
        ViopContract c = liveContract();
        c.setLastPrice(bd("12500"));
        ViopPosition existing = openPosition(ViopDirection.LONG, "3", "12000");
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.LONG), any(), eq(bd("12500")), any(), eq(bd("3"))))
                .thenReturn(bd("1500.00"));
        stubValuationCalc();

        service.close(USER, SYMBOL, bd("3"), null);

        verify(calc).realizedPnl(eq(ViopDirection.LONG), any(), eq(bd("12500")), any(), eq(bd("3")));
    }

    @Test
    void close_throws_when_contract_missing_and_no_price() {
        // contractRepo returns empty → resolvePrice(null) with no override → BAD_REQUEST.
        ViopPosition existing = openPosition(ViopDirection.LONG, "3", "12000");
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(USER, SYMBOL, bd("3"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Kontrat için güncel fiyat bulunamadı");
                });
    }

    // ───────────────────────── expireDuePositions() ─────────────────────────

    @Test
    void expire_returns_zero_when_nothing_due() {
        when(positionRepo.findByStatusAndMaturityDateLessThanEqual(eq(ViopPositionStatus.OPEN), any(LocalDate.class)))
                .thenReturn(List.of());

        int n = service.expireDuePositions();

        assertThat(n).isZero();
        verify(positionRepo, never()).save(any());
        verify(txnRepo, never()).save(any());
    }

    @Test
    void expire_settles_due_positions_using_contract_price() {
        ViopContract c = liveContract();
        c.setLastPrice(bd("12500"));
        ViopPosition due = openPosition(ViopDirection.LONG, "2", "12000");
        due.setMaturityDate(LocalDate.now().minusDays(1));
        when(positionRepo.findByStatusAndMaturityDateLessThanEqual(eq(ViopPositionStatus.OPEN), any(LocalDate.class)))
                .thenReturn(List.of(due));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(calc.realizedPnl(eq(ViopDirection.LONG), eq(bd("12000")), eq(bd("12500")), eq(bd("10")), eq(bd("2"))))
                .thenReturn(bd("1000.00"));
        lenient().when(calc.positionSize(any(), any(), any())).thenReturn(bd("250000.00"));

        int n = service.expireDuePositions();

        assertThat(n).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(ViopPositionStatus.EXPIRED);
        assertThat(due.getQuantity()).isEqualByComparingTo("0");
        assertThat(due.getClosedAt()).isNotNull();
        assertThat(due.getRealizedPnl()).isEqualByComparingTo("1000.00");
        verify(txnRepo).save(org.mockito.ArgumentMatchers.argThat(t -> t.getType() == ViopTransactionType.EXPIRE));
    }

    @Test
    void expire_falls_back_to_entry_price_when_no_market_price() {
        // Contract missing entirely → resolvePriceOrNull(null) == null → use entryPrice.
        ViopPosition due = openPosition(ViopDirection.SHORT, "1", "12000");
        due.setMaturityDate(LocalDate.now());
        when(positionRepo.findByStatusAndMaturityDateLessThanEqual(eq(ViopPositionStatus.OPEN), any(LocalDate.class)))
                .thenReturn(List.of(due));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.empty());
        when(positionRepo.save(any(ViopPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any(ViopTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        // entry == price → realized 0 (settle at entry).
        when(calc.realizedPnl(eq(ViopDirection.SHORT), eq(bd("12000")), eq(bd("12000")), any(), eq(bd("1"))))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(calc.positionSize(any(), any(), any())).thenReturn(bd("120000.00"));

        int n = service.expireDuePositions();

        assertThat(n).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(ViopPositionStatus.EXPIRED);
        verify(calc).realizedPnl(eq(ViopDirection.SHORT), eq(bd("12000")), eq(bd("12000")), any(), eq(bd("1")));
    }

    // ──────────────────────────── list() ────────────────────────────────────

    @Test
    void list_returns_empty_when_no_positions() {
        when(positionRepo.findByUserIdOrderByOpenedAtDesc(USER)).thenReturn(List.of());
        assertThat(service.list(USER)).isEmpty();
    }

    @Test
    void list_maps_positions_to_views_with_unrealized_pnl() {
        ViopContract c = liveContract();
        ViopPosition p = openPosition(ViopDirection.LONG, "2", "12000");
        when(positionRepo.findByUserIdOrderByOpenedAtDesc(USER)).thenReturn(List.of(p));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(calc.positionSize(any(), any(), any())).thenReturn(bd("240000.00"));
        when(calc.leverage(any(), any())).thenReturn(bd("10.00"));
        when(calc.unrealizedPnl(eq(ViopDirection.LONG), eq(bd("12000")), eq(bd("12000")), eq(bd("10")), eq(bd("2"))))
                .thenReturn(bd("0.00"));

        List<ViopPositionView> views = service.list(USER);

        assertThat(views).hasSize(1);
        ViopPositionView v = views.get(0);
        assertThat(v.contractSymbol()).isEqualTo(SYMBOL);
        assertThat(v.direction()).isEqualTo("LONG");
        assertThat(v.currentPrice()).isEqualByComparingTo("12000");
        assertThat(v.unrealizedPnl()).isEqualByComparingTo("0.00");
        assertThat(v.status()).isEqualTo("OPEN");
    }

    @Test
    void list_view_unrealized_is_zero_when_position_closed() {
        ViopContract c = liveContract();
        ViopPosition p = openPosition(ViopDirection.LONG, "0", "12000");
        p.setStatus(ViopPositionStatus.CLOSED);
        when(positionRepo.findByUserIdOrderByOpenedAtDesc(USER)).thenReturn(List.of(p));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(calc.positionSize(any(), any(), any())).thenReturn(bd("0.00"));
        when(calc.leverage(any(), any())).thenReturn(null);

        List<ViopPositionView> views = service.list(USER);

        // unrealized P&L short-circuits to ZERO for non-OPEN status (calc not called).
        assertThat(views.get(0).unrealizedPnl()).isEqualByComparingTo("0");
        verify(calc, never()).unrealizedPnl(any(), any(), any(), any(), any());
    }

    @Test
    void list_view_unrealized_is_zero_when_current_price_null() {
        ViopContract c = liveContract();
        c.setLastPrice(null);
        ViopPosition p = openPosition(ViopDirection.LONG, "2", "12000");
        when(positionRepo.findByUserIdOrderByOpenedAtDesc(USER)).thenReturn(List.of(p));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(marketQuoteRepo.findTop1ByInstrument_SymbolOrderByAsOfDesc(SYMBOL)).thenReturn(Optional.empty());
        when(calc.positionSize(any(), any(), any())).thenReturn(bd("240000.00"));
        when(calc.leverage(any(), any())).thenReturn(bd("10.00"));

        List<ViopPositionView> views = service.list(USER);

        assertThat(views.get(0).currentPrice()).isNull();
        assertThat(views.get(0).unrealizedPnl()).isEqualByComparingTo("0");
        verify(calc, never()).unrealizedPnl(any(), any(), any(), any(), any());
    }

    // ─────────────────────────── transactions() ─────────────────────────────

    @Test
    void transactions_maps_txns_to_views() {
        ViopTransaction t = new ViopTransaction();
        ReflectionTestUtils.setField(t, "id", 7L);
        t.setUserId(USER);
        t.setContractSymbol(SYMBOL);
        t.setType(ViopTransactionType.LONG_OPEN);
        t.setQuantity(bd("2"));
        t.setPrice(bd("12000"));
        t.setContractSize(bd("10"));
        t.setPositionSize(bd("240000.00"));
        t.setRealizedPnl(bd("0.00"));
        t.setExecutedAt(Instant.now());
        t.setNote("ilk açılış");
        when(txnRepo.findByUserIdOrderByExecutedAtDesc(USER)).thenReturn(List.of(t));

        List<ViopTransactionView> views = service.transactions(USER);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).type()).isEqualTo("LONG_OPEN");
        assertThat(views.get(0).contractSymbol()).isEqualTo(SYMBOL);
        assertThat(views.get(0).note()).isEqualTo("ilk açılış");
    }

    @Test
    void transactions_returns_empty_when_none() {
        when(txnRepo.findByUserIdOrderByExecutedAtDesc(USER)).thenReturn(List.of());
        assertThat(service.transactions(USER)).isEmpty();
    }

    // ─────────────────────────────── summary() ──────────────────────────────

    @Test
    void summary_zero_when_no_positions() {
        when(positionRepo.findByUserIdOrderByOpenedAtDesc(USER)).thenReturn(List.of());

        ViopSummary s = service.summary(USER);

        assertThat(s.openPositionCount()).isZero();
        assertThat(s.totalOpenPositionSize()).isEqualByComparingTo("0.00");
        assertThat(s.totalRequiredMargin()).isEqualByComparingTo("0.00");
        assertThat(s.totalUnrealizedPnl()).isEqualByComparingTo("0.00");
        assertThat(s.totalRealizedPnl()).isEqualByComparingTo("0.00");
    }

    @Test
    void summary_aggregates_open_and_includes_closed_realized_pnl() {
        ViopContract c = liveContract();
        ViopPosition open = openPosition(ViopDirection.LONG, "2", "12000");
        open.setRealizedPnl(bd("100.00"));
        open.setRequiredMargin(bd("24000.00"));
        ViopPosition closed = openPosition(ViopDirection.SHORT, "0", "11000");
        closed.setStatus(ViopPositionStatus.CLOSED);
        closed.setRealizedPnl(bd("500.00"));

        when(positionRepo.findByUserIdOrderByOpenedAtDesc(USER)).thenReturn(List.of(open, closed));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        // Only the OPEN row is rendered into a view for size/margin/unreal aggregation.
        when(calc.positionSize(any(), any(), any())).thenReturn(bd("240000.00"));
        when(calc.leverage(any(), any())).thenReturn(bd("10.00"));
        when(calc.unrealizedPnl(eq(ViopDirection.LONG), any(), any(), any(), any())).thenReturn(bd("600.00"));

        ViopSummary s = service.summary(USER);

        assertThat(s.openPositionCount()).isEqualTo(1);
        assertThat(s.totalOpenPositionSize()).isEqualByComparingTo("240000.00");
        assertThat(s.totalRequiredMargin()).isEqualByComparingTo("24000.00");
        assertThat(s.totalUnrealizedPnl()).isEqualByComparingTo("600.00");
        // Realized is summed across ALL rows (open + closed).
        assertThat(s.totalRealizedPnl()).isEqualByComparingTo("600.00");
    }

    @Test
    void summary_skips_open_view_metrics_that_are_null() {
        ViopContract c = liveContract();
        ViopPosition open = openPosition(ViopDirection.LONG, "2", "12000");
        open.setRealizedPnl(bd("0.00"));
        open.setRequiredMargin(null); // requiredMargin null → not added
        when(positionRepo.findByUserIdOrderByOpenedAtDesc(USER)).thenReturn(List.of(open));
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(calc.positionSize(any(), any(), any())).thenReturn(bd("240000.00"));
        when(calc.leverage(any(), any())).thenReturn(null);
        when(calc.unrealizedPnl(any(), any(), any(), any(), any())).thenReturn(bd("0.00"));

        ViopSummary s = service.summary(USER);

        assertThat(s.openPositionCount()).isEqualTo(1);
        assertThat(s.totalRequiredMargin()).isEqualByComparingTo("0.00");
        assertThat(s.totalOpenPositionSize()).isEqualByComparingTo("240000.00");
    }

    // ─────────────────────────────── preview() ──────────────────────────────

    @Test
    void preview_throws_notFound_when_contract_missing() {
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(USER, SYMBOL, ViopDirection.LONG, bd("2"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).isEqualTo("Kontrat bulunamadı");
                });
    }

    @Test
    void preview_computes_metrics_without_persisting() {
        ViopContract c = liveContract();
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.empty());
        when(calc.positionSize(eq(bd("12000")), eq(bd("10")), eq(bd("2")))).thenReturn(bd("240000.00"));
        when(calc.requiredMargin(eq(bd("240000.00")), any(), any(), eq(bd("2")))).thenReturn(bd("24000.00"));
        when(calc.leverage(eq(bd("240000.00")), eq(bd("24000.00")))).thenReturn(bd("10.00"));

        ViopPreviewResult r = service.preview(USER, SYMBOL, ViopDirection.LONG, bd("2"), null);

        assertThat(r.contractSymbol()).isEqualTo(SYMBOL);
        assertThat(r.direction()).isEqualTo("LONG");
        assertThat(r.quantity()).isEqualByComparingTo("2");
        assertThat(r.price()).isEqualByComparingTo("12000");
        assertThat(r.contractSize()).isEqualByComparingTo("10");
        assertThat(r.currency()).isEqualTo("TRY");
        assertThat(r.positionSize()).isEqualByComparingTo("240000.00");
        assertThat(r.requiredMargin()).isEqualByComparingTo("24000.00");
        assertThat(r.leverage()).isEqualByComparingTo("10.00");
        assertThat(r.willCloseOpposite()).isFalse();
        assertThat(r.note()).isNull();
        verify(positionRepo, never()).save(any());
    }

    @Test
    void preview_defaults_quantity_to_one_when_null() {
        ViopContract c = liveContract();
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.empty());
        when(calc.positionSize(eq(bd("12000")), eq(bd("10")), eq(BigDecimal.ONE))).thenReturn(bd("120000.00"));
        when(calc.requiredMargin(any(), any(), any(), eq(BigDecimal.ONE))).thenReturn(bd("12000.00"));
        when(calc.leverage(any(), any())).thenReturn(bd("10.00"));

        ViopPreviewResult r = service.preview(USER, SYMBOL, ViopDirection.SHORT, null, null);

        assertThat(r.quantity()).isEqualByComparingTo("1");
        assertThat(r.direction()).isEqualTo("SHORT");
    }

    @Test
    void preview_flags_will_close_opposite_when_existing_opposite_open() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "3", "12000");
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(calc.positionSize(any(), any(), any())).thenReturn(bd("120000.00"));
        when(calc.requiredMargin(any(), any(), any(), any())).thenReturn(bd("12000.00"));
        when(calc.leverage(any(), any())).thenReturn(bd("10.00"));

        // Requesting SHORT against an open LONG → willCloseOpposite true.
        ViopPreviewResult r = service.preview(USER, SYMBOL, ViopDirection.SHORT, bd("1"), bd("12000"));

        assertThat(r.willCloseOpposite()).isTrue();
        assertThat(r.note()).isEqualTo("Bu işlem önce ters yöndeki açık pozisyonu kapatır");
    }

    @Test
    void preview_does_not_flag_when_same_direction_existing() {
        ViopContract c = liveContract();
        ViopPosition existing = openPosition(ViopDirection.LONG, "3", "12000");
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.of(existing));
        when(calc.positionSize(any(), any(), any())).thenReturn(bd("120000.00"));
        when(calc.requiredMargin(any(), any(), any(), any())).thenReturn(bd("12000.00"));
        when(calc.leverage(any(), any())).thenReturn(bd("10.00"));

        ViopPreviewResult r = service.preview(USER, SYMBOL, ViopDirection.LONG, bd("1"), bd("12000"));

        assertThat(r.willCloseOpposite()).isFalse();
        assertThat(r.note()).isNull();
    }

    @Test
    void preview_handles_null_direction_name() {
        ViopContract c = liveContract();
        when(contractRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(c));
        when(positionRepo.findByUserIdAndContractSymbol(USER, SYMBOL)).thenReturn(Optional.empty());
        when(calc.positionSize(any(), any(), any())).thenReturn(bd("120000.00"));
        when(calc.requiredMargin(any(), any(), any(), any())).thenReturn(bd("12000.00"));
        when(calc.leverage(any(), any())).thenReturn(bd("10.00"));

        ViopPreviewResult r = service.preview(USER, SYMBOL, null, bd("1"), bd("12000"));

        assertThat(r.direction()).isNull();
        assertThat(r.willCloseOpposite()).isFalse();
    }
}
