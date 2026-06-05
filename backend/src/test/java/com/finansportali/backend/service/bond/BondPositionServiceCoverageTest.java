package com.finansportali.backend.service.bond;

import com.finansportali.backend.dto.response.bond.BondPortfolioSummary;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SUPPLEMENTAL branch coverage for {@link BondPositionService}, complementing
 * {@code BondPositionServiceTest}. Targets the conditional branches the primary
 * suite leaves unexercised: dirtyOverride negative (non-positive) branch,
 * coupon-rate null (vs zero) in {@code frequencyFor}, currency already set (vs
 * null) on buy, {@code toView} null-maturity → null days, non-ACTIVE position
 * holding a live quote (unrealized forced to ZERO), quote with a null quoteDate
 * (stale=false), high-frequency coupon schedule (monthsPer guard), coupon
 * schedule / due-coupon resolution when purchaseDate / lastCouponDate are null,
 * summary excluding a null-maturity active position from upcoming, and previews
 * driven by a positive dirtyOverride.
 */
@ExtendWith(MockitoExtension.class)
class BondPositionServiceCoverageTest {

    private static final String USER = "user-cov";
    private static final String ISIN = "TR-ISIN-COV";
    private static final String SYMBOL = "TRCOV";

    @Mock private BondPositionRepository positionRepo;
    @Mock private BondTransactionRepository txnRepo;
    @Mock private DebtInstrumentRepository instrumentRepo;
    @Mock private DebtInstrumentQuoteRepository quoteRepo;

    // Real calculation service — same convention as the primary test.
    private final BondCalculationService calc = new BondCalculationService();

    private BondPositionService service;

