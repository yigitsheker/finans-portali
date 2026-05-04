package com.finansportali.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record PortfolioPerformanceResponse(
        String range,
        LocalDate startDate,
        LocalDate endDate,
        String granularity,
        String source,
        List<PortfolioPerformancePoint> points
) {}
