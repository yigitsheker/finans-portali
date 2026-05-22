package com.finansportali.backend.controller;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeRateController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class ExchangeRateControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private ExchangeRateService service;

    private ExchangeRate rate(String currency, double buy, double sell) {
        ExchangeRate r = new ExchangeRate();
        r.setCurrencyCode(currency);
        r.setBuyingRate(BigDecimal.valueOf(buy));
        r.setSellingRate(BigDecimal.valueOf(sell));
        r.setSource("TCMB");
        return r;
    }

    @Test
    void latest_returns_all_rates() throws Exception {
        when(service.getLatestRates()).thenReturn(List.of(
                rate("USD", 45.55, 45.65), rate("EUR", 52.85, 52.95)));

        mvc.perform(get("/api/v1/exchange-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currencyCode").value("USD"));
    }

    @Test
    void sources_returns_source_strings() throws Exception {
        when(service.getSources()).thenReturn(List.of("TCMB", "Yapi Kredi"));

        mvc.perform(get("/api/v1/exchange-rates/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("TCMB"));
    }

    @Test
    void by_source_routes_path_var() throws Exception {
        when(service.getRatesBySource("TCMB")).thenReturn(List.of(rate("USD", 45.55, 45.65)));

        mvc.perform(get("/api/v1/exchange-rates/source/TCMB"))
                .andExpect(status().isOk());

        verify(service).getRatesBySource("TCMB");
    }

    @Test
    void currency_history_routes_path_var() throws Exception {
        when(service.getCurrencyHistory("USD")).thenReturn(List.of(rate("USD", 45.55, 45.65)));

        mvc.perform(get("/api/v1/exchange-rates/currency/USD/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currencyCode").value("USD"));

        verify(service).getCurrencyHistory("USD");
    }
}
