package com.finansportali.backend.service;

import com.finansportali.backend.dto.request.SellPositionRequest;
import com.finansportali.backend.dto.request.UpsertPositionRequest;
import com.finansportali.backend.dto.response.portfolio.AllocationByTypeItem;
import com.finansportali.backend.dto.response.portfolio.AllocationItem;
import com.finansportali.backend.dto.response.portfolio.PortfolioPerformanceResponse;
import com.finansportali.backend.dto.response.portfolio.PortfolioSummary;
import com.finansportali.backend.dto.response.portfolio.PortfolioSummaryDetail;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.service.portfolio.PortfolioCalculationService;
import com.finansportali.backend.service.portfolio.PortfolioPerformanceService;
import com.finansportali.backend.service.portfolio.PortfolioPositionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock private PortfolioPositionService positionService;
    @Mock private PortfolioCalculationService calculationService;
    @Mock private PortfolioPerformanceService performanceService;
    @InjectMocks private PortfolioService service;

    @Test
    void upsert_delegates() {
        UpsertPositionRequest req = new UpsertPositionRequest("THYAO", BigDecimal.ONE, BigDecimal.TEN);
        service.upsert("u", req);
        verify(positionService).upsert("u", req);
    }

    @Test
    void list_delegates() {
        List<PortfolioPosition> rows = List.of();
        when(positionService.list("u")).thenReturn(rows);
        assertThat(service.list("u")).isEqualTo(rows);
    }

    @Test
    void summary_delegates() {
        PortfolioSummary s = new PortfolioSummary(BigDecimal.ZERO, List.of());
        when(calculationService.summary("u")).thenReturn(s);
        assertThat(service.summary("u")).isEqualTo(s);
    }

    @Test
    void allocation_delegates() {
        List<AllocationItem> rows = List.of();
        when(calculationService.allocation("u")).thenReturn(rows);
        assertThat(service.allocation("u")).isEqualTo(rows);
    }

    @Test
    void allocationByType_delegates() {
        List<AllocationByTypeItem> rows = List.of();
        when(calculationService.allocationByType("u")).thenReturn(rows);
        assertThat(service.allocationByType("u")).isEqualTo(rows);
    }

    @Test
    void deleteBySymbol_delegates() {
        service.deleteBySymbol("u", "THYAO");
        verify(positionService).deleteBySymbol("u", "THYAO");
    }

    @Test
    void sell_delegates_and_returns_proceeds() {
        SellPositionRequest req = new SellPositionRequest("THYAO", BigDecimal.ONE);
        when(positionService.sell("u", req)).thenReturn(new BigDecimal("100"));
        assertThat(service.sell("u", req)).isEqualByComparingTo("100");
    }

    @Test
    void clear_delegates() {
        service.clear("u");
        verify(positionService).clear("u");
    }

    @Test
    void calculatePortfolioSummaryDetail_delegates() {
        PortfolioSummaryDetail d = new PortfolioSummaryDetail(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        when(calculationService.calculatePortfolioSummaryDetail("u")).thenReturn(d);
        assertThat(service.calculatePortfolioSummaryDetail("u")).isEqualTo(d);
    }

    @Test
    void calculatePortfolioPerformance_delegates() {
        PortfolioPerformanceResponse r = new PortfolioPerformanceResponse(
                "1M", LocalDate.now(), LocalDate.now(), "daily", "ws", List.of());
        when(performanceService.calculatePortfolioPerformance("u", "1M")).thenReturn(r);
        assertThat(service.calculatePortfolioPerformance("u", "1M")).isEqualTo(r);
    }
}
