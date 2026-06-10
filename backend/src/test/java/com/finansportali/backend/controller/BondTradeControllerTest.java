package com.finansportali.backend.controller;

import com.finansportali.backend.service.bond.BondPositionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for the bond/bill SIMULATION trade endpoints
 * (/api/v1/portfolio/bonds). Verifies routing, JWT-derived user, and bean
 * validation — the service layer is mocked (its math is covered separately).
 */
@WebMvcTest(BondTradeController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
class BondTradeControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private BondPositionService service;

    private static final String SUB = "user-1";
    private static final String BUY_JSON = "{\"identifier\":\"TRT123\",\"nominal\":1000,\"cleanPrice\":97.5}";
    private static final String SELL_JSON = "{\"identifier\":\"TRT123\",\"nominal\":500,\"cleanPrice\":98.0}";

    @Test
    void buy_routes_payload_to_service() throws Exception {
        mvc.perform(post("/api/v1/portfolio/bonds/buy")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BUY_JSON))
                .andExpect(status().isOk());
        verify(service).buy(eq(SUB), eq("TRT123"), any(), any(), any(), any(), any());
    }

    @Test
    void buy_rejects_invalid_payload_with_400() throws Exception {
        // blank identifier + missing nominal/cleanPrice → bean validation fails.
        mvc.perform(post("/api/v1/portfolio/bonds/buy")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"identifier\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sell_routes_payload_to_service() throws Exception {
        mvc.perform(post("/api/v1/portfolio/bonds/sell")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELL_JSON))
                .andExpect(status().isOk());
        verify(service).sell(eq(SUB), eq("TRT123"), any(), any(), any(), any());
    }

    @Test
    void positions_lists_user_bonds() throws Exception {
        when(service.list(SUB)).thenReturn(List.of());
        mvc.perform(get("/api/v1/portfolio/bonds/positions").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).list(SUB);
    }

    @Test
    void transactions_lists_user_movements() throws Exception {
        when(service.transactions(SUB)).thenReturn(List.of());
        mvc.perform(get("/api/v1/portfolio/bonds/transactions").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).transactions(SUB);
    }

    @Test
    void summary_forwards_to_service() throws Exception {
        mvc.perform(get("/api/v1/portfolio/bonds/summary").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).summary(SUB);
    }

    @Test
    void preview_buy_routes_to_service() throws Exception {
        mvc.perform(post("/api/v1/portfolio/bonds/preview/buy")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BUY_JSON))
                .andExpect(status().isOk());
        verify(service).previewBuy(eq("TRT123"), any(), any(), any(), any(), any());
    }

    @Test
    void preview_sell_routes_to_service() throws Exception {
        mvc.perform(post("/api/v1/portfolio/bonds/preview/sell")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELL_JSON))
                .andExpect(status().isOk());
        verify(service).previewSell(eq(SUB), eq("TRT123"), any(), any(), any(), any());
    }
}
