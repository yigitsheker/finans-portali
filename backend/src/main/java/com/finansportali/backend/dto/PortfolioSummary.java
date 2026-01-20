package com.finansportali.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummary(
        BigDecimal totalValue,
        List<PositionView> positions
) {}
