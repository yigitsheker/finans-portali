package com.finansportali.backend.service.bond;

import com.finansportali.backend.dto.response.bond.*;
import com.finansportali.backend.entity.*;
import com.finansportali.backend.repository.BondPositionRepository;
import com.finansportali.backend.repository.BondTransactionRepository;
import com.finansportali.backend.repository.DebtInstrumentQuoteRepository;
import com.finansportali.backend.repository.DebtInstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bond/bill position orchestration — SIMULATION ONLY. Nominal-based, dirty-price
 * cost, weighted-average merge per ISIN, proportional cost on partial sell, plus
 * scheduled coupon payments and maturity/redemption.
 */
@Service
public class BondPositionService {

    private static final Logger log = LoggerFactory.getLogger(BondPositionService.class);

    private final BondPositionRepository positionRepo;
    private final BondTransactionRepository txnRepo;
    private final DebtInstrumentRepository instrumentRepo;
    private final DebtInstrumentQuoteRepository quoteRepo;
    private final BondCalculationService calc;

    @Value("${app.bonds.coupon-frequency-default:2}")
    private int defaultCouponFrequency;

    @Value("${app.bonds.coupon-tax-rate:0}")
    private BigDecimal couponTaxRate;

    public BondPositionService(BondPositionRepository positionRepo,
                               BondTransactionRepository txnRepo,
                               DebtInstrumentRepository instrumentRepo,
                               DebtInstrumentQuoteRepository quoteRepo,
                               BondCalculationService calc) {
        this.positionRepo = positionRepo;
        this.txnRepo = txnRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.calc = calc;
    }

    // ── Buy ───────────────────────────────────────────────────────────────────
    /** Backward-compatible overload — no coupon-frequency override. */
    @Transactional
    public BondTradeResult buy(String userId, String identifier, BigDecimal nominal,
                               BigDecimal cleanPrice, BigDecimal accrued, BigDecimal dirtyOverride) {
        return buy(userId, identifier, nominal, cleanPrice, accrued, dirtyOverride, null);
    }

    @Transactional
    public BondTradeResult buy(String userId, String identifier, BigDecimal nominal,
                               BigDecimal cleanPrice, BigDecimal accrued, BigDecimal dirtyOverride,
                               Integer couponFrequencyOverride) {
        DebtInstrument inst = resolveInstrument(identifier);
        LocalDate today = LocalDate.now();
        if (inst.getMaturityDate() != null && !inst.getMaturityDate().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vadesi geçmiş enstrümanda yeni alış yapılamaz");
        }
        if (nominal == null || nominal.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nominal tutar sıfırdan büyük olmalı");
        }
        // User enters only the clean price → derive accrued interest from the
        // coupon schedule (skip when an explicit dirty price overrides everything).
        if (accrued == null && !hasOverride(dirtyOverride)) {
            accrued = computeAccrued(inst, today, couponFrequencyOverride);
        }
        BigDecimal dirty = dirtyPrice(cleanPrice, accrued, dirtyOverride);
        if (dirty == null || dirty.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fiyat sıfırdan büyük olmalı");
        }
        BigDecimal cost = calc.amountAtPrice(nominal, dirty);
        Instant now = Instant.now();

        BondPosition pos = positionRepo.findByUserIdAndIsin(userId, inst.getIsin()).orElse(null);
        if (pos != null && pos.getStatus() == BondPositionStatus.ACTIVE) {
            BigDecimal newNominal = pos.getRemainingNominal().add(nominal);
            BigDecimal newCost = pos.getTotalCost().add(cost);
            pos.setRemainingNominal(newNominal);
            pos.setTotalCost(newCost);
            pos.setAvgCostPrice(calc.weightedAvgPrice(newCost, newNominal));
        } else {
            if (pos == null) pos = new BondPosition();
            // Reactivating a previously SOLD/MATURED row begins a NEW holding
            // cycle: reset the closed cycle's realized P&L and coupon income so
            // they don't carry into the new position. (Lifetime totals for the
            // summary come from the transaction ledger, not the row.)
            pos.setRealizedPnl(BigDecimal.ZERO);
            pos.setCouponIncome(BigDecimal.ZERO);
            pos.setUserId(userId);
            pos.setIsin(inst.getIsin());
            pos.setSymbol(inst.getSymbol());
            pos.setName(inst.getName());
            pos.setType(inst.getType());
            pos.setIssuer(inst.getIssuer());
            pos.setCurrency(inst.getCurrency() != null ? inst.getCurrency() : "TRY");
            pos.setRemainingNominal(nominal);
            pos.setTotalCost(cost);
            pos.setAvgCostPrice(calc.weightedAvgPrice(cost, nominal));
            pos.setCouponRate(inst.getCouponRate());
            pos.setCouponFrequency(frequencyFor(inst, couponFrequencyOverride));
            pos.setMaturityDate(inst.getMaturityDate());
            pos.setPurchaseDate(today);
            pos.setLastCouponDate(today); // only coupons after purchase are paid
            pos.setStatus(BondPositionStatus.ACTIVE);
        }
        pos.setUpdatedAt(now);
        positionRepo.save(pos);

        BondTransaction t = newTxn(userId, inst.getIsin(), BondTransactionType.BOND_BUY, nominal, now);
        t.setCleanPrice(cleanPrice);
        t.setAccruedInterest(accrued);
        t.setDirtyPrice(dirty);
        t.setGrossAmount(cost);
        txnRepo.save(t);

        log.info("[BOND] buy {} nominal={} dirty={} cost={}", inst.getIsin(), nominal, dirty, cost);
        return new BondTradeResult(toView(pos), toView(t), "Alış kaydedildi (simülasyon)");
    }

