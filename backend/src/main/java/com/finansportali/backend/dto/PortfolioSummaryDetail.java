package com.finansportali.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryDetail(
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalChangeValue,
        BigDecimal totalChangePercent,
        List<PortfolioPositionDetail> positions
) {}
