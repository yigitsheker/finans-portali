package com.finansportali.backend.service.viop;

import com.finansportali.backend.dto.response.viop.*;
import com.finansportali.backend.entity.*;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.ViopContractRepository;
import com.finansportali.backend.repository.ViopPositionRepository;
import com.finansportali.backend.repository.ViopTransactionRepository;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * VİOP (futures) position orchestration — SIMULATION ONLY, never sends a real
 * order. Implements net-position logic: a user never holds LONG and SHORT on the
 * same contract at once. Opening the opposite side first closes the existing
 * side (recording CLOSE legs) and only the remainder opens a new position.
 */
@Service
public class ViopPositionService {

    private static final Logger log = LoggerFactory.getLogger(ViopPositionService.class);

    // BIST contracts trade in Istanbul time; using the server's default zone
    // (UTC on GKE) would shift the "today" boundary for maturity/expiry by hours.
    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private final ViopPositionRepository positionRepo;
    private final ViopTransactionRepository txnRepo;
    private final ViopContractRepository contractRepo;
    private final MarketQuoteRepository marketQuoteRepo;
    private final ViopCalculationService calc;

    @Value("${app.viop.margin-rate:0.10}")
    private BigDecimal marginRate;

    // Optional broker/exchange commission as a fraction of the closed notional,
    // deducted from realized P&L on close/expiry. Default 0 → no fee (opt-in).
    @Value("${app.viop.commission-rate:0}")
    private BigDecimal commissionRate;

    public ViopPositionService(ViopPositionRepository positionRepo,
                               ViopTransactionRepository txnRepo,
                               ViopContractRepository contractRepo,
                               MarketQuoteRepository marketQuoteRepo,
                               ViopCalculationService calc) {
        this.positionRepo = positionRepo;
        this.txnRepo = txnRepo;
        this.contractRepo = contractRepo;
        this.marketQuoteRepo = marketQuoteRepo;
        this.calc = calc;
    }

