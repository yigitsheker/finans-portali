package com.finansportali.backend.controller;

import com.finansportali.backend.dto.request.SellPositionRequest;
import com.finansportali.backend.dto.request.UpsertPositionRequest;
import com.finansportali.backend.dto.response.portfolio.AllocationByTypeItem;
import com.finansportali.backend.dto.response.portfolio.AllocationItem;
import com.finansportali.backend.dto.response.portfolio.PortfolioPerformanceResponse;
import com.finansportali.backend.dto.response.portfolio.PortfolioSummary;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
class PortfolioControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private PortfolioService service;

    private PortfolioPosition position(String symbol, double qty, double avgCost) {
        return new PortfolioPosition("user-1", symbol,
                BigDecimal.valueOf(qty), BigDecimal.valueOf(avgCost));
    }

    @Test
    void positions_returns_user_list() throws Exception {
        when(service.list("user-1")).thenReturn(List.of(position("THYAO", 10, 100)));

        mvc.perform(get("/api/v1/portfolio/positions").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("THYAO"));
    }

    @Test
    void summary_returns_totals() throws Exception {
        when(service.summary("user-1")).thenReturn(new PortfolioSummary(
                new BigDecimal("12345.67"), List.of()));

        mvc.perform(get("/api/v1/portfolio/summary").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(12345.67));
    }

    @Test
    void allocation_returns_pct_per_symbol() throws Exception {
        when(service.allocation("user-1")).thenReturn(List.of(
                new AllocationItem("THYAO", new BigDecimal("1000"), new BigDecimal("50.0"))));

        mvc.perform(get("/api/v1/portfolio/allocation").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weightPct").value(50.0));
    }

    @Test
    void allocation_by_type_routes_to_service() throws Exception {
        when(service.allocationByType("user-1")).thenReturn(List.of(
                new AllocationByTypeItem("BIST", new BigDecimal("2000"), new BigDecimal("60.0"))));

        mvc.perform(get("/api/v1/portfolio/allocation/by-type").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BIST"));
    }

    @Test
    void upsert_position_routes_payload() throws Exception {
        mvc.perform(post("/api/v1/portfolio/positions")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"THYAO\",\"quantity\":10,\"avgCost\":100}"))
                .andExpect(status().isOk());

        verify(service).upsert(org.mockito.ArgumentMatchers.eq("user-1"), any(UpsertPositionRequest.class));
    }

    @Test
    void sell_returns_proceeds_envelope() throws Exception {
        when(service.sell(org.mockito.ArgumentMatchers.eq("user-1"), any(SellPositionRequest.class)))
                .thenReturn(new BigDecimal("1234.56"));

        mvc.perform(post("/api/v1/portfolio/positions/sell")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"THYAO\",\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("THYAO"))
                .andExpect(jsonPath("$.soldQuantity").value(5))
                .andExpect(jsonPath("$.proceeds").value(1234.56));
    }

    @Test
    void delete_position_by_symbol() throws Exception {
        mvc.perform(delete("/api/v1/portfolio/positions/THYAO")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).deleteBySymbol("user-1", "THYAO");
    }

    @Test
    void clear_deletes_all_positions() throws Exception {
        mvc.perform(delete("/api/v1/portfolio/positions")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).clear("user-1");
    }

    @Test
    void summary_detail_forwards_to_service() throws Exception {
        when(service.calculatePortfolioSummaryDetail("user-1"))
                .thenReturn(new com.finansportali.backend.dto.response.portfolio.PortfolioSummaryDetail(
                        new BigDecimal("100"), new BigDecimal("110"),
                        new BigDecimal("10"), new BigDecimal("10.0"), List.of()));

        mvc.perform(get("/api/v1/portfolio/summary-detail").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk());
    }

    @Test
    void performance_forwards_range_param() throws Exception {
        when(service.calculatePortfolioPerformance("user-1", "1Y"))
                .thenReturn(new PortfolioPerformanceResponse(
                        "1Y",
                        java.time.LocalDate.now().minusYears(1),
                        java.time.LocalDate.now(),
                        "DAILY", "DEMO", List.of()));

        mvc.perform(get("/api/v1/portfolio/performance")
                        .param("range", "1Y")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk());

        verify(service).calculatePortfolioPerformance("user-1", "1Y");
    }
}