    @BeforeEach
    void setUp() {
        service = new BondPositionService(positionRepo, txnRepo, instrumentRepo, quoteRepo, calc);
        ReflectionTestUtils.setField(service, "defaultCouponFrequency", 2);
        ReflectionTestUtils.setField(service, "couponTaxRate", new BigDecimal("0"));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private DebtInstrument instrument(LocalDate maturity, BigDecimal couponRate) {
        DebtInstrument inst = new DebtInstrument(SYMBOL, "Cov Bond", DebtInstrumentType.GOVERNMENT_BOND);
        inst.setId(99L);
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
        p.setName("Cov Bond");
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

    /** Resolves a latest quote so toView() can compute currentValue / staleness. */
    private void stubLatestQuote(DebtInstrument inst, BigDecimal clean, BigDecimal dirty, LocalDate quoteDate) {
        DebtInstrumentQuote q = new DebtInstrumentQuote();
        q.setCleanPrice(clean);
        q.setDirtyPrice(dirty);
        q.setPrice(clean);
        q.setYieldRate(bd("12"));
        q.setQuoteDate(quoteDate);
        lenient().when(quoteRepo.findLatestByInstrument(inst)).thenReturn(Optional.of(q));
    }

    // ── dirtyPrice: negative override is non-positive → falls back to clean+accrued ──

    @Test
    void buy_negative_dirty_override_is_ignored_and_falls_back() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        // dirtyOverride = -5 → signum() <= 0 → ignored; dirty = clean(99) + accrued(1) = 100
        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), bd("-5"));
        assertThat(res.transaction().dirtyPrice()).isEqualByComparingTo("100");
    }

    // ── frequencyFor: couponRate == null (first operand of ||) → frequency 0 ─────────

    @Test
    void buy_null_coupon_rate_sets_frequency_zero() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), null);
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null);
        assertThat(res.position().couponFrequency()).isZero();
        assertThat(res.position().couponRate()).isNull();
    }

    // ── buy: instrument currency already set (non-null) → kept as-is (ternary true) ──

    @Test
    void buy_keeps_instrument_currency_when_present() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        inst.setCurrency("USD");
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.empty());
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null);
        assertThat(res.position().currency()).isEqualTo("USD");
    }

    // ── toView: null maturity → daysToMaturity null (ternary false side) ─────────────

    @Test
    void list_view_null_maturity_yields_null_days_to_maturity() {
        DebtInstrument inst = instrument(null, bd("10"));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        pos.setMaturityDate(null);
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        stubLatestQuote(inst, bd("99.00"), bd("99.20"), LocalDate.now());

        BondPositionView v = service.list(USER).get(0);
        assertThat(v.daysToMaturity()).isNull();
        assertThat(v.maturityDate()).isNull();
        assertThat(v.currentValue()).isEqualByComparingTo("99200.00");
    }

    // ── toView: non-ACTIVE position WITH a live quote → currentValue set but
    //            unrealized forced to ZERO (left side of && is false) ────────────────

    @Test
    void list_view_non_active_with_quote_has_value_but_zero_unrealized() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        BondPosition sold = activePosition(bd("100000"), bd("97700.00"));
        sold.setStatus(BondPositionStatus.SOLD);
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(sold));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        stubLatestQuote(inst, bd("99.00"), bd("99.20"), LocalDate.now());

        BondPositionView v = service.list(USER).get(0);
        assertThat(v.status()).isEqualTo("SOLD");
        assertThat(v.currentValue()).isEqualByComparingTo("99200.00"); // value still computed
        assertThat(v.unrealizedPnl()).isEqualByComparingTo("0");        // but not ACTIVE → ZERO
        assertThat(v.priceIsStale()).isFalse();
    }

    // ── currentPrices: quoteDate == null → stale=false (first operand false) ─────────

    @Test
    void list_view_null_quote_date_is_not_stale() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        stubLatestQuote(inst, bd("99.00"), bd("99.20"), null); // quoteDate null

        BondPositionView v = service.list(USER).get(0);
        assertThat(v.currentValue()).isEqualByComparingTo("99200.00");
        assertThat(v.priceIsStale()).isFalse();
    }

    // ── couponSchedule: frequency > 12 → monthsPer == 0 → empty schedule guard ───────

    @Test
    void process_high_frequency_yields_no_coupons_when_months_per_zero() {
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(bd("12"));
        pos.setCouponFrequency(24);                       // 12 / 24 = 0 → monthsPer <= 0
        pos.setMaturityDate(LocalDate.now().plusYears(1));
        pos.setPurchaseDate(LocalDate.now().minusMonths(6));
        pos.setLastCouponDate(LocalDate.now().minusMonths(6));
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        assertThat(service.processCouponsAndMaturities()).isZero();
        verify(txnRepo, never()).save(any());
        assertThat(pos.getCouponIncome()).isEqualByComparingTo("0");
    }

    // ── couponSchedule floor / payDueCoupons since: purchaseDate AND lastCouponDate
    //    both null → floor = maturity.minusYears(50); since == null so every past
    //    schedule date is paid (both null-branches of two ternaries / the && guard) ──

    @Test
    void process_pays_coupons_when_purchase_and_last_coupon_dates_null() {
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(bd("20"));
        pos.setCouponFrequency(2);
        pos.setMaturityDate(LocalDate.now().plusMonths(3)); // one schedule date 3 months back is <= today
        pos.setPurchaseDate(null);                          // floor fallback branch
        pos.setLastCouponDate(null);                        // since = purchaseDate(null) → null
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        int events = service.processCouponsAndMaturities();
        assertThat(events).isGreaterThanOrEqualTo(1);
        // at least the coupon dated maturity-6m (3 months ago) is paid: 100000*20/100/2 = 10000.00 each
        assertThat(pos.getCouponIncome()).isGreaterThanOrEqualTo(bd("10000.00"));
        verify(txnRepo, atLeastOnce()).save(any(BondTransaction.class));
        assertThat(pos.getLastCouponDate()).isNotNull();
    }

    // ── summary: ACTIVE position with NULL maturity → not counted as upcoming
    //    (first operand of the maturity-null && guard is false) ────────────────────

    @Test
    void summary_active_null_maturity_not_upcoming() {
        DebtInstrument inst = instrument(null, bd("20"));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        pos.setCouponRate(bd("20"));
        pos.setCouponFrequency(2);
        pos.setMaturityDate(null); // null maturity → upcoming guard short-circuits false
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());

        BondPortfolioSummary s = service.summary(USER);
        assertThat(s.positionCount()).isEqualTo(1);
        assertThat(s.upcomingMaturities()).isZero();
        // coupon leg still computed (rate>0, freq>0): 100000*20/100/2 = 10000.00
        assertThat(s.expectedNextCoupons()).isEqualByComparingTo("10000.00");
    }

    // ── summary: ACTIVE with positive coupon but maturity beyond 30 days
    //    (coupon branch true, upcoming branch false) ─────────────────────────────────

    @Test
    void summary_active_with_coupon_but_no_upcoming_maturity() {
        DebtInstrument inst = instrument(LocalDate.now().plusDays(31), bd("20"));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        pos.setCouponRate(bd("20"));
        pos.setCouponFrequency(2);
        pos.setMaturityDate(LocalDate.now().plusDays(31)); // strictly after today+30 → not upcoming
        when(positionRepo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(pos));
        lenient().when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(instrumentRepo.findBySymbol(ISIN)).thenReturn(Optional.of(inst));
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());

        BondPortfolioSummary s = service.summary(USER);
        assertThat(s.upcomingMaturities()).isZero();
        assertThat(s.expectedNextCoupons()).isEqualByComparingTo("10000.00");
    }

    // ── previewBuy: positive dirtyOverride bypasses clean+accrued (override branch) ──

    @Test
    void previewBuy_uses_positive_dirty_override() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));

        BondTradePreview p = service.previewBuy(ISIN, bd("1000"), bd("50"), bd("1"), bd("98.00"));
        assertThat(p.dirtyPrice()).isEqualByComparingTo("98.00");
        // cost = 1000 * 98/100 = 980.00
        assertThat(p.totalAmount()).isEqualByComparingTo("980.00");
        assertThat(p.proportionalCost()).isNull();
        assertThat(p.estimatedRealizedPnl()).isNull();
    }

    // ── previewSell: positive dirtyOverride with an active position ──────────────────

    @Test
    void previewSell_uses_positive_dirty_override_with_position() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        BondPosition pos = activePosition(bd("100000"), bd("97700.00"));
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(pos));

        // override 100.00 → proceeds 40000*100/100 = 40000.00; propCost 97700*40000/100000 = 39080.00; est 920.00
        BondTradePreview p = service.previewSell(USER, ISIN, bd("40000"), bd("50"), bd("1"), bd("100.00"));
        assertThat(p.dirtyPrice()).isEqualByComparingTo("100.00");
        assertThat(p.totalAmount()).isEqualByComparingTo("40000.00");
        assertThat(p.proportionalCost()).isEqualByComparingTo("39080.00");
        assertThat(p.estimatedRealizedPnl()).isEqualByComparingTo("920.00");
    }

    // ── buy: existing position present but null status guard handled via merge branch.
    //    Covers pos != null with a non-ACTIVE (MATURED) status → re-init branch
    //    distinct from the SOLD case already in the primary suite. ─────────────────────

    @Test
    void buy_reinitialises_matured_position_row() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));
        BondPosition matured = activePosition(BigDecimal.ZERO, BigDecimal.ZERO);
        matured.setStatus(BondPositionStatus.MATURED);
        when(positionRepo.findByUserIdAndIsin(USER, ISIN)).thenReturn(Optional.of(matured));
        lenient().when(quoteRepo.findLatestByInstrument(any())).thenReturn(Optional.empty());
        stubSaves();

        BondTradeResult res = service.buy(USER, ISIN, bd("1000"), bd("99"), bd("1"), null);
        assertThat(res.position().status()).isEqualTo("ACTIVE");
        assertThat(res.position().remainingNominal()).isEqualByComparingTo("1000");
        assertThat(matured.getStatus()).isEqualTo(BondPositionStatus.ACTIVE);
    }

    // ── buy: blank-vs-null nominal price guard already covered; add the dirtyFrom
    //    null-clean path through previewBuy (override null, clean null → dirty null,
    //    cost 0) without throwing (preview does not validate price). ────────────────

    @Test
    void previewBuy_null_clean_and_override_yields_null_dirty_zero_cost() {
        DebtInstrument inst = instrument(LocalDate.now().plusYears(2), bd("10"));
        when(instrumentRepo.findByIsin(ISIN)).thenReturn(Optional.of(inst));

        BondTradePreview p = service.previewBuy(ISIN, bd("1000"), null, null, null);
        assertThat(p.dirtyPrice()).isNull();
        // amountAtPrice(nominal, null) → ZERO
        assertThat(p.totalAmount()).isEqualByComparingTo("0");
    }

    // ── process: an ACTIVE position whose coupon leg pays AND which also matures
    //    in the same run is redeemed afterwards (status still ACTIVE after coupons
    //    → maturity branch true). Distinct from zero-coupon redemption already tested. ─

    @Test
    void process_pays_coupon_then_redeems_on_maturity() {
        BondPosition pos = activePosition(bd("100000"), bd("97700"));
        pos.setCouponRate(bd("20"));
        pos.setCouponFrequency(2);
        pos.setMaturityDate(LocalDate.now().minusDays(1)); // already matured today
        pos.setPurchaseDate(LocalDate.now().minusMonths(8));
        pos.setLastCouponDate(LocalDate.now().minusMonths(8));
        when(positionRepo.findByStatus(BondPositionStatus.ACTIVE)).thenReturn(List.of(pos));
        stubSaves();

        int events = service.processCouponsAndMaturities();
        assertThat(events).isGreaterThanOrEqualTo(2); // >=1 coupon + 1 redemption
        assertThat(pos.getStatus()).isEqualTo(BondPositionStatus.MATURED);
        assertThat(pos.getRemainingNominal()).isEqualByComparingTo("0");

        ArgumentCaptor<BondTransaction> cap = ArgumentCaptor.forClass(BondTransaction.class);
        verify(txnRepo, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues())
                .anyMatch(t -> t.getType() == BondTransactionType.BOND_COUPON_PAYMENT)
                .anyMatch(t -> t.getType() == BondTransactionType.BOND_REDEMPTION);
    }
}
