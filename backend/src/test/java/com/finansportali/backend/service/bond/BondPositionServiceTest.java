package com.finansportali.backend.service.bond;

import com.finansportali.backend.dto.response.bond.BondPortfolioSummary;
import com.finansportali.backend.dto.response.bond.BondPositionTransactionView;
import com.finansportali.backend.dto.response.bond.BondPositionView;
import com.finansportali.backend.dto.response.bond.BondTradePreview;
import com.finansportali.backend.dto.response.bond.BondTradeResult;
import com.finansportali.backend.entity.BondPosition;
import com.finansportali.backend.entity.BondPositionStatus;
import com.finansportali.backend.entity.BondTransaction;
import com.finansportali.backend.entity.BondTransactionType;
import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentQuote;
import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.repository.BondPositionRepository;
import com.finansportali.backend.repository.BondTransactionRepository;
import com.finansportali.backend.repository.DebtInstrumentQuoteRepository;
import com.finansportali.backend.repository.DebtInstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioural coverage for {@link BondPositionService}: buy (new vs weighted-average merge),
 * sell (partial vs full close, proportional cost + realized P&L), scheduled coupon/maturity
 * processing, reads (list/transactions/summary) and previews — including every validation path.
 */
@ExtendWith(MockitoExtension.class)
class BondPositionServiceTest {

    private static final String USER = "user-1";
    private static final String ISIN = "TR-ISIN-1";
    private static final String SYMBOL = "TR2YT";

    @Mock private BondPositionRepository positionRepo;
    @Mock private BondTransactionRepository txnRepo;
    @Mock private DebtInstrumentRepository instrumentRepo;
    @Mock private DebtInstrumentQuoteRepository quoteRepo;

    // Real calculation service — exercises the math the production code relies on.
    private final BondCalculationService calc = new BondCalculationService();

    private BondPositionService service;

    @BeforeEach
    void setUp() {
        service = new BondPositionService(positionRepo, txnRepo, instrumentRepo, quoteRepo, calc);
        ReflectionTestUtils.setField(service, "defaultCouponFrequency", 2);
        ReflectionTestUtils.setField(service, "couponTaxRate", new BigDecimal("0"));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    /** Build a ledger transaction (summary reads lifetime realized P&L / coupon
     *  income from these, not from the position rows). */
    private static BondTransaction txn(BondTransactionType type, BigDecimal realizedPnl, BigDecimal netCoupon) {
        BondTransaction t = new BondTransaction();
        t.setType(type);
        t.setRealizedPnl(realizedPnl);
        t.setNetCoupon(netCoupon);
        return t;
    }

    @Test
    void redeem_books_pull_to_par_realized_pnl_for_discount_position() {
        BondPosition pos = activePosition(bd("100000"), bd("97000.00")); // bought below par
        pos.setRealizedPnl(BigDecimal.ZERO);
        pos.setCouponRate(BigDecimal.ZERO);   // zero-coupon → no coupons, only redemption
        pos.setCouponFrequency(0);
        pos.setMaturityDate(LocalDate.now()); // matures today
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));

        int events = service.processCouponsAndMaturities();

