package com.finansportali.backend.controller;

import com.finansportali.backend.entity.DepositRatePoint;
import com.finansportali.backend.service.DepositRateService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepositRateController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class DepositRateControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private DepositRateService service;

    private DepositRatePoint point(String currency, double avg) {
        DepositRatePoint p = new DepositRatePoint();
        p.setPeriodDate(LocalDate.of(2026, 5, 1));
        p.setCurrency(currency);
        p.setRate1m(BigDecimal.valueOf(avg));
        p.setRateAvg(BigDecimal.valueOf(avg));
        return p;
    }

    @Test
    void getAll_defaults_to_try() throws Exception {
        when(service.getAllForCurrency("TRY")).thenReturn(List.of(point("TRY", 45.0)));

        mvc.perform(get("/api/v1/deposit-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("TRY"))
                .andExpect(jsonPath("$[0].rateAvg").value(45.0));

        verify(service).getAllForCurrency("TRY");
    }

    @Test
    void getAll_upper_cases_currency_param() throws Exception {
        when(service.getAllForCurrency("USD")).thenReturn(List.of(point("USD", 2.5)));

        mvc.perform(get("/api/v1/deposit-rates").param("currency", "usd"))
                .andExpect(status().isOk());

        verify(service).getAllForCurrency("USD");
    }

    @Test
    void getLatest_returns_keyed_map() throws Exception {
        when(service.getLatest("TRY")).thenReturn(Optional.of(point("TRY", 45.0)));
        when(service.getLatest("USD")).thenReturn(Optional.of(point("USD", 2.5)));
        when(service.getLatest("EUR")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/deposit-rates/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TRY.rateAvg").value(45.0))
                .andExpect(jsonPath("$.USD.rateAvg").value(2.5));
    }

    @Test
    void getLatestForCurrency_returns_200_when_present() throws Exception {
        when(service.getLatest("TRY")).thenReturn(Optional.of(point("TRY", 45.0)));

        mvc.perform(get("/api/v1/deposit-rates/latest/try"))
                .andExpect(status().isOk());

        verify(service).getLatest("TRY");
    }

    @Test
    void getLatestForCurrency_returns_204_when_absent() throws Exception {
        when(service.getLatest("EUR")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/deposit-rates/latest/EUR"))
                .andExpect(status().isNoContent());
    }
}
