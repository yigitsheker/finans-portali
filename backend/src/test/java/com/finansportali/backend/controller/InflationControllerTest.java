package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.inflation.InflationCompareDto;
import com.finansportali.backend.entity.InflationDataPoint;
import com.finansportali.backend.service.InflationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InflationController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class InflationControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private InflationService service;

    private InflationDataPoint point(LocalDate d, double cpi, double yearly) {
        InflationDataPoint p = new InflationDataPoint();
        p.setPeriodDate(d);
        p.setCpiIndex(BigDecimal.valueOf(cpi));
        p.setCpiYearlyChange(BigDecimal.valueOf(yearly));
        p.setSource("TCMB");
        return p;
    }

    @Test
    void list_returns_dto_array() throws Exception {
        when(service.getAllAscending()).thenReturn(List.of(
                point(LocalDate.of(2026, 1, 1), 3500.0, 35.2),
                point(LocalDate.of(2026, 2, 1), 3600.0, 33.0)));

        mvc.perform(get("/api/v1/inflation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cpiIndex").value(3500.0))
                .andExpect(jsonPath("$[1].cpiYearlyChange").value(33.0));
    }

    @Test
    void latest_returns_200_when_present() throws Exception {
        when(service.getLatest()).thenReturn(Optional.of(
                point(LocalDate.of(2026, 5, 1), 3700.0, 30.5)));

        mvc.perform(get("/api/v1/inflation/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpiIndex").value(3700.0));
    }

    @Test
    void latest_returns_204_when_absent() throws Exception {
        when(service.getLatest()).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/inflation/latest"))
                .andExpect(status().isNoContent());
    }

    @Test
    void compare_passes_dates_and_nominal_through() throws Exception {
        when(service.compare(eq(LocalDate.parse("2026-01-01")),
                             eq(LocalDate.parse("2026-05-01")),
                             any()))
                .thenReturn(Optional.of(new InflationCompareDto(
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-05-01"),
                        new BigDecimal("3500"),
                        new BigDecimal("3700"),
                        new BigDecimal("5.71"),
                        new BigDecimal("10.00"),
                        new BigDecimal("4.06"))));

        mvc.perform(get("/api/v1/inflation/compare")
                        .param("from", "2026-01-01")
                        .param("to",   "2026-05-01")
                        .param("nominalPct", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumulativeInflationPct").value(5.71))
                .andExpect(jsonPath("$.realReturnPct").value(4.06));
    }

    @Test
    void compare_returns_204_when_data_missing() throws Exception {
        when(service.compare(any(), any(), any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/inflation/compare")
                        .param("from", "2020-01-01")
                        .param("to",   "2026-05-01"))
                .andExpect(status().isNoContent());
    }

    @Test
    void compare_without_required_params_returns_400() throws Exception {
        mvc.perform(get("/api/v1/inflation/compare").param("from", "2020-01-01"))
                .andExpect(status().isBadRequest());
    }
}
