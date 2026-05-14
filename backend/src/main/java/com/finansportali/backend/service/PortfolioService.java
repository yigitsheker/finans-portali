package com.finansportali.backend.service;

import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.dto.request.SellPositionRequest;
import com.finansportali.backend.dto.request.UpsertPositionRequest;
import com.finansportali.backend.dto.response.portfolio.*;
import com.finansportali.backend.service.portfolio.PortfolioCalculationService;
import com.finansportali.backend.service.portfolio.PortfolioPerformanceService;
import com.finansportali.backend.service.portfolio.PortfolioPositionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Facade service for portfolio operations.
 * Delegates to specialized services for different concerns.
 * 
 * @deprecated This class is kept for backward compatibility.
 * New code should use the specialized services directly:
 * - PortfolioPositionService for CRUD operations
 * - PortfolioCalculationService for summaries and allocations
 * - PortfolioPerformanceService for performance calculations
 */
@Service
public class PortfolioService {

    private final PortfolioPositionService positionService;
    private final PortfolioCalculationService calculationService;
    private final PortfolioPerformanceService performanceService;

    public PortfolioService(PortfolioPositionService positionService,
                            PortfolioCalculationService calculationService,
                            PortfolioPerformanceService performanceService) {
        this.positionService = positionService;
        this.calculationService = calculationService;
        this.performanceService = performanceService;
    }

    public void upsert(String userId, UpsertPositionRequest req) {
        positionService.upsert(userId, req);
    }

    public List<PortfolioPosition> list(String userId) {
        return positionService.list(userId);
    }

    public PortfolioSummary summary(String userId) {
        return calculationService.summary(userId);
    }

    public List<AllocationItem> allocation(String userId) {
        return calculationService.allocation(userId);
    }

    public List<AllocationByTypeItem> allocationByType(String userId) {
        return calculationService.allocationByType(userId);
    }

    public void deleteBySymbol(String userId, String symbol) {
        positionService.deleteBySymbol(userId, symbol);
    }

    public BigDecimal sell(String userId, SellPositionRequest req) {
        return positionService.sell(userId, req);
    }

    public void clear(String userId) {
        positionService.clear(userId);
    }

    public PortfolioSummaryDetail calculatePortfolioSummaryDetail(String userId) {
        return calculationService.calculatePortfolioSummaryDetail(userId);
    }

    public PortfolioPerformanceResponse calculatePortfolioPerformance(String userId, String range) {
        return performanceService.calculatePortfolioPerformance(userId, range);
    }
}
