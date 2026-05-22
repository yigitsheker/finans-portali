package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.SummaryDto;
import com.finansportali.backend.dto.response.TechnicalAnalysisResponse;
import com.finansportali.backend.dto.response.TrendDto;
import com.finansportali.backend.service.TechnicalAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link TechnicalAnalysisController}.
 *
 * The controller is thin — most logic is in the service — so the tests
 * focus on:
 *   • routing + binding (path var, @DateTimeFormat query params)
 *   • the date-range guard (from > to → 400)
 *   • default-window behaviour when params are omitted
 */
@WebMvcTest(TechnicalAnalysisController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class TechnicalAnalysisControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private TechnicalAnalysisService service;

    private TechnicalAnalysisResponse stubResponse() {
        return new TechnicalAnalysisResponse(
                "THYAO",
                "2026-04-01",
                "2026-05-01",
                new TrendDto("UPWARD", 0.5, 5.2, "Yükselen"),
                new SummaryDto(298.0, 305.0, 290.0, 297.5, 22.4, 55.0),
                List.of());
    }

    @Test
    void get_with_full_query_params_routes_to_service() throws Exception {
        when(service.getTechnicalAnalysis(eq("THYAO"), any(), any())).thenReturn(stubResponse());

        mvc.perform(get("/api/v1/technical-analysis/THYAO")
                        .param("from", "2026-04-01")
                        .param("to",   "2026-05-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("THYAO"))
                .andExpect(jsonPath("$.trend.direction").value("UPWARD"))
                .andExpect(jsonPath("$.summary.latestClose").value(298.0));

        verify(service).getTechnicalAnalysis("THYAO",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"));
    }

    @Test
    void omitted_dates_default_to_last_three_months() throws Exception {
        when(service.getTechnicalAnalysis(eq("AKBNK"), any(), any())).thenReturn(stubResponse());

        mvc.perform(get("/api/v1/technical-analysis/AKBNK"))
                .andExpect(status().isOk());

        // We don't assert the exact dates (depends on LocalDate.now()), but
        // the service must have been called exactly once with non-null bounds.
        verify(service).getTechnicalAnalysis(eq("AKBNK"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void inverted_date_range_returns_400_without_hitting_service() throws Exception {
        mvc.perform(get("/api/v1/technical-analysis/THYAO")
                        .param("from", "2026-05-01")
                        .param("to",   "2026-04-01"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void malformed_date_returns_400() throws Exception {
        mvc.perform(get("/api/v1/technical-analysis/THYAO")
                        .param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
    }
}