        assertThat(events).isEqualTo(1);
        assertThat(pos.getStatus()).isEqualTo(BondPositionStatus.MATURED);
        // par 100000 − cost 97000 = 3000 realized gain (pull-to-par)
        assertThat(pos.getRealizedPnl()).isEqualByComparingTo("3000.00");
        ArgumentCaptor<BondTransaction> cap = ArgumentCaptor.forClass(BondTransaction.class);
        verify(txnRepo).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(BondTransactionType.BOND_REDEMPTION);
        assertThat(cap.getValue().getRealizedPnl()).isEqualByComparingTo("3000.00");
    }

    private DebtInstrument instrument(LocalDate maturity, BigDecimal couponRate) {
        DebtInstrument inst = new DebtInstrument(SYMBOL, "2Y Bond", DebtInstrumentType.GOVERNMENT_BOND);
        inst.setId(10L);
        inst.setIsin(ISIN);
        inst.setIssuer("Hazine");
        inst.setCurrency("TRY");
        inst.setMaturityDate(maturity);
        inst.setCouponRate(couponRate);
        return inst;
    }

    private BondPosition activePosition(BigDecimal nominal, BigDecimal totalCost) {
        BondPosition p = new BondPosition();
        p.setUserId(USER);
        p.setIsin(ISIN);
        p.setSymbol(SYMBOL);
        p.setName("2Y Bond");
        p.setType(DebtInstrumentType.GOVERNMENT_BOND);
        p.setIssuer("Hazine");
        p.setCurrency("TRY");
        p.setRemainingNominal(nominal);
        p.setTotalCost(totalCost);
        p.setAvgCostPrice(calc.weightedAvgPrice(totalCost, nominal));
        p.setMaturityDate(LocalDate.now().plusYears(2));
        p.setPurchaseDate(LocalDate.now().minusDays(10));
        p.setStatus(BondPositionStatus.ACTIVE);
        return p;
    }

    /** Echoes saved entities back so the service's post-save reads work. */
    private void stubSaves() {
        lenient().when(positionRepo.save(any(BondPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(txnRepo.save(any(BondTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Makes currentPrices(...) resolve a price so toView() computes currentValue / unrealizedPnl. */
    private void stubLatestQuote(DebtInstrument inst, BigDecimal clean, BigDecimal dirty, LocalDate quoteDate) {
        DebtInstrumentQuote q = new DebtInstrumentQuote();
        q.setCleanPrice(clean);
        q.setDirtyPrice(dirty);
        q.setPrice(clean);
        q.setYieldRate(bd("12"));
        q.setQuoteDate(quoteDate);
        lenient().when(quoteRepo.findLatestByInstrument(inst)).thenReturn(Optional.of(q));
    }

    // ── resolveInstrument validation (via buy/preview) ───────────────────────────

    @Test
    void buy_rejects_blank_identifier() {
        assertThatThrownBy(() -> service.buy(USER, "  ", bd("1000"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Enstrüman belirtilmedi");
                });
    }

    @Test
    void buy_rejects_null_identifier() {
        assertThatThrownBy(() -> service.buy(USER, null, bd("1000"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void buy_rejects_unknown_instrument() {
        when(instrumentRepo.findByIsin("NOPE")).thenReturn(Optional.empty());
        when(instrumentRepo.findBySymbol("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buy(USER, "NOPE", bd("1000"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resolveInstrument_falls_back_to_symbol_when_isin_missing() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(SYMBOL)).thenReturn(Optional.empty());
        when(instrumentRepo.findBySymbol(SYMBOL)).thenReturn(Optional.of(inst));
        stubLatestQuote(inst, bd("99"), bd("100"), LocalDate.now());
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        stubSaves();

        BondTradeResult res = service.buy(USER, SYMBOL, bd("1000"), bd("99"), bd("1"), null);
        assertThat(res.position().isin()).isEqualTo(ISIN);
    }

    // ── buy validations ──────────────────────────────────────────────────────────

    @Test
    void buy_rejects_matured_instrument() {
        DebtInstrument inst = instrument(LocalDate.now().minusDays(1), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        assertThatThrownBy(() -> service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("Vadesi geçmiş enstrümanda yeni alış yapılamaz"));
    }

    @Test
    void buy_rejects_maturity_today() {
        DebtInstrument inst = instrument(LocalDate.now(), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        assertThatThrownBy(() -> service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void buy_allows_null_maturity() {
        DebtInstrument inst = instrument(null, bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        stubLatestQuote(inst, bd("99"), bd("100"), LocalDate.now());
        stubSaves();

        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null);
        // null maturity → couponFrequency uses defaultCouponFrequency (rate > 0) but no schedule
        assertThat(res.position().maturityDate()).isNull();
    }

    @Test
    void buy_rejects_null_nominal() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        assertThatThrownBy(() -> service.buy(USER, ISIN, null, bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("Nominal tutar sıfırdan büyük olmalı"));
    }

    @Test
    void buy_rejects_zero_nominal() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        assertThatThrownBy(() -> service.buy(USER, ISIN, BigDecimal.ZERO, bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void buy_rejects_non_positive_price() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        // clean null + accrued null → dirtyFrom returns null → price error
        assertThatThrownBy(() -> service.buy(USER, ISIN, bd("1000"), null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("Fiyat sıfırdan büyük olmalı"));
    }

    @Test
    void buy_zero_dirty_override_falls_back_to_clean_plus_accrued() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        stubLatestQuote(inst, bd("99.00"), bd("100.00"), LocalDate.now());
        stubSaves();

        // dirtyOverride = 0 is non-positive → ignored; dirty = clean(99) + accrued(1) = 100
        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), BigDecimal.ZERO);

        assertThat(res.transaction().dirtyPrice()).isEqualByComparingTo("100");
    }

    // ── buy happy paths ──────────────────────────────────────────────────────────

    @Test
    void buy_creates_new_position_from_clean_plus_accrued() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        stubLatestQuote(inst, bd("99.00"), bd("100.00"), LocalDate.now());
        stubSaves();

        // dirty = 96.50 + 1.20 = 97.70 ; cost = 100000 * 97.70/100 = 97700.00
        BondTradeResult res = service.buy(USER, ISIN, bd("100000"), bd("96.50"), bd("1.20"), null);

        assertThat(res.message()).isEqualTo("Alış kaydedildi (simülasyon)");
        assertThat(res.transaction().type()).isEqualTo("BOND_BUY");
        assertThat(res.transaction().dirtyPrice()).isEqualByComparingTo("97.70");
        assertThat(res.transaction().grossAmount()).isEqualByComparingTo("97700.00");
        assertThat(res.position().remainingNominal()).isEqualByComparingTo("100000");
        assertThat(res.position().remainingCost()).isEqualByComparingTo("97700.00");
        assertThat(res.position().status()).isEqualTo("ACTIVE");
        assertThat(res.position().couponFrequency()).isEqualTo(2);

        ArgumentCaptor<BondPosition> cap = ArgumentCaptor.forClass(BondPosition.class);
        verify(positionRepo).save(cap.capture());
        assertThat(cap.getValue().getPurchaseDate()).isEqualTo(LocalDate.now());
        assertThat(cap.getValue().getLastCouponDate()).isEqualTo(LocalDate.now());
        verify(txnRepo).save(any(BondTransaction.class));
    }

    @Test
    void buy_uses_dirty_override_when_positive() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), bd("98.00"));
        assertThat(res.transaction().dirtyPrice()).isEqualByComparingTo("98.00");
        // cost = 1000 * 98/100 = 980.00
        assertThat(res.transaction().grossAmount()).isEqualByComparingTo("980.00");
    }

    @Test
    void buy_zero_coupon_instrument_sets_frequency_zero() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(1), BigDecimal.ZERO);
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null);
        assertThat(res.position().couponFrequency()).isZero();
    }

    @Test
    void buy_defaults_currency_to_TRY_when_instrument_currency_null() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        inst.setCurrency(null);
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null);
        assertThat(res.position().currency()).isEqualTo("TRY");
    }

    @Test
    void buy_merges_into_active_position_with_weighted_average() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        // existing: 100000 nominal @ cost 97700
        BondPosition existing = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(existing));
        stubLatestQuote(inst, bd("99.00"), bd("100.00"), LocalDate.now());
        stubSaves();

        // second buy: 50000 @ dirty 98.50 → cost = 50000*98.50/100 = 49250.00
        BondTradeResult res = service.buy(USER, ISIN, bd("50000"), bd("98.50"), BigDecimal.ZERO, null);

        // merged nominal 150000, cost 146950.00, avg = 146950*100/150000 = 97.9667
        assertThat(res.position().remainingNominal()).isEqualByComparingTo("150000");
        assertThat(res.position().remainingCost()).isEqualByComparingTo("146950.00");
        assertThat(res.position().avgCostPrice()).isEqualByComparingTo("97.9667");
        assertThat(existing.getRemainingNominal()).isEqualByComparingTo("150000");
    }

    @Test
    void buy_reactivates_sold_position_as_new() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        BondPosition sold = activePosition(BigDecimal.ZERO, BigDecimal.ZERO);
        sold.setStatus(BondPositionStatus.SOLD);
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(sold));
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null);
        // re-initialised as a fresh ACTIVE position on the same row
        assertThat(res.position().status()).isEqualTo("ACTIVE");
        assertThat(res.position().remainingNominal()).isEqualByComparingTo("1000");
        assertThat(sold.getStatus()).isEqualTo(BondPositionStatus.ACTIVE);
    }

    // ── sell validations ─────────────────────────────────────────────────────────

    @Test
    void sell_rejects_when_no_active_position() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.sell(USER, ISIN, bd("1000"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("Aktif pozisyon bulunamadı"));
    }

    @Test
    void sell_rejects_when_position_not_active() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        BondPosition matured = activePosition(bd("1000"), bd("980"));
        matured.setStatus(BondPositionStatus.MATURED);
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(matured));
        assertThatThrownBy(() -> service.sell(USER, ISIN, bd("500"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sell_rejects_null_nominal() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN))
                .thenReturn(Optional.of(activePosition(bd("1000"), bd("980"))));
        assertThatThrownBy(() -> service.sell(USER, ISIN, null, bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("Satış nominali sıfırdan büyük olmalı"));
    }

    @Test
    void sell_rejects_non_positive_nominal() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN))
                .thenReturn(Optional.of(activePosition(bd("1000"), bd("980"))));
        assertThatThrownBy(() -> service.sell(USER, ISIN, bd("-5"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sell_rejects_over_sell() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN))
                .thenReturn(Optional.of(activePosition(bd("1000"), bd("980"))));
        assertThatThrownBy(() -> service.sell(USER, ISIN, bd("2000"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("Satış nominali eldeki nominalden fazla olamaz"));
    }

    @Test
    void sell_rejects_non_positive_price() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN))
                .thenReturn(Optional.of(activePosition(bd("1000"), bd("980"))));
        assertThatThrownBy(() -> service.sell(USER, ISIN, bd("500"), null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("Fiyat sıfırdan büyük olmalı"));
    }

    // ── sell happy paths ─────────────────────────────────────────────────────────

    @Test
    void sell_partial_keeps_position_active_with_proportional_cost_and_realized_pnl() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(pos));
        stubLatestQuote(inst, bd("99.00"), bd("99.00"), LocalDate.now());
        stubSaves();

        // sell 40000 @ dirty 99.00 → proceeds 39600.00; propCost 97700*40000/100000 = 39080.00 ; realized 520.00
        BondTradeResult res = service.sell(USER, ISIN, bd("40000"), bd("99.00"), BigDecimal.ZERO, null);

        assertThat(res.message()).isEqualTo("Satış kaydedildi (simülasyon)");
        assertThat(res.transaction().type()).isEqualTo("BOND_SELL");
        assertThat(res.transaction().grossAmount()).isEqualByComparingTo("39600.00");
        assertThat(res.transaction().realizedPnl()).isEqualByComparingTo("520.00");
        assertThat(res.position().status()).isEqualTo("ACTIVE");
        assertThat(res.position().remainingNominal()).isEqualByComparingTo("60000");
        assertThat(res.position().remainingCost()).isEqualByComparingTo("58620.00");
        assertThat(res.position().realizedPnl()).isEqualByComparingTo("520.00");
        assertThat(pos.getStatus()).isEqualTo(BondPositionStatus.ACTIVE);
    }

    @Test
    void sell_full_closes_position_and_zeroes_cost() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(pos));
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        // sell all 100000 @ dirty 99.00 → proceeds 99000.00; propCost 97700.00 → realized 1300.00
        BondTradeResult res = service.sell(USER, ISIN, bd("100000"), bd("99.00"), BigDecimal.ZERO, null);

        assertThat(res.position().status()).isEqualTo("SOLD");
        assertThat(res.position().remainingNominal()).isEqualByComparingTo("0");
        assertThat(res.position().remainingCost()).isEqualByComparingTo("0");
        assertThat(res.transaction().realizedPnl()).isEqualByComparingTo("1300.00");
        assertThat(pos.getStatus()).isEqualTo(BondPositionStatus.SOLD);
    }

    @Test
    void sell_uses_dirty_override_when_positive() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        BondPosition pos = activePosition(bd("1000"), bd("980.00"));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(pos));
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        // override 100.00 → proceeds 1000.00 ; propCost 980.00 ; realized 20.00
        BondTradeResult res = service.sell(USER, ISIN, bd("1000"), bd("50"), bd("1"), bd("100.00"));
        assertThat(res.transaction().dirtyPrice()).isEqualByComparingTo("100.00");
        assertThat(res.transaction().realizedPnl()).isEqualByComparingTo("20.00");
    }

    // ── processCouponsAndMaturities ──────────────────────────────────────────────

    @Test
    void process_returns_zero_when_no_active_positions() {
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of());
        assertThat(service.processCouponsAndMaturities()).isZero();
        verify(positionRepo, never()).save(any());
    }

    @Test
    void process_skips_coupons_when_rate_zero_and_not_matured() {
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(BigDecimal.ZERO);
        pos.setCouponFrequency(0);
        pos.setMaturityDate(LocalDate.now().plusYears(5));
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        assertThat(service.processCouponsAndMaturities()).isZero();
        verify(txnRepo, never()).save(any());
        verify(positionRepo).save(pos); // still persisted (updatedAt bump)
    }

    @Test
    void process_skips_coupons_when_frequency_null() {
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(bd("10"));
        pos.setCouponFrequency(null);
        pos.setMaturityDate(LocalDate.now().plusYears(5));
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        assertThat(service.processCouponsAndMaturities()).isZero();
        verify(txnRepo, never()).save(any());
    }

    @Test
    void process_pays_one_due_coupon() {
        // Maturity 3 months out, semi-annual → schedule includes a date between
        // purchase (lastCouponDate) and today.
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(bd("20"));
        pos.setCouponFrequency(2);
        pos.setMaturityDate(LocalDate.now().plusMonths(3));     // next coupon = maturity
        pos.setPurchaseDate(LocalDate.now().minusMonths(4));
        pos.setLastCouponDate(LocalDate.now().minusMonths(4));
        // a coupon date 3 months back (maturity - 6 months) is after lastCoupon and <= today
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        int events = service.processCouponsAndMaturities();
        assertThat(events).isGreaterThanOrEqualTo(1);
        // coupon = 100000 * 20/100 / 2 = 10000.00, tax 0 → net 10000.00
        assertThat(pos.getCouponIncome()).isEqualByComparingTo("10000.00");

        ArgumentCaptor<BondTransaction> cap = ArgumentCaptor.forClass(BondTransaction.class);
        verify(txnRepo, times(events)).save(cap.capture());
        BondTransaction coupon = cap.getAllValues().get(0);
        assertThat(coupon.getType()).isEqualTo(BondTransactionType.BOND_COUPON_PAYMENT);
        assertThat(coupon.getGrossCoupon()).isEqualByComparingTo("10000.00");
        assertThat(coupon.getNetCoupon()).isEqualByComparingTo("10000.00");
        assertThat(coupon.getTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(coupon.getNote()).startsWith("Kupon ödemesi");
    }

    @Test
    void process_applies_coupon_tax() {
        ReflectionTestUtils.setField(service, "couponTaxRate", new BigDecimal("0.10"));
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(bd("20"));
        pos.setCouponFrequency(2);
        pos.setMaturityDate(LocalDate.now().plusMonths(3));
        pos.setPurchaseDate(LocalDate.now().minusMonths(4));
        pos.setLastCouponDate(LocalDate.now().minusMonths(4));
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        service.processCouponsAndMaturities();
        // gross 10000.00, tax 10% = 1000.00, net 9000.00
        assertThat(pos.getCouponIncome()).isEqualByComparingTo("9000.00");
    }

    @Test
    void process_redeems_matured_position() {
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(BigDecimal.ZERO);     // zero-coupon bill, no coupon leg
        pos.setCouponFrequency(0);
        pos.setMaturityDate(LocalDate.now().minusDays(1));   // matured
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        int events = service.processCouponsAndMaturities();
        assertThat(events).isEqualTo(1);
        assertThat(pos.getStatus()).isEqualTo(BondPositionStatus.MATURED);
        assertThat(pos.getRemainingNominal()).isEqualByComparingTo("0");
        assertThat(pos.getTotalCost()).isEqualByComparingTo("0");

        ArgumentCaptor<BondTransaction> cap = ArgumentCaptor.forClass(BondTransaction.class);
        verify(txnRepo).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(BondTransactionType.BOND_REDEMPTION);
        assertThat(cap.getValue().getGrossAmount()).isEqualByComparingTo("100000");
        assertThat(cap.getValue().getNote()).startsWith("İtfa");
    }

    @Test
    void process_does_not_redeem_future_maturity() {
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(BigDecimal.ZERO);
        pos.setCouponFrequency(0);
        pos.setMaturityDate(LocalDate.now().plusYears(1));
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        assertThat(service.processCouponsAndMaturities()).isZero();
        assertThat(pos.getStatus()).isEqualTo(BondPositionStatus.ACTIVE);
    }

    @Test
    void process_does_not_redeem_when_maturity_null() {
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(BigDecimal.ZERO);
        pos.setCouponFrequency(0);
        pos.setMaturityDate(null);
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        assertThat(service.processCouponsAndMaturities()).isZero();
        assertThat(pos.getStatus()).isEqualTo(BondPositionStatus.ACTIVE);
    }

    // ── list / transactions ──────────────────────────────────────────────────────

    @Test
    void list_maps_positions_to_views() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        stubLatestQuote(inst, bd("99.00"), bd("99.20"), LocalDate.now());

        List<BondPositionView> views = service.list(USER);
        assertThat(views).hasSize(1);
        BondPositionView v = views.get(0);
        assertThat(v.isin()).isEqualTo(ISIN);
        assertThat(v.type()).isEqualTo("GOVERNMENT_BOND");
        // currentValue = 100000 * 99.20/100 = 99200.00 ; unrealized = 99200 - 97700 = 1500.00
        assertThat(v.currentValue()).isEqualByComparingTo("99200.00");
        assertThat(v.unrealizedPnl()).isEqualByComparingTo("1500.00");
        assertThat(v.priceIsStale()).isFalse();
    }

    @Test
    void list_returns_empty_when_no_positions() {
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of());
        assertThat(service.list(USER)).isEmpty();
    }

    @Test
    void list_view_has_null_value_when_no_quote() {
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.empty());
        when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.empty());

        BondPositionView v = service.list(USER).get(0);
        assertThat(v.currentValue()).isNull();
        // ACTIVE but currentValue null → unrealized falls to ZERO
        assertThat(v.unrealizedPnl()).isEqualByComparingTo("0");
        assertThat(v.priceIsStale()).isFalse();
    }

    @Test
    void list_view_marks_stale_quote() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        stubLatestQuote(inst, bd("99.00"), bd("99.20"), LocalDate.now().minusDays(3)); // old

        BondPositionView v = service.list(USER).get(0);
        assertThat(v.priceIsStale()).isTrue();
    }

    @Test
    void list_view_falls_back_to_price_when_clean_and_dirty_null() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        DebtInstrumentQuote q = new DebtInstrumentQuote();
        q.setCleanPrice(null);
        q.setDirtyPrice(null);
        q.setPrice(bd("98.00"));
        q.setQuoteDate(LocalDate.now());
        lenient().when(quoteRepo.findLatestByInstrument(inst)).thenReturn(Optional.of(q));

        BondPositionView v = service.list(USER).get(0);
        // clean ← price (98.00), dirty ← clean (98.00) → value = 100000*98/100
        assertThat(v.currentCleanPrice()).isEqualByComparingTo("98.00");
        assertThat(v.currentValue()).isEqualByComparingTo("98000.00");
    }

    @Test
    void transactions_maps_to_views() {
        BondTransaction t = new BondTransaction();
        t.setUserId(USER);
        t.setIsin(ISIN);
        t.setType(BondTransactionType.BOND_BUY);
        t.setNominal(bd("1000"));
        t.setCleanPrice(bd("99"));
        t.setAccruedInterest(bd("1"));
        t.setDirtyPrice(bd("100"));
        t.setGrossAmount(bd("1000.00"));
        when(txnRepo.findByUserIdOrderByExecutedAtDesc(USER)).thenReturn(List.of(t));

        List<BondPositionTransactionView> views = service.transactions(USER);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).type()).isEqualTo("BOND_BUY");
        assertThat(views.get(0).isin()).isEqualTo(ISIN);
    }

    @Test
    void transactions_returns_empty() {
        when(txnRepo.findByUserIdOrderByExecutedAtDesc(USER)).thenReturn(List.of());
        assertThat(service.transactions(USER)).isEmpty();
    }

    // ── summary ──────────────────────────────────────────────────────────────────

    @Test
    void summary_empty_portfolio_returns_zeros() {
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of());
        BondPortfolioSummary s = service.summary(USER);
        assertThat(s.positionCount()).isZero();
        assertThat(s.totalNominal()).isEqualByComparingTo("0.00");
        assertThat(s.currentMarketValue()).isEqualByComparingTo("0.00");
        assertThat(s.upcomingMaturities()).isZero();
        assertThat(s.expectedNextCoupons()).isEqualByComparingTo("0.00");
        assertThat(s.realizedCouponIncome()).isEqualByComparingTo("0.00");
        assertThat(s.totalUnrealizedPnl()).isEqualByComparingTo("0.00");
        assertThat(s.totalRealizedPnl()).isEqualByComparingTo("0.00");
    }

    @Test
    void summary_aggregates_active_position_with_coupon_and_upcoming_maturity() {
        DebtInstrument inst = instrument(LocalDate.now().plusDays(10), bd("20"));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        pos.setCouponRate(bd("20"));
        pos.setCouponFrequency(2);
        pos.setMaturityDate(LocalDate.now().plusDays(10)); // within 30 days → upcoming
        pos.setRealizedPnl(bd("100.00"));
        pos.setCouponIncome(bd("50.00"));
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        when(txnRepo.findByUserIdOrderByExecutedAtDesc(USER)).thenReturn(List.of(
                txn(BondTransactionType.BOND_SELL, bd("100.00"), null),
                txn(BondTransactionType.BOND_COUPON_PAYMENT, null, bd("50.00"))));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        stubLatestQuote(inst, bd("99.00"), bd("99.20"), LocalDate.now());

        BondPortfolioSummary s = service.summary(USER);
        assertThat(s.positionCount()).isEqualTo(1);
        assertThat(s.totalNominal()).isEqualByComparingTo("100000.00");
        assertThat(s.currentMarketValue()).isEqualByComparingTo("99200.00");
        assertThat(s.upcomingMaturities()).isEqualTo(1);
        // expected coupon = 100000 * 20/100 / 2 = 10000.00
        assertThat(s.expectedNextCoupons()).isEqualByComparingTo("10000.00");
        assertThat(s.realizedCouponIncome()).isEqualByComparingTo("50.00");
        assertThat(s.totalUnrealizedPnl()).isEqualByComparingTo("1500.00");
        assertThat(s.totalRealizedPnl()).isEqualByComparingTo("100.00");
    }

    @Test
    void summary_includes_realized_from_non_active_but_excludes_from_active_metrics() {
        BondPosition sold = activePosition(bd("0"), bd("0"));
        sold.setStatus(BondPositionStatus.SOLD);
        sold.setRealizedPnl(bd("250.00"));
        sold.setCouponIncome(bd("30.00"));
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(sold));
        when(txnRepo.findByUserIdOrderByExecutedAtDesc(USER)).thenReturn(List.of(
                txn(BondTransactionType.BOND_SELL, bd("250.00"), null),
                txn(BondTransactionType.BOND_COUPON_PAYMENT, null, bd("30.00"))));
        // sold positions still routed through toView → currentPrices lookups
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.empty());
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.empty());

        BondPortfolioSummary s = service.summary(USER);
        assertThat(s.positionCount()).isZero(); // not ACTIVE
        assertThat(s.totalNominal()).isEqualByComparingTo("0.00");
        assertThat(s.totalRealizedPnl()).isEqualByComparingTo("250.00");
        assertThat(s.realizedCouponIncome()).isEqualByComparingTo("30.00");
    }

    @Test
    void summary_active_without_coupon_or_upcoming_maturity() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(5), BigDecimal.ZERO);
        BondPosition pos = activePosition(bd("50000"), bd("48000.00"));
        pos.setCouponRate(BigDecimal.ZERO);
        pos.setCouponFrequency(0);
        pos.setMaturityDate(LocalDate.now().plusYears(5)); // far out
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());

        BondPortfolioSummary s = service.summary(USER);
        assertThat(s.positionCount()).isEqualTo(1);
        assertThat(s.expectedNextCoupons()).isEqualByComparingTo("0.00");
        assertThat(s.upcomingMaturities()).isZero();
        // no quote → currentValue null → marketValue stays 0
        assertThat(s.currentMarketValue()).isEqualByComparingTo("0.00");
    }

    // ── previewBuy ───────────────────────────────────────────────────────────────

    @Test
    void previewBuy_computes_cost_from_clean_plus_accrued() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));

        BondTradePreview p = service.previewBuy(ISIN, bd("100000"), bd("96.50"), bd("1.20"), null);
        assertThat(p.side()).isEqualTo("BUY");
        assertThat(p.isin()).isEqualTo(ISIN);
        assertThat(p.dirtyPrice()).isEqualByComparingTo("97.70");
        assertThat(p.totalAmount()).isEqualByComparingTo("97700.00");
        assertThat(p.proportionalCost()).isNull();
        assertThat(p.estimatedRealizedPnl()).isNull();
    }

    @Test
    void previewBuy_treats_null_nominal_as_zero() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));

        BondTradePreview p = service.previewBuy(ISIN, null, bd("99"), bd("1"), null);
        assertThat(p.nominal()).isEqualByComparingTo("0");
        assertThat(p.totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void previewBuy_validates_instrument_existence() {
        when(instrumentRepo.findByIsin("NOPE")).thenReturn(Optional.empty());
        when(instrumentRepo.findBySymbol("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.previewBuy("NOPE", bd("1000"), bd("99"), bd("1"), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── previewSell ──────────────────────────────────────────────────────────────

    @Test
    void previewSell_with_active_position_estimates_pnl() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(pos));

        // sell preview 40000 @ dirty 99 → proceeds 39600.00; propCost 39080.00; est 520.00
        BondTradePreview p = service.previewSell(USER, ISIN, bd("40000"), bd("99.00"), BigDecimal.ZERO, null);
        assertThat(p.side()).isEqualTo("SELL");
        assertThat(p.isin()).isEqualTo(ISIN);
        assertThat(p.totalAmount()).isEqualByComparingTo("39600.00");
        assertThat(p.proportionalCost()).isEqualByComparingTo("39080.00");
        assertThat(p.estimatedRealizedPnl()).isEqualByComparingTo("520.00");
    }

    @Test
    void previewSell_without_position_has_null_cost_and_pnl() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());

        BondTradePreview p = service.previewSell(USER, ISIN, bd("40000"), bd("99.00"), BigDecimal.ZERO, null);
        assertThat(p.proportionalCost()).isNull();
        assertThat(p.estimatedRealizedPnl()).isNull();
        assertThat(p.totalAmount()).isEqualByComparingTo("39600.00");
    }

    @Test
    void previewSell_ignores_non_active_position() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        BondPosition sold = activePosition(bd("100000"), bd("97700.00"));
        sold.setStatus(BondPositionStatus.SOLD);
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(sold));

        BondTradePreview p = service.previewSell(USER, ISIN, bd("40000"), bd("99.00"), BigDecimal.ZERO, null);
        assertThat(p.proportionalCost()).isNull();
        assertThat(p.estimatedRealizedPnl()).isNull();
    }

    @Test
    void previewSell_null_nominal_defaults_to_zero() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(pos));

        BondTradePreview p = service.previewSell(USER, ISIN, null, bd("99.00"), BigDecimal.ZERO, null);
        assertThat(p.nominal()).isEqualByComparingTo("0");
        assertThat(p.totalAmount()).isEqualByComparingTo("0.00");
        // propCost = 97700 * 0 / 100000 = 0.00 ; est = 0 - 0 = 0.00
        assertThat(p.proportionalCost()).isEqualByComparingTo("0.00");
        assertThat(p.estimatedRealizedPnl()).isEqualByComparingTo("0.00");
    }
}
