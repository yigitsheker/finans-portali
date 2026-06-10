package com.finansportali.backend.controller;

import com.finansportali.backend.service.viop.ViopPositionService;
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
 * MockMvc integration tests for the VİOP (futures) SIMULATION trade endpoints
 * (/api/v1/portfolio/viop). Verifies routing, JWT-derived user, and bean
 * validation — the service (net-position math) is mocked + covered separately.
 */
@WebMvcTest(ViopTradeController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
class ViopTradeControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private ViopPositionService service;

    private static final String SUB = "user-1";
    private static final String OPEN_JSON = "{\"contractSymbol\":\"F_XU0300625\",\"direction\":\"LONG\",\"quantity\":2}";
    private static final String CLOSE_JSON = "{\"contractSymbol\":\"F_XU0300625\",\"quantity\":1}";

    @Test
    void open_routes_payload_to_service() throws Exception {
        mvc.perform(post("/api/v1/portfolio/viop/positions/open")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_JSON))
                .andExpect(status().isOk());
        verify(service).open(eq(SUB), eq("F_XU0300625"), any(), any(), any());
    }

    @Test
    void open_rejects_invalid_payload_with_400() throws Exception {
        // blank symbol + missing direction/quantity → bean validation fails.
        mvc.perform(post("/api/v1/portfolio/viop/positions/open")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contractSymbol\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void close_routes_payload_to_service() throws Exception {
        mvc.perform(post("/api/v1/portfolio/viop/positions/close")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CLOSE_JSON))
                .andExpect(status().isOk());
        verify(service).close(eq(SUB), eq("F_XU0300625"), any(), any());
    }

    @Test
    void positions_lists_user_positions() throws Exception {
        when(service.list(SUB)).thenReturn(List.of());
        mvc.perform(get("/api/v1/portfolio/viop/positions").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).list(SUB);
    }

    @Test
    void transactions_lists_user_movements() throws Exception {
        when(service.transactions(SUB)).thenReturn(List.of());
        mvc.perform(get("/api/v1/portfolio/viop/transactions").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).transactions(SUB);
    }

    @Test
    void summary_forwards_to_service() throws Exception {
        mvc.perform(get("/api/v1/portfolio/viop/summary").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).summary(SUB);
    }

    @Test
    void preview_routes_to_service() throws Exception {
        mvc.perform(post("/api/v1/portfolio/viop/preview")
                        .with(jwt().jwt(j -> j.subject(SUB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_JSON))
                .andExpect(status().isOk());
        verify(service).preview(eq(SUB), eq("F_XU0300625"), any(), any(), any());
    }
}
