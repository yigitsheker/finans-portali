package com.finansportali.backend.service.analysis;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Maps an instrument category + recent volatility to a coarse risk bucket.
 * Used by both the table column ("Risk Seviyesi") and the chatbot when
 * picking scenarios. The rules are deliberately simple — anything more
 * elaborate belongs in a proper portfolio-risk service.
 */
@Service
public class RiskProfileService {

    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";

    /**
     * Category-first heuristic. Volatility ({@code changeYearly} % absolute)
     * can bump a normally-medium instrument up to high.
     */
    public String classify(String category, BigDecimal changeYearly) {
        if (category == null) return MEDIUM;
        String base = baseRiskByCategory(category);
        if (changeYearly == null) return base;
        double absYearly = changeYearly.abs().doubleValue();
        // Volatility override: any instrument moving more than 60% YoY in
        // absolute terms gets bumped up one bucket from its category base.
        if (absYearly > 60 && LOW.equals(base)) return MEDIUM;
        if (absYearly > 60 && MEDIUM.equals(base)) return HIGH;
        return base;
    }

    private String baseRiskByCategory(String category) {
        return switch (category) {
            case "INFLATION_TR", "INFLATION_US" -> LOW;
            case "BOND" -> LOW;
            case "FUND" -> MEDIUM;
            case "FX" -> MEDIUM;
            case "COMMODITY" -> MEDIUM;
            case "STOCK" -> MEDIUM;
            case "CRYPTO" -> HIGH;
            default -> MEDIUM;
        };
    }
}
