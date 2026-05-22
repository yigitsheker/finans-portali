package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.market.MarketHistoryPoint;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.service.MarketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class MarketControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private MarketService service;

    @Test
    void summary_returns_items_with_change_data() throws Exception {
        when(service.summary()).thenReturn(List.of(
                new MarketSummaryItem("THYAO", "Türk Hava Yolları", "BIST",
                        new BigDecimal("294.50"),
                        new BigDecimal("-4.49"),
                        new BigDecimal("-1.50"),
                        Instant.parse("2026-05-19T10:00:00Z"),
                        true,
                        "Gecikmeli")));

        mvc.perform(get("/api/v1/market/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("THYAO"))
                .andExpect(jsonPath("$[0].changePct").value(-1.50))
                .andExpect(jsonPath("$[0].delayed").value(true));
    }

    @Test
    void history_forwards_symbol_and_default_period() throws Exception {
        when(service.history(eq("THYAO"), eq("30D"))).thenReturn(List.of(
                new MarketHistoryPoint(java.time.LocalDate.parse("2026-05-18"), new BigDecimal("300.00"), "18 May", 1758110400L),
                new MarketHistoryPoint(java.time.LocalDate.parse("2026-05-19"), new BigDecimal("294.50"), "19 May", 1758196800L)));

        mvc.perform(get("/api/v1/market/history").param("symbol", "THYAO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].close").value(300.00))
                .andExpect(jsonPath("$[1].close").value(294.50));

        verify(service).history("THYAO", "30D");
    }

    @Test
    void history_forwards_custom_period() throws Exception {
        when(service.history("BTCUSD", "1Y")).thenReturn(List.of());

        mvc.perform(get("/api/v1/market/history")
                        .param("symbol", "BTCUSD")
                        .param("period", "1Y"))
                .andExpect(status().isOk());

        verify(service).history("BTCUSD", "1Y");
    }

    @Test
    void history_batch_returns_map_of_symbols() throws Exception {
        // Note the order — LinkedHashMap preserves insertion order in the
        // JSON output, which is what the front-end relies on.
        when(service.history("THYAO", "1M")).thenReturn(List.of(
                new MarketHistoryPoint(java.time.LocalDate.parse("2026-05-19"), new BigDecimal("294.50"), "19 May", 1758196800L)));
        when(service.history("GARAN", "1M")).thenReturn(List.of(
                new MarketHistoryPoint(java.time.LocalDate.parse("2026-05-19"), new BigDecimal("129.30"), "19 May", 1758196800L)));

        mvc.perform(get("/api/v1/market/history/batch")
                        .param("symbols", "THYAO,GARAN")
                        .param("period", "1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.THYAO[0].close").value(294.50))
                .andExpect(jsonPath("$.GARAN[0].close").value(129.30));
    }

    @Test
    void history_batch_caps_at_100_symbols() throws Exception {
        // The cap defends against query-string blow-up; symbols 101+ are
        // silently dropped. We just verify the call still returns 200 with
        // the first batch trimmed; counting the keys is sufficient.
        when(service.history(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            if (i > 0) sb.append(',');
            sb.append('S').append(i);
        }
        mvc.perform(get("/api/v1/market/history/batch")
                        .param("symbols", sb.toString()))
                .andExpect(status().isOk());

        // 100 symbols × service.history → at most 100 mock calls.
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(100))
                .history(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("30D"));
    }

    @Test
    void instruments_lists_known_symbols() throws Exception {
        MarketInstrument m = new MarketInstrument();
        m.setSymbol("AAPL");
        m.setName("Apple");
        m.setInstrumentType(InstrumentType.STOCK);
        when(service.instruments()).thenReturn(List.of(m));

        mvc.perform(get("/api/v1/market/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    void price_returns_latest_for_symbol() throws Exception {
        when(service.latestPrice("THYAO")).thenReturn(Map.of(
                "symbol", "THYAO",
                "price", new BigDecimal("294.50"),
                "delayed", true));

        mvc.perform(get("/api/v1/market/price").param("symbol", "THYAO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("THYAO"))
                .andExpect(jsonPath("$.price").value(294.50));
    }
}