    // ── Sell ──────────────────────────────────────────────────────────────────
    @Transactional
    public BondTradeResult sell(String userId, String identifier, BigDecimal nominal,
                                BigDecimal cleanPrice, BigDecimal accrued, BigDecimal dirtyOverride) {
        DebtInstrument inst = resolveInstrument(identifier);
        BondPosition pos = positionRepo.findByUserIdAndIsin(userId, inst.getIsin())
                .filter(p -> p.getStatus() == BondPositionStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aktif pozisyon bulunamadı"));
        if (nominal == null || nominal.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Satış nominali sıfırdan büyük olmalı");
        }
        if (nominal.compareTo(pos.getRemainingNominal()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Satış nominali eldeki nominalden fazla olamaz");
        }
        // Auto-accrue from the position's coupon schedule when not supplied.
        if (accrued == null && !hasOverride(dirtyOverride)) {
            accrued = computeAccrued(inst, LocalDate.now(), pos.getCouponFrequency());
        }
        BigDecimal sellDirty = dirtyPrice(cleanPrice, accrued, dirtyOverride);
        if (sellDirty == null || sellDirty.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fiyat sıfırdan büyük olmalı");
        }
        BigDecimal proceeds = calc.amountAtPrice(nominal, sellDirty);
        BigDecimal propCost = calc.proportionalCost(pos.getTotalCost(), nominal, pos.getRemainingNominal());
        BigDecimal realized = proceeds.subtract(propCost).setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now();

        pos.setRemainingNominal(pos.getRemainingNominal().subtract(nominal));
        pos.setTotalCost(pos.getTotalCost().subtract(propCost));
        pos.setRealizedPnl(pos.getRealizedPnl().add(realized));
        if (pos.getRemainingNominal().signum() <= 0) {
            pos.setRemainingNominal(BigDecimal.ZERO);
            pos.setTotalCost(BigDecimal.ZERO);
            pos.setStatus(BondPositionStatus.SOLD);
        }
        pos.setUpdatedAt(now);
        positionRepo.save(pos);

        BondTransaction t = newTxn(userId, inst.getIsin(), BondTransactionType.BOND_SELL, nominal, now);
        t.setCleanPrice(cleanPrice);
        t.setAccruedInterest(accrued);
        t.setDirtyPrice(sellDirty);
        t.setGrossAmount(proceeds);
        t.setRealizedPnl(realized);
        txnRepo.save(t);

        log.info("[BOND] sell {} nominal={} proceeds={} realized={}", inst.getIsin(), nominal, proceeds, realized);
        return new BondTradeResult(toView(pos), toView(t), "Satış kaydedildi (simülasyon)");
    }

    // ── Coupon + maturity processing (scheduled) ───────────────────────────────
    @Transactional
    public int processCouponsAndMaturities() {
        LocalDate today = LocalDate.now();
        List<BondPosition> active = positionRepo.findByStatus(BondPositionStatus.ACTIVE);
        int events = 0;
        for (BondPosition pos : active) {
            events += payDueCoupons(pos, today);
            if (pos.getStatus() == BondPositionStatus.ACTIVE
                    && pos.getMaturityDate() != null && !pos.getMaturityDate().isAfter(today)) {
                redeem(pos, today);
                events++;
            }
            pos.setUpdatedAt(Instant.now());
            positionRepo.save(pos);
        }
        if (events > 0) log.info("[BOND] processed {} coupon/maturity event(s)", events);
        return events;
    }

    private int payDueCoupons(BondPosition pos, LocalDate today) {
        if (pos.getCouponRate() == null || pos.getCouponRate().signum() <= 0
                || pos.getCouponFrequency() == null || pos.getCouponFrequency() <= 0) {
            return 0;
        }
        int paid = 0;
        LocalDate since = pos.getLastCouponDate() != null ? pos.getLastCouponDate() : pos.getPurchaseDate();
        for (LocalDate d : couponSchedule(pos)) {
            if (since != null && !d.isAfter(since)) continue;   // already paid / before purchase
            if (d.isAfter(today)) break;                         // future
            BigDecimal gross = calc.couponPayment(pos.getRemainingNominal(), pos.getCouponRate(), pos.getCouponFrequency());
            long holdingDays = pos.getPurchaseDate() != null
                    ? ChronoUnit.DAYS.between(pos.getPurchaseDate(), d) : 0L;
            BigDecimal tax = gross.multiply(couponTaxRateFor(pos.getType(), holdingDays))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(tax);
            BondTransaction t = newTxn(pos.getUserId(), pos.getIsin(),
                    BondTransactionType.BOND_COUPON_PAYMENT, pos.getRemainingNominal(), Instant.now());
            t.setGrossAmount(net);
            t.setGrossCoupon(gross);
            t.setTaxAmount(tax);
            t.setNetCoupon(net);
            t.setNote("Kupon ödemesi " + d);
            txnRepo.save(t);
            pos.setCouponIncome(pos.getCouponIncome().add(net));
            pos.setLastCouponDate(d);
            paid++;
        }
        return paid;
    }

    private void redeem(BondPosition pos, LocalDate today) {
        BigDecimal redemption = pos.getRemainingNominal();
        // Pull-to-par: principal is redeemed at par (100), so the difference vs the
        // remaining cost basis is a realized gain/loss. Without this a discount
        // bond / zero-coupon T-bill bought below par would show ZERO profit at
        // maturity. Booked the same way sell() books proceeds − proportional cost.
        BigDecimal gain = redemption.subtract(pos.getTotalCost()).setScale(2, RoundingMode.HALF_UP);
        BondTransaction t = newTxn(pos.getUserId(), pos.getIsin(),
                BondTransactionType.BOND_REDEMPTION, redemption, Instant.now());
        t.setGrossAmount(redemption);
        t.setRealizedPnl(gain);
        t.setNote("İtfa (anapara) " + today);
        txnRepo.save(t);
        pos.setRealizedPnl(pos.getRealizedPnl().add(gain));
        pos.setRemainingNominal(BigDecimal.ZERO);
        pos.setTotalCost(BigDecimal.ZERO);
        pos.setStatus(BondPositionStatus.MATURED);
    }

    // ── Reads ──────────────────────────────────────────────────────────────────
    public List<BondPositionView> list(String userId) {
        return positionRepo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toView).toList();
    }

    public List<BondPositionTransactionView> transactions(String userId) {
        return txnRepo.findByUserIdOrderByExecutedAtDesc(userId).stream().map(this::toView).toList();
    }

    public BondPortfolioSummary summary(String userId) {
        List<BondPosition> all = positionRepo.findByUserIdOrderByCreatedAtDesc(userId);
        LocalDate today = LocalDate.now();
        BigDecimal totalNominal = BigDecimal.ZERO, marketValue = BigDecimal.ZERO,
                expectedCoupons = BigDecimal.ZERO, unrealized = BigDecimal.ZERO;
        int count = 0, upcoming = 0;
        for (BondPosition pos : all) {
            if (pos.getStatus() == BondPositionStatus.ACTIVE) {
                count++;
                totalNominal = totalNominal.add(pos.getRemainingNominal());
                BondPositionView v = toView(pos);
                if (v.currentValue() != null) marketValue = marketValue.add(v.currentValue());
                if (v.unrealizedPnl() != null) unrealized = unrealized.add(v.unrealizedPnl());
                if (pos.getCouponRate() != null && pos.getCouponRate().signum() > 0
                        && pos.getCouponFrequency() != null && pos.getCouponFrequency() > 0) {
                    expectedCoupons = expectedCoupons.add(
                            calc.couponPayment(pos.getRemainingNominal(), pos.getCouponRate(), pos.getCouponFrequency()));
                }
                if (pos.getMaturityDate() != null
                        && !pos.getMaturityDate().isAfter(today.plusDays(30))) {
                    upcoming++;
                }
            }
        }
        // Lifetime realized P&L + coupon income from the transaction ledger, so
        // they survive a position-row reset when an ISIN is re-bought (reactivation).
        BigDecimal realized = BigDecimal.ZERO, realizedCoupon = BigDecimal.ZERO;
        for (BondTransaction t : txnRepo.findByUserIdOrderByExecutedAtDesc(userId)) {
            if (t.getRealizedPnl() != null
                    && (t.getType() == BondTransactionType.BOND_SELL
                        || t.getType() == BondTransactionType.BOND_REDEMPTION)) {
                realized = realized.add(t.getRealizedPnl());
            }
            if (t.getNetCoupon() != null && t.getType() == BondTransactionType.BOND_COUPON_PAYMENT) {
                realizedCoupon = realizedCoupon.add(t.getNetCoupon());
            }
        }
        return new BondPortfolioSummary(count,
                totalNominal.setScale(2, RoundingMode.HALF_UP),
                marketValue.setScale(2, RoundingMode.HALF_UP),
                upcoming,
                expectedCoupons.setScale(2, RoundingMode.HALF_UP),
                realizedCoupon.setScale(2, RoundingMode.HALF_UP),
                unrealized.setScale(2, RoundingMode.HALF_UP),
                realized.setScale(2, RoundingMode.HALF_UP));
    }

    public BondTradePreview previewBuy(String identifier, BigDecimal nominal,
                                       BigDecimal cleanPrice, BigDecimal accrued, BigDecimal dirtyOverride) {
        return previewBuy(identifier, nominal, cleanPrice, accrued, dirtyOverride, null);
    }

    public BondTradePreview previewBuy(String identifier, BigDecimal nominal,
                                       BigDecimal cleanPrice, BigDecimal accrued, BigDecimal dirtyOverride,
                                       Integer couponFrequencyOverride) {
        DebtInstrument inst = resolveInstrument(identifier); // validates existence
        if (accrued == null && !hasOverride(dirtyOverride)) {
            accrued = computeAccrued(inst, LocalDate.now(), couponFrequencyOverride);
        }
        BigDecimal dirty = dirtyPrice(cleanPrice, accrued, dirtyOverride);
        BigDecimal n = nominal == null ? BigDecimal.ZERO : nominal;
        BigDecimal cost = calc.amountAtPrice(n, dirty);
        return new BondTradePreview("BUY", identifier, n, cleanPrice, accrued, dirty, cost, null, null, null);
    }

    public BondTradePreview previewSell(String userId, String identifier, BigDecimal nominal,
                                        BigDecimal cleanPrice, BigDecimal accrued, BigDecimal dirtyOverride) {
        DebtInstrument inst = resolveInstrument(identifier);
        BondPosition pos = positionRepo.findByUserIdAndIsin(userId, inst.getIsin())
                .filter(p -> p.getStatus() == BondPositionStatus.ACTIVE).orElse(null);
        if (accrued == null && !hasOverride(dirtyOverride)) {
            accrued = computeAccrued(inst, LocalDate.now(), pos != null ? pos.getCouponFrequency() : null);
        }
        BigDecimal dirty = dirtyPrice(cleanPrice, accrued, dirtyOverride);
        BigDecimal n = nominal == null ? BigDecimal.ZERO : nominal;
        BigDecimal proceeds = calc.amountAtPrice(n, dirty);
        BigDecimal propCost = pos == null ? null
                : calc.proportionalCost(pos.getTotalCost(), n, pos.getRemainingNominal());
        BigDecimal estPnl = (propCost == null) ? null
                : proceeds.subtract(propCost).setScale(2, RoundingMode.HALF_UP);
        return new BondTradePreview("SELL", inst.getIsin(), n, cleanPrice, accrued, dirty, proceeds, propCost, estPnl, null);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private DebtInstrument resolveInstrument(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enstrüman belirtilmedi");
        }
        return instrumentRepo.findByIsin(identifier)
                .or(() -> instrumentRepo.findBySymbol(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enstrüman bulunamadı"));
    }

    private int frequencyFor(DebtInstrument inst, Integer override) {
        if (inst.getCouponRate() == null || inst.getCouponRate().signum() <= 0) return 0;
        if (override != null && override > 0) {
            // The coupon schedule steps by 12/freq months, while the coupon size is
            // rate/freq — these agree only when freq divides 12. Reject anything else
            // so a non-divisor (5,7,8,9,10,11) can't corrupt accrued/coupon math.
            if (12 % override != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Kupon sıklığı 12'nin böleni olmalı (1, 2, 3, 4, 6 veya 12)");
            }
            return override;  // user-selected (1=yıllık, 2=yarı-yıllık, 4=üç-aylık)
        }
        return defaultCouponFrequency;
    }

    /**
     * Accrued interest per 100 nominal at {@code settlement}, derived from the
     * coupon schedule (maturity stepped back by 12/frequency months to the period
     * holding the settlement date). Returns 0 for zero-coupon bills or when no
     * usable schedule exists.
     */
    private BigDecimal computeAccrued(DebtInstrument inst, LocalDate settlement, Integer freqOverride) {
        if (inst.getCouponRate() == null || inst.getCouponRate().signum() <= 0
                || inst.getMaturityDate() == null) {
            return BigDecimal.ZERO;
        }
        int freq = frequencyFor(inst, freqOverride);
        if (freq <= 0) return BigDecimal.ZERO;
        int monthsPer = 12 / freq;
        if (monthsPer <= 0) return BigDecimal.ZERO;
        LocalDate next = inst.getMaturityDate();
        while (next.minusMonths(monthsPer).isAfter(settlement)) {
            next = next.minusMonths(monthsPer);
        }
        LocalDate prev = next.minusMonths(monthsPer);
        return calc.accruedInterest(inst.getCouponRate(), freq, prev, next, settlement);
    }

    /**
     * Coupon withholding tax (stopaj) rate. Resident individuals pay 0% on
     * government / treasury / lease-certificate / eurobond coupons; corporate-bond
     * withholding decreases with holding period (illustrative brackets — adjust to
     * the current regulation). A positive {@code app.bonds.coupon-tax-rate}
     * overrides everything with a flat rate.
     */
    private BigDecimal couponTaxRateFor(DebtInstrumentType type, long holdingDays) {
        if (couponTaxRate != null && couponTaxRate.signum() > 0) return couponTaxRate;
        if (type == null) return BigDecimal.ZERO;
        return switch (type) {
            case GOVERNMENT_BOND, TREASURY_BILL, LEASE_CERTIFICATE, EUROBOND -> BigDecimal.ZERO;
            case CORPORATE_BOND -> holdingDays < 365 ? new BigDecimal("0.10")
                    : holdingDays < 1095 ? new BigDecimal("0.07")
                    : new BigDecimal("0.03");
            default -> BigDecimal.ZERO;
        };
    }

    /** A dirty-price override only counts when positive — used by both dirtyPrice()
     *  and the auto-accrued guards so the two agree on what "override present" means. */
    private static boolean hasOverride(BigDecimal dirtyOverride) {
        return dirtyOverride != null && dirtyOverride.signum() > 0;
    }

    private BigDecimal dirtyPrice(BigDecimal clean, BigDecimal accrued, BigDecimal dirtyOverride) {
        if (hasOverride(dirtyOverride)) return dirtyOverride;
        return calc.dirtyFrom(clean, accrued);
    }

    private List<LocalDate> couponSchedule(BondPosition pos) {
        List<LocalDate> out = new ArrayList<>();
        if (pos.getCouponFrequency() == null || pos.getCouponFrequency() <= 0 || pos.getMaturityDate() == null) {
            return out;
        }
        int monthsPer = 12 / pos.getCouponFrequency();
        if (monthsPer <= 0) return out;
        LocalDate floor = pos.getPurchaseDate() != null ? pos.getPurchaseDate() : pos.getMaturityDate().minusYears(50);
        LocalDate d = pos.getMaturityDate();
        while (d.isAfter(floor)) {
            out.add(d);
            d = d.minusMonths(monthsPer);
        }
        Collections.reverse(out);
        return out;
    }

    private BondTransaction newTxn(String userId, String isin, BondTransactionType type,
                                   BigDecimal nominal, Instant when) {
        BondTransaction t = new BondTransaction();
        t.setUserId(userId);
        t.setIsin(isin);
        t.setType(type);
        t.setNominal(nominal);
        t.setGrossAmount(BigDecimal.ZERO);
        t.setExecutedAt(when);
        return t;
    }

    private record PriceInfo(BigDecimal clean, BigDecimal dirty, BigDecimal yield, boolean stale) {}

    private PriceInfo currentPrices(String isin) {
        DebtInstrument inst = instrumentRepo.findByIsin(isin).or(() -> instrumentRepo.findBySymbol(isin)).orElse(null);
        if (inst == null) return new PriceInfo(null, null, null, true);
        DebtInstrumentQuote q = quoteRepo.findLatestByInstrument(inst).orElse(null);
        if (q == null) return new PriceInfo(null, null, null, true);
        BigDecimal clean = q.getCleanPrice() != null ? q.getCleanPrice() : q.getPrice();
        BigDecimal dirty = q.getDirtyPrice() != null ? q.getDirtyPrice()
                : (clean != null ? clean : q.getPrice());
        boolean stale = q.getQuoteDate() != null && q.getQuoteDate().isBefore(LocalDate.now());
        return new PriceInfo(clean, dirty, q.getYieldRate(), stale);
    }

    private BondPositionView toView(BondPosition pos) {
        if (pos == null) return null;
        LocalDate today = LocalDate.now();
        PriceInfo pi = currentPrices(pos.getIsin());
        BigDecimal currentValue = (pi.dirty() != null)
                ? calc.amountAtPrice(pos.getRemainingNominal(), pi.dirty()) : null;
        BigDecimal unrealized = (pos.getStatus() == BondPositionStatus.ACTIVE && currentValue != null)
                ? currentValue.subtract(pos.getTotalCost()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Long days = pos.getMaturityDate() != null ? ChronoUnit.DAYS.between(today, pos.getMaturityDate()) : null;
        return new BondPositionView(
                pos.getId(), pos.getIsin(), pos.getSymbol(), pos.getName(),
                pos.getType() == null ? null : pos.getType().name(), pos.getIssuer(), pos.getCurrency(),
                pos.getRemainingNominal(), pos.getAvgCostPrice(), pos.getTotalCost(),
                pi.clean(), pi.dirty(), pi.yield(), pos.getCouponRate(), pos.getCouponFrequency(),
                pos.getMaturityDate(), days, pos.getPurchaseDate(), currentValue, unrealized,
                pos.getRealizedPnl(), pos.getCouponIncome(), pos.getStatus().name(),
                currentValue != null && pi.stale());
    }

    private BondPositionTransactionView toView(BondTransaction t) {
        return new BondPositionTransactionView(t.getId(), t.getIsin(), t.getType().name(),
                t.getNominal(), t.getCleanPrice(), t.getAccruedInterest(), t.getDirtyPrice(),
                t.getGrossAmount(), t.getRealizedPnl(), t.getGrossCoupon(), t.getTaxAmount(),
                t.getNetCoupon(), t.getExecutedAt(), t.getNote());
    }
}
