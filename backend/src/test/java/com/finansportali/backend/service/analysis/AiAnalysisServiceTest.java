package com.finansportali.backend.service.analysis;

import com.finansportali.backend.dto.response.analysis.AnalysisInstrumentDto;
import com.finansportali.backend.dto.response.analysis.ChatResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Intent-routing tests for {@link AiAnalysisService}. The live-LLM branch is
 * suppressed via {@code when(llm.isEnabled()).thenReturn(false)} so each test
 * lands on the deterministic mock path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAnalysisServiceTest {

    @Mock private InstrumentAnalysisService instrumentService;
    @Mock private LlmClient llm;
    private AiAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AiAnalysisService(instrumentService, llm);
        when(llm.isEnabled()).thenReturn(false);
    }

    private static AnalysisInstrumentDto instr(String symbol, String name, String category) {
        AnalysisInstrumentDto d = new AnalysisInstrumentDto();
        d.setSymbol(symbol);
        d.setName(name);
        d.setCategory(category);
        d.setValue(new BigDecimal("123.45"));
        d.setCurrency("USD");
        d.setChangeDaily(new BigDecimal("1.50"));
        d.setChangeWeekly(new BigDecimal("2.75"));
        d.setChangeMonthly(new BigDecimal("5.10"));
        d.setChangeYearly(new BigDecimal("25.00"));
        d.setRiskLevel("MEDIUM");
        d.setShortTermSignal("HOLD");
        d.setLongTermSignal("BUY");
        return d;
    }

    // ── Budget intent ───────────────────────────────────────────────────

    @Test
    void budget_intent_returns_three_scenarios_tr() {
        ChatResponseDto r = service.generateReply("5000 TL'm var, ne yapayım?", "tr");
        assertThat(r.getScenarios()).hasSize(3);
        assertThat(r.getReply()).contains("5.000 TL").contains("senaryo");
        assertThat(r.getDisclaimer()).isNotNull().contains("yatırım tavsiyesi değildir");
        assertThat(r.getTimestamp()).isNotNull();
    }

    @Test
    void budget_intent_returns_three_scenarios_en() {
        ChatResponseDto r = service.generateReply("I have 8000 TL", "en");
        assertThat(r.getScenarios()).hasSize(3);
        assertThat(r.getScenarios().get(0).getLabel()).isEqualTo("Safe");
        assertThat(r.getScenarios().get(1).getLabel()).isEqualTo("Balanced");
        assertThat(r.getScenarios().get(2).getLabel()).isEqualTo("Aggressive");
        assertThat(r.getDisclaimer()).contains("not investment advice");
    }

    @Test
    void budget_scenarios_have_allocations_that_sum_to_100() {
        ChatResponseDto r = service.generateReply("10000 TL var", "tr");
        for (var s : r.getScenarios()) {
            int sum = s.getAllocations().stream().mapToInt(a -> a.getPercent()).sum();
            assertThat(sum).isEqualTo(100);
            assertThat(s.getDescription()).isNotBlank();
        }
    }

    @Test
    void budget_with_lira_keyword_also_matches() {
        ChatResponseDto r = service.generateReply("3500 lira ile yatırım", "tr");
        assertThat(r.getScenarios()).hasSize(3);
    }

    // ── Instrument intent ───────────────────────────────────────────────

    @Test
    void instrument_intent_matches_by_ticker_in_message() {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(instr("ASELS", "Aselsan", "STOCK")));
        ChatResponseDto r = service.generateReply("ASELS analiz", "tr");
        assertThat(r.getScenarios()).isNull();
        assertThat(r.getReply()).contains("Aselsan").contains("ASELS");
        assertThat(r.getReply()).contains("Risk seviyesi").contains("Orta");
        assertThat(r.getDisclaimer()).isNotNull();
    }

    @Test
    void instrument_intent_handles_bitcoin_keyword() {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(instr("BTCUSD", "Bitcoin", "CRYPTO")));
        ChatResponseDto r = service.generateReply("Bitcoin alınır mı?", "tr");
        assertThat(r.getReply()).contains("Bitcoin").contains("BTCUSD");
    }

    @Test
    void instrument_intent_handles_btc_token() {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(instr("BTCUSD", "Bitcoin", "CRYPTO")));
        // BTC alone is 3-letter so it matches the ticker regex, but we also
        // route the plain-name keyword path; either resolves to BTCUSD.
        ChatResponseDto r = service.generateReply("BTC nedir", "tr");
        assertThat(r.getReply()).contains("BTCUSD");
    }

    @Test
    void instrument_intent_handles_ethereum_keyword() {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(instr("ETHUSD", "Ethereum", "CRYPTO")));
        ChatResponseDto r = service.generateReply("Ethereum dipte mi?", "tr");
        assertThat(r.getReply()).contains("ETHUSD");
    }

    @Test
    void instrument_intent_handles_gold_keyword_en() {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(instr("XAUUSD", "Gold", "COMMODITY")));
        ChatResponseDto r = service.generateReply("Is gold a good buy?", "en");
        assertThat(r.getReply()).contains("XAUUSD");
        assertThat(r.getReply()).contains("Risk level").contains("Latest value");
    }

    @Test
    void instrument_intent_handles_altin_keyword_tr() {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(instr("XAUUSD", "Altın", "COMMODITY")));
        ChatResponseDto r = service.generateReply("altın yükselecek mi", "tr");
        assertThat(r.getReply()).contains("XAUUSD");
    }

    @Test
    void instrument_intent_falls_through_when_unknown_keyword_only() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        // Falls to help-fallback because no scenario / instrument matched.
        ChatResponseDto r = service.generateReply("random gibberish", "tr");
        assertThat(r.getReply()).contains("örnekler");
        assertThat(r.getScenarios()).isNull();
    }

    @Test
    void instrument_lookup_swallows_service_exceptions() {
        when(instrumentService.getAllInstruments())
                .thenThrow(new RuntimeException("db down"));
        ChatResponseDto r = service.generateReply("ASELS analiz", "tr");
        // Exception swallowed → falls through to help fallback.
        assertThat(r.getReply()).contains("örnekler");
        assertThat(r.getDisclaimer()).isNotNull();
    }

    @Test
    void teknik_analiz_without_symbol_prompts_user() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("teknik analiz yap", "tr");
        assertThat(r.getReply()).contains("sembol");
    }

    // ── Low-risk intent ─────────────────────────────────────────────────

    @Test
    void low_risk_keyword_tr_returns_low_risk_advice() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("düşük risk öneri", "tr");
        assertThat(r.getReply()).contains("Devlet tahvili");
    }

    @Test
    void low_risk_keyword_en_returns_low_risk_advice() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("low risk advice please", "en");
        assertThat(r.getReply()).contains("Government bonds");
    }

    @Test
    void guvenli_yatirim_keyword_also_triggers_low_risk() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("guvenli yatirim onerisi", "tr");
        assertThat(r.getReply()).contains("Devlet tahvili");
    }

    // ── Long-term intent ────────────────────────────────────────────────

    @Test
    void uzun_vade_keyword_tr_returns_long_term_advice() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("uzun vade yatırım", "tr");
        assertThat(r.getReply()).contains("Endeks fonu");
    }

    @Test
    void long_term_keyword_en_returns_long_term_advice() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("long term horizon", "en");
        assertThat(r.getReply()).contains("Index funds");
    }

    @Test
    void emeklilik_keyword_routes_to_long_term() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("emeklilik planı", "tr");
        assertThat(r.getReply()).contains("Endeks fonu");
    }

    // ── Fallback / edge cases ───────────────────────────────────────────

    @Test
    void null_message_falls_through_to_help() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply(null, "tr");
        assertThat(r.getReply()).contains("örnekler");
        assertThat(r.getDisclaimer()).isNotNull();
    }

    @Test
    void blank_message_falls_through_to_help() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("   ", "tr");
        assertThat(r.getReply()).contains("örnekler");
    }

    @Test
    void null_lang_defaults_to_turkish() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("hello", null);
        assertThat(r.getDisclaimer()).contains("yatırım tavsiyesi değildir");
    }

    @Test
    void blank_lang_defaults_to_turkish() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("hello", "");
        assertThat(r.getDisclaimer()).contains("yatırım tavsiyesi değildir");
    }

    @Test
    void english_help_response_uses_en_disclaimer() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        ChatResponseDto r = service.generateReply("???", "en");
        assertThat(r.getReply()).contains("Examples of what I can help with");
        assertThat(r.getDisclaimer()).contains("not investment advice");
    }

    @Test
    void live_llm_path_is_used_when_enabled() {
        when(llm.isEnabled()).thenReturn(true);
        when(llm.complete(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("Live LLM reply text");
        when(instrumentService.getAllInstruments()).thenReturn(List.of());

        // Use a message that DOESN'T hit budget or instrument intent, so the
        // live-LLM branch is what answers.
        ChatResponseDto r = service.generateReply("explain CDS spreads", "en");
        assertThat(r.getReply()).isEqualTo("Live LLM reply text");
        assertThat(r.getDisclaimer()).isNotNull();
    }

    @Test
    void live_llm_blank_reply_falls_through() {
        when(llm.isEnabled()).thenReturn(true);
        when(llm.complete(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("   ");
        when(instrumentService.getAllInstruments()).thenReturn(List.of());

        ChatResponseDto r = service.generateReply("explain CDS spreads", "en");
        // Blank LLM reply → service falls back to help / low / long branches.
        assertThat(r.getReply()).contains("Examples of what I can help with");
    }

    // ── localizeRisk(...) private helper ─────────────────────────────────

    @Test
    void localizeRisk_covers_every_label_tr_and_en() {
        // tr=true cases
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", "LOW", true)).isEqualTo("Düşük");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", "MEDIUM", true)).isEqualTo("Orta");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", "HIGH", true)).isEqualTo("Yüksek");
        // tr=false cases
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", "LOW", false)).isEqualTo("Low");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", "MEDIUM", false)).isEqualTo("Medium");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", "HIGH", false)).isEqualTo("High");
    }

    @Test
    void localizeRisk_returns_dash_for_null() {
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", (Object) null, true)).isEqualTo("—");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", (Object) null, false)).isEqualTo("—");
    }

    @Test
    void localizeRisk_passes_through_unknown_label() {
        // Default branch in the switch — unknown labels echo back verbatim.
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeRisk", "CRAZY", true)).isEqualTo("CRAZY");
    }

    // ── localizeSignal(...) private helper ───────────────────────────────

    @Test
    void localizeSignal_covers_every_label_tr_and_en() {
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "BUY", true)).isEqualTo("Al");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "SELL", true)).isEqualTo("Sat");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "HOLD", true)).isEqualTo("Tut");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "NEUTRAL", true)).isEqualTo("Nötr");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "BUY", false)).isEqualTo("Buy");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "SELL", false)).isEqualTo("Sell");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "HOLD", false)).isEqualTo("Hold");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "NEUTRAL", false)).isEqualTo("Neutral");
    }

    @Test
    void localizeSignal_returns_dash_for_null() {
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", (Object) null, true)).isEqualTo("—");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", (Object) null, false)).isEqualTo("—");
    }

    @Test
    void localizeSignal_passes_through_unknown_label() {
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "localizeSignal", "MAYBE", true)).isEqualTo("MAYBE");
    }

    // ── appendMarketContext(...) private helper ──────────────────────────

    @Test
    void appendMarketContext_appends_block_when_instruments_present() {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(
                        instr("AAPL", "Apple", "STOCK"),
                        instr("BTCUSD", "Bitcoin", "CRYPTO"),
                        instr("ASELS", "Aselsan", "STOCK")));

        String out = ReflectionTestUtils.invokeMethod(service, "appendMarketContext", "hi", "en");
        assertThat(out).startsWith("hi");
        // English context header + every symbol shows up.
        assertThat(out).contains("Latest market data");
        assertThat(out).contains("AAPL").contains("BTCUSD").contains("ASELS");
        // Daily change formatting included (uses %+.2f%%).
        assertThat(out).contains("daily");
        assertThat(out).contains("yearly");
    }

    @Test
    void appendMarketContext_uses_turkish_header_when_lang_tr() {
        when(instrumentService.getAllInstruments())
                .thenReturn(List.of(instr("THYAO", "Türk Hava Yolları", "STOCK")));

        String out = ReflectionTestUtils.invokeMethod(service, "appendMarketContext", "selam", "tr");
        assertThat(out).contains("Güncel piyasa verisi");
        assertThat(out).contains("THYAO");
    }

    @Test
    void appendMarketContext_returns_bare_message_on_exception() {
        when(instrumentService.getAllInstruments())
                .thenThrow(new RuntimeException("boom"));

        String out = ReflectionTestUtils.invokeMethod(service, "appendMarketContext", "hello", "en");
        // Catch branch swallows the throw and returns userMessage unchanged.
        assertThat(out).isEqualTo("hello");
    }

    @Test
    void appendMarketContext_returns_bare_message_when_no_instruments() {
        when(instrumentService.getAllInstruments()).thenReturn(List.of());
        String out = ReflectionTestUtils.invokeMethod(service, "appendMarketContext", "hello", "en");
        // Empty list → no header appended → same as input.
        assertThat(out).isEqualTo("hello");
    }
}
