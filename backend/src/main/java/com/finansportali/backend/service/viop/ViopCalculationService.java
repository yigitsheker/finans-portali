package com.finansportali.backend.service.viop;

import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.entity.ViopDirection;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure VİOP (futures) math — no persistence, no Spring deps used in the
 * formulas, so it is trivially unit-testable against the spec examples.
 *
 * <p>Definitions (all simulation/illustration only):
 * <ul>
 *   <li>positionSize = entryPrice × contractSize × qty</li>
 *   <li>requiredMargin = initialMargin×qty (if known) else positionSize × marginRate</li>
 *   <li>leverage ≈ positionSize / requiredMargin</li>
 *   <li>unrealized P/L LONG = (current−entry)×size×qty, SHORT = (entry−current)×size×qty</li>
 *   <li>realized  P/L LONG = (exit−entry)×size×closedQty, SHORT = (entry−exit)×size×closedQty</li>
 * </ul>
 */
@Service
public class ViopCalculationService {

    /**
     * Contract size (kontrat büyüklüğü) per İş Yatırım VİOP category. This is the
     * authoritative source — the frontend mirrors these for preview only.
     */
    public static int contractSizeFor(ViopContract.Category category) {
        if (category == null) return 1;
        return switch (category) {
            case STOCK -> 100;        // 1 lot = 100 pay
            case INDEX -> 10;         // 1 lot = 10 × endeks puanı
            case FX_TRY, FX_USD -> 1000;
            case METAL_USD -> 10;
            case METAL_TRY, METAL -> 1;
        };
    }

    /**
     * BIST VİOP contracts are all TRY-margined and TRY-settled — even USD/TL and
     * USD-priced metal futures settle their (variation) margin in TRY. So every
     * position's P&L, margin and size are in TRY, which keeps the portfolio
     * summary single-currency. A category's USD pricing only affects the quoted
     * price / contract size, not the settlement currency. (Param kept for API
     * stability and a future genuinely-FX contract.)
     */
    public static String currencyFor(ViopContract.Category category) {
        return "TRY";
    }

    public BigDecimal positionSize(BigDecimal entryPrice, BigDecimal contractSize, BigDecimal qty) {
        if (entryPrice == null || contractSize == null || qty == null) return BigDecimal.ZERO;
        return entryPrice.multiply(contractSize).multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Required margin. Prefers a known per-contract initial margin; otherwise
     * applies a margin rate to the position size.
     */
    public BigDecimal requiredMargin(BigDecimal positionSize, BigDecimal marginRate,
                                     BigDecimal initialMargin, BigDecimal qty) {
        if (initialMargin != null && initialMargin.signum() > 0 && qty != null) {
            return initialMargin.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        }
        if (positionSize == null || marginRate == null) return BigDecimal.ZERO;
        return positionSize.multiply(marginRate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Approximate leverage = position size / required margin. Null if margin ≤ 0. */
    public BigDecimal leverage(BigDecimal positionSize, BigDecimal requiredMargin) {
        if (positionSize == null || requiredMargin == null || requiredMargin.signum() <= 0) return null;
        return positionSize.divide(requiredMargin, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal unrealizedPnl(ViopDirection direction, BigDecimal entryPrice,
                                    BigDecimal currentPrice, BigDecimal contractSize, BigDecimal qty) {
        if (direction == null || entryPrice == null || currentPrice == null
                || contractSize == null || qty == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = direction == ViopDirection.LONG
                ? currentPrice.subtract(entryPrice)
                : entryPrice.subtract(currentPrice);
        return diff.multiply(contractSize).multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal realizedPnl(ViopDirection direction, BigDecimal entryPrice,
                                  BigDecimal exitPrice, BigDecimal contractSize, BigDecimal closedQty) {
        // Same formula as unrealized but over the closed quantity at the exit price.
        return unrealizedPnl(direction, entryPrice, exitPrice, contractSize, closedQty);
    }
}
