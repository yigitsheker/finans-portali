package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.analysis.AnalysisDetailDto;
import com.finansportali.backend.dto.response.analysis.AnalysisInstrumentDto;
import com.finansportali.backend.dto.response.analysis.ChatResponseDto;
import com.finansportali.backend.exception.GlobalExceptionHandler;
import com.finansportali.backend.service.analysis.AiAnalysisService;
import com.finansportali.backend.service.analysis.InstrumentAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@WithMockUser
class AnalysisControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private InstrumentAnalysisService instrumentService;
    @MockitoBean private AiAnalysisService aiService;

    private static AnalysisInstrumentDto sample(String symbol) {
        AnalysisInstrumentDto d = new AnalysisInstrumentDto();
        d.setSymbol(symbol);
        d.setName(symbol + " Co");
        d.setCategory("STOCK");
        return d;
    }

    @Test
    void list_instruments_returns_aggregator_output() throws Exception {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(sample("AAPL"), sample("MSFT")));

        mvc.perform(get("/api/v1/analysis/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].symbol").value("MSFT"));
    }

    @Test
    void list_instruments_empty() throws Exception {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        mvc.perform(get("/api/v1/analysis/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void detail_returns_200_when_present() throws Exception {
        AnalysisDetailDto d = new AnalysisDetailDto();
        d.setSummary(sample("AAPL"));
        d.setTrend("UP");
        d.setVolatility("LOW");
        d.setShortTermNote("ok");
        d.setLongTermNote("ok");
        d.setRiskNote("ok");
        when(instrumentService.getDetail("AAPL")).thenReturn(Optional.of(d));

        mvc.perform(get("/api/v1/analysis/instruments/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.symbol").value("AAPL"))
                .andExpect(jsonPath("$.trend").value("UP"));
    }

    @Test
    void detail_returns_404_when_missing() throws Exception {
        when(instrumentService.getDetail("MISSING")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/analysis/instruments/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void chat_echoes_service_reply() throws Exception {
        ChatResponseDto resp = new ChatResponseDto();
        resp.setReply("hello back");
        resp.setDisclaimer("not advice");
        when(aiService.generateReply(eq("hi"), eq("tr"))).thenReturn(resp);

        mvc.perform(post("/api/v1/analysis/chat").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\",\"lang\":\"tr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("hello back"))
                .andExpect(jsonPath("$.disclaimer").value("not advice"));
    }

    @Test
    void chat_rejects_blank_message() throws Exception {
        mvc.perform(post("/api/v1/analysis/chat").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\",\"lang\":\"tr\"}"))
                .andExpect(status().isBadRequest());
    }
}