    // ── Open (Long Aç / Short Aç) with net-position logic ───────────────────
    @Transactional
    public ViopTradeResult open(String userId, String contractSymbol,
                                ViopDirection requestedDirection, BigDecimal qtyRequested,
                                BigDecimal priceOverride) {
        ViopContract contract = contractRepo.findBySymbol(contractSymbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kontrat bulunamadı"));
        LocalDate maturity = maturityDate(contract);
        if (maturity == null) {
            // No maturity → the position could never be expired by the scheduler.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kontrat vadesi belirsiz; işlem yapılamaz");
        }
        if (!maturity.isAfter(LocalDate.now(IST))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vadesi geçmiş kontratta yeni pozisyon açılamaz");
        }
        if (qtyRequested == null || qtyRequested.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kontrat adedi sıfırdan büyük olmalı");
        }
        BigDecimal price = resolvePrice(contract, priceOverride);
        BigDecimal contractSize = BigDecimal.valueOf(ViopCalculationService.contractSizeFor(contract.getCategory()));
        String currency = ViopCalculationService.currencyFor(contract.getCategory());

        Instant now = Instant.now();
        List<ViopTransaction> legs = new ArrayList<>();
        BigDecimal remaining = qtyRequested;

        ViopPosition pos = positionRepo.findByUserIdAndContractSymbol(userId, contractSymbol).orElse(null);
        boolean hasOpen = pos != null && pos.getStatus() == ViopPositionStatus.OPEN
                && pos.getQuantity().signum() > 0;

        // 1) If an opposing open position exists, close it first (net logic).
        if (hasOpen && pos.getDirection() != requestedDirection) {
            BigDecimal closeQty = pos.getQuantity().min(remaining);
            BigDecimal realized = applyCommission(
                    calc.realizedPnl(pos.getDirection(), pos.getEntryPrice(), price, contractSize, closeQty),
                    price, contractSize, closeQty);
            boolean partial = closeQty.compareTo(pos.getQuantity()) < 0;
            ViopTransactionType closeType = partial ? ViopTransactionType.PARTIAL_CLOSE
                    : (pos.getDirection() == ViopDirection.LONG
                    ? ViopTransactionType.LONG_CLOSE : ViopTransactionType.SHORT_CLOSE);
            legs.add(record(userId, contractSymbol, closeType, closeQty, price, contractSize, realized, now,
                    "Net pozisyon: ters yön kapatma"));

            pos.setRealizedPnl(pos.getRealizedPnl().add(realized));
            BigDecimal newQty = pos.getQuantity().subtract(closeQty);
            remaining = remaining.subtract(closeQty);
            if (newQty.signum() <= 0) {
                pos.setQuantity(BigDecimal.ZERO);
                pos.setStatus(ViopPositionStatus.CLOSED);
                pos.setClosedAt(now);
            } else {
                pos.setQuantity(newQty);
                recomputeMargin(pos, contractSize);
            }
            pos.setUpdatedAt(now);
            positionRepo.save(pos);
        }

        // 2) Open requested direction for whatever remains.
        if (remaining.signum() > 0) {
            if (pos == null) {
                pos = new ViopPosition();
                pos.setUserId(userId);
                pos.setContractSymbol(contractSymbol);
            }
            pos.setUnderlying(contract.getUnderlying());
            pos.setContractType(contract.getCategory().name());
            pos.setMaturityDate(maturity);
            pos.setCurrency(currency);
            pos.setContractSize(contractSize);
            // Snapshot per-contract margins (from a verified exchange source) when
            // present; null leaves requiredMargin on the category-based rate.
            pos.setInitialMargin(contract.getInitialMargin());
            pos.setMaintenanceMargin(contract.getMaintenanceMargin());

            boolean addingSameSide = pos.getStatus() == ViopPositionStatus.OPEN
                    && pos.getQuantity().signum() > 0 && pos.getDirection() == requestedDirection;
            if (addingSameSide) {
                BigDecimal oldQty = pos.getQuantity();
                BigDecimal newQty = oldQty.add(remaining);
                BigDecimal weighted = pos.getEntryPrice().multiply(oldQty)
                        .add(price.multiply(remaining))
                        .divide(newQty, 6, RoundingMode.HALF_UP);
                pos.setEntryPrice(weighted);
                pos.setQuantity(newQty);
            } else {
                // fresh open or flip onto a now-closed row (realizedPnl is kept)
                pos.setDirection(requestedDirection);
                pos.setEntryPrice(price);
                pos.setQuantity(remaining);
                pos.setStatus(ViopPositionStatus.OPEN);
                pos.setClosedAt(null);
                pos.setOpenedAt(now);
            }
            recomputeMargin(pos, contractSize);
            pos.setUpdatedAt(now);
            positionRepo.save(pos);

            ViopTransactionType openType = requestedDirection == ViopDirection.LONG
                    ? ViopTransactionType.LONG_OPEN : ViopTransactionType.SHORT_OPEN;
            legs.add(record(userId, contractSymbol, openType, remaining, price, contractSize,
                    BigDecimal.ZERO, now, null));
        }

        ViopPosition finalPos = positionRepo.findByUserIdAndContractSymbol(userId, contractSymbol).orElse(null);
        log.info("[VIOP] open {} {} x{} → {} leg(s)", contractSymbol, requestedDirection, qtyRequested, legs.size());
        return new ViopTradeResult(toView(finalPos), toViews(legs), "Pozisyon güncellendi (simülasyon)");
    }

    // ── Close / Partial close ───────────────────────────────────────────────
    @Transactional
    public ViopTradeResult close(String userId, String contractSymbol, BigDecimal qty, BigDecimal priceOverride) {
        ViopPosition pos = positionRepo.findByUserIdAndContractSymbol(userId, contractSymbol)
                .filter(p -> p.getStatus() == ViopPositionStatus.OPEN && p.getQuantity().signum() > 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Açık pozisyon bulunamadı"));
        if (qty == null || qty.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kapatılacak adet sıfırdan büyük olmalı");
        }
        if (qty.compareTo(pos.getQuantity()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kapatılacak adet açık pozisyon adedinden fazla olamaz");
        }
        ViopContract contract = contractRepo.findBySymbol(contractSymbol).orElse(null);
        BigDecimal price = resolvePrice(contract, priceOverride);
        BigDecimal contractSize = pos.getContractSize();

        BigDecimal realized = applyCommission(
                calc.realizedPnl(pos.getDirection(), pos.getEntryPrice(), price, contractSize, qty),
                price, contractSize, qty);
        BigDecimal newQty = pos.getQuantity().subtract(qty);
        boolean partial = newQty.signum() > 0;
        ViopTransactionType type = partial ? ViopTransactionType.PARTIAL_CLOSE
                : (pos.getDirection() == ViopDirection.LONG
                ? ViopTransactionType.LONG_CLOSE : ViopTransactionType.SHORT_CLOSE);

        Instant now = Instant.now();
        ViopTransaction leg = record(userId, contractSymbol, type, qty, price, contractSize, realized, now, null);
        pos.setRealizedPnl(pos.getRealizedPnl().add(realized));
        if (partial) {
            pos.setQuantity(newQty);
            recomputeMargin(pos, contractSize);
        } else {
            pos.setQuantity(BigDecimal.ZERO);
            pos.setStatus(ViopPositionStatus.CLOSED);
            pos.setClosedAt(now);
        }
        pos.setUpdatedAt(now);
        positionRepo.save(pos);
        log.info("[VIOP] close {} x{} realized={}", contractSymbol, qty, realized);
        return new ViopTradeResult(toView(pos), List.of(toView(leg)), "Pozisyon kapatıldı (simülasyon)");
    }

    // ── Expiry job ──────────────────────────────────────────────────────────
    @Transactional
    public int expireDuePositions() {
        LocalDate today = LocalDate.now(IST);
        List<ViopPosition> due = positionRepo.findByStatusAndMaturityDateLessThanEqual(
                ViopPositionStatus.OPEN, today);
        Instant now = Instant.now();
        for (ViopPosition pos : due) {
            ViopContract c = contractRepo.findBySymbol(pos.getContractSymbol()).orElse(null);
            BigDecimal price = resolvePriceOrNull(c);
            if (price == null) {
                // No settlement price available (the scrape typically stops once a
                // contract is no longer active) → fall back to entry, i.e. flat P&L.
                // Surface it instead of silently settling at break-even.
                price = pos.getEntryPrice();
                log.warn("[VIOP-EXPIRY] {} (user {}) — no settlement price; settling at entry (P&L=0)",
                        pos.getContractSymbol(), pos.getUserId());
            }
            BigDecimal realized = applyCommission(
                    calc.realizedPnl(pos.getDirection(), pos.getEntryPrice(), price, pos.getContractSize(), pos.getQuantity()),
                    price, pos.getContractSize(), pos.getQuantity());
            record(pos.getUserId(), pos.getContractSymbol(), ViopTransactionType.EXPIRE,
                    pos.getQuantity(), price, pos.getContractSize(), realized, now, "Vade sonu");
            pos.setRealizedPnl(pos.getRealizedPnl().add(realized));
            pos.setQuantity(BigDecimal.ZERO);
            pos.setStatus(ViopPositionStatus.EXPIRED);
            pos.setClosedAt(now);
            pos.setUpdatedAt(now);
            positionRepo.save(pos);
        }
        if (!due.isEmpty()) log.info("[VIOP] expired {} position(s)", due.size());
        return due.size();
    }

    // ── Reads ────────────────────────────────────────────────────────────────
    public List<ViopPositionView> list(String userId) {
        return positionRepo.findByUserIdOrderByOpenedAtDesc(userId).stream().map(this::toView).toList();
    }

    public List<ViopTransactionView> transactions(String userId) {
        return txnRepo.findByUserIdOrderByExecutedAtDesc(userId).stream().map(this::toView).toList();
    }

    public ViopSummary summary(String userId) {
        List<ViopPosition> all = positionRepo.findByUserIdOrderByOpenedAtDesc(userId);
        BigDecimal totalSize = BigDecimal.ZERO, totalMargin = BigDecimal.ZERO,
                totalUnreal = BigDecimal.ZERO, totalReal = BigDecimal.ZERO;
        int openCount = 0;
        for (ViopPosition pos : all) {
            totalReal = totalReal.add(pos.getRealizedPnl());
            if (pos.getStatus() == ViopPositionStatus.OPEN && pos.getQuantity().signum() > 0) {
                openCount++;
                ViopPositionView v = toView(pos);
                if (v.positionSize() != null) totalSize = totalSize.add(v.positionSize());
                if (v.requiredMargin() != null) totalMargin = totalMargin.add(v.requiredMargin());
                if (v.unrealizedPnl() != null) totalUnreal = totalUnreal.add(v.unrealizedPnl());
            }
        }
        return new ViopSummary(openCount,
                totalSize.setScale(2, RoundingMode.HALF_UP),
                totalMargin.setScale(2, RoundingMode.HALF_UP),
                totalUnreal.setScale(2, RoundingMode.HALF_UP),
                totalReal.setScale(2, RoundingMode.HALF_UP));
    }

    public ViopPreviewResult preview(String userId, String contractSymbol,
                                     ViopDirection direction, BigDecimal qty, BigDecimal priceOverride) {
        ViopContract contract = contractRepo.findBySymbol(contractSymbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kontrat bulunamadı"));
        BigDecimal price = resolvePrice(contract, priceOverride);
        BigDecimal contractSize = BigDecimal.valueOf(ViopCalculationService.contractSizeFor(contract.getCategory()));
        String currency = ViopCalculationService.currencyFor(contract.getCategory());
        BigDecimal q = (qty == null || qty.signum() <= 0) ? BigDecimal.ONE : qty;
        BigDecimal posSize = calc.positionSize(price, contractSize, q);
        BigDecimal rate = ViopCalculationService.marginRateFor(contract.getCategory());
        BigDecimal reqMargin = calc.requiredMargin(posSize, rate, contract.getInitialMargin(), q);
        BigDecimal lev = calc.leverage(posSize, reqMargin);

        ViopPosition existing = positionRepo.findByUserIdAndContractSymbol(userId, contractSymbol).orElse(null);
        boolean willCloseOpposite = existing != null && existing.getStatus() == ViopPositionStatus.OPEN
                && existing.getQuantity().signum() > 0 && existing.getDirection() != direction;

        return new ViopPreviewResult(contractSymbol, direction == null ? null : direction.name(),
                q, price, contractSize, currency, posSize, reqMargin, lev, willCloseOpposite,
                willCloseOpposite ? "Bu işlem önce ters yöndeki açık pozisyonu kapatır" : null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    /** Net realized P&L after the optional commission on the closed notional.
     *  commissionRate defaults to 0, so this is a no-op unless configured. */
    private BigDecimal applyCommission(BigDecimal grossRealized, BigDecimal price,
                                       BigDecimal contractSize, BigDecimal qty) {
        if (commissionRate == null || commissionRate.signum() <= 0) return grossRealized;
        BigDecimal fee = calc.positionSize(price, contractSize, qty)
                .multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        return grossRealized.subtract(fee).setScale(2, RoundingMode.HALF_UP);
    }

    private void recomputeMargin(ViopPosition pos, BigDecimal contractSize) {
        BigDecimal posSize = calc.positionSize(pos.getEntryPrice(), contractSize, pos.getQuantity());
        BigDecimal rate = effectiveMarginRate(pos);
        pos.setMarginRate(rate);
        pos.setRequiredMargin(calc.requiredMargin(posSize, rate, pos.getInitialMargin(), pos.getQuantity()));
    }

    /** Category-based margin rate; falls back to the configured global default
     *  ({@code app.viop.margin-rate}) when the category can't be resolved. */
    private BigDecimal effectiveMarginRate(ViopPosition pos) {
        ViopContract.Category c = parseCategory(pos.getContractType());
        return c != null ? ViopCalculationService.marginRateFor(c) : marginRate;
    }

    private ViopContract.Category parseCategory(String name) {
        if (name == null) return null;
        try { return ViopContract.Category.valueOf(name); }
        catch (IllegalArgumentException e) { return null; }
    }

    private ViopTransaction record(String userId, String contractSymbol, ViopTransactionType type,
                                   BigDecimal qty, BigDecimal price, BigDecimal contractSize,
                                   BigDecimal realized, Instant when, String note) {
        ViopTransaction t = new ViopTransaction();
        t.setUserId(userId);
        t.setContractSymbol(contractSymbol);
        t.setType(type);
        t.setQuantity(qty);
        t.setPrice(price);
        t.setContractSize(contractSize);
        t.setPositionSize(calc.positionSize(price, contractSize, qty));
        t.setRealizedPnl(realized == null ? BigDecimal.ZERO : realized.setScale(2, RoundingMode.HALF_UP));
        t.setExecutedAt(when);
        t.setNote(note);
        return txnRepo.save(t);
    }

    private LocalDate maturityDate(ViopContract c) {
        if (c.getMaturityYear() == null || c.getMaturityMonth() == null) return null;
        return YearMonth.of(c.getMaturityYear(), c.getMaturityMonth()).atEndOfMonth();
    }

    private BigDecimal resolvePrice(ViopContract contract, BigDecimal override) {
        if (override != null && override.signum() > 0) return override;
        BigDecimal p = resolvePriceOrNull(contract);
        if (p == null || p.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kontrat için güncel fiyat bulunamadı");
        }
        return p;
    }

    private BigDecimal resolvePriceOrNull(ViopContract contract) {
        if (contract == null) return null;
        if (contract.getLastPrice() != null && contract.getLastPrice().signum() > 0) {
            return contract.getLastPrice();
        }
        return marketQuoteRepo.findTop1ByInstrument_SymbolOrderByAsOfDesc(contract.getSymbol())
                .map(MarketQuote::getLast).orElse(null);
    }

    private ViopPositionView toView(ViopPosition pos) {
        if (pos == null) return null;
        ViopContract c = contractRepo.findBySymbol(pos.getContractSymbol()).orElse(null);
        BigDecimal current = resolvePriceOrNull(c);
        BigDecimal posSize = calc.positionSize(pos.getEntryPrice(), pos.getContractSize(), pos.getQuantity());
        BigDecimal lev = calc.leverage(posSize, pos.getRequiredMargin());
        BigDecimal unreal = (pos.getStatus() == ViopPositionStatus.OPEN && current != null)
                ? calc.unrealizedPnl(pos.getDirection(), pos.getEntryPrice(), current, pos.getContractSize(), pos.getQuantity())
                : BigDecimal.ZERO;
        return new ViopPositionView(
                pos.getId(), pos.getContractSymbol(), pos.getUnderlying(), pos.getContractType(),
                pos.getMaturityDate(), pos.getDirection().name(), pos.getQuantity(), pos.getEntryPrice(),
                current, pos.getContractSize(), pos.getCurrency(), posSize, pos.getRequiredMargin(),
                pos.getMarginRate(), pos.getInitialMargin(), pos.getMaintenanceMargin(), lev,
                unreal, pos.getRealizedPnl(), pos.getStatus().name(), pos.getOpenedAt());
    }

    private List<ViopTransactionView> toViews(List<ViopTransaction> legs) {
        return legs.stream().map(this::toView).toList();
    }

    private ViopTransactionView toView(ViopTransaction t) {
        return new ViopTransactionView(t.getId(), t.getContractSymbol(), t.getType().name(),
                t.getQuantity(), t.getPrice(), t.getContractSize(), t.getPositionSize(),
                t.getRealizedPnl(), t.getExecutedAt(), t.getNote());
    }
}
