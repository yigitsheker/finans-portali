package com.finansportali.backend.controller;

import com.finansportali.backend.dto.request.analysis.ChatRequestDto;
import com.finansportali.backend.dto.response.analysis.AnalysisDetailDto;
import com.finansportali.backend.dto.response.analysis.AnalysisInstrumentDto;
import com.finansportali.backend.dto.response.analysis.ChatResponseDto;
import com.finansportali.backend.service.analysis.AiAnalysisService;
import com.finansportali.backend.service.analysis.InstrumentAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Analysis page back-end. All three endpoints are authenticated — the route
 * itself only renders for logged-in users, and the backend also rejects
 * anonymous calls so the chatbot can't be hit anonymously.
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final InstrumentAnalysisService instrumentService;
    private final AiAnalysisService aiService;

    public AnalysisController(InstrumentAnalysisService instrumentService,
                              AiAnalysisService aiService) {
        this.instrumentService = instrumentService;
        this.aiService = aiService;
    }

    /** Full cross-asset list with daily/weekly/monthly/yearly change + signals. */
    @GetMapping("/instruments")
    public List<AnalysisInstrumentDto> getInstruments() {
        return instrumentService.getAllInstruments();
    }

    /** Detail view used by the table row's click-to-expand card. */
    @GetMapping("/instruments/{symbol}")
    public ResponseEntity<AnalysisDetailDto> getInstrumentDetail(@PathVariable String symbol) {
        return instrumentService.getDetail(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Chatbot turn — single request/response, server-side AI handles intent. */
    @PostMapping("/chat")
    public ChatResponseDto chat(@Valid @RequestBody ChatRequestDto request) {
        return aiService.generateReply(request.getMessage(), request.getLang());
    }
}
