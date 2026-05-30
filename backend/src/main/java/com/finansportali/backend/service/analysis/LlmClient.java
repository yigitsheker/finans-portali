package com.finansportali.backend.service.analysis;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin OpenAI-compatible chat-completions client.
 *
 * <p>Targets the {@code POST {baseUrl}/chat/completions} endpoint that
 * OpenAI defined and Groq, Together, Anyscale, Fireworks, OpenRouter,
 * Mistral et al. all implement byte-for-byte. Same request shape, same
 * response shape — swap the base URL + API key + model name and you're
 * on a different provider.
 *
 * <p>{@link #isEnabled()} returns {@code false} when no API key is
 * configured. AiAnalysisService checks this before delegating, so the
 * backend boots cleanly without a key and only flips to live LLM
 * responses once {@code APP_LLM_API_KEY} is set.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    // 6 MB — Groq/OpenAI responses with reasoning_content can run several
    // hundred KB; bumping past Spring's 256 KB default avoids truncation.
    private static final int MAX_IN_MEMORY_BYTES = 6 * 1024 * 1024;

    private static final String KEY_CONTENT = "content";

    private final WebClient webClient;
    private final boolean enabled;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public LlmClient(
            @Value("${app.llm.base-url:}") String baseUrl,
            @Value("${app.llm.api-key:}") String apiKey,
            @Value("${app.llm.model:gpt-4o-mini}") String model,
            @Value("${app.llm.max-tokens:600}") int maxTokens,
            @Value("${app.llm.temperature:0.4}") double temperature,
            @Value("${app.llm.timeout-seconds:30}") int timeoutSeconds) {

        this.enabled = apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        if (!enabled) {
            this.webClient = null;
            log.info("[LLM] disabled — set APP_LLM_API_KEY to enable real chatbot responses");
            return;
        }

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(timeoutSeconds)));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
        log.info("[LLM] enabled — provider={} model={}", baseUrl, model);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Single-turn chat completion. Returns the assistant's reply text, or
     * {@code null} on any error (timeout, non-2xx, unparseable response).
     * Callers — AiAnalysisService — treat null as "fall back to the local
     * mock" so a flaky upstream never breaks the UI.
     */
    @SuppressWarnings("unchecked")
    public String complete(String systemPrompt, String userMessage) {
        if (!enabled) return null;
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", KEY_CONTENT, systemPrompt));
            }
            messages.add(Map.of("role", "user", KEY_CONTENT, userMessage));

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );

            Map<?, ?> resp = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return null;
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) return null;
            Object content = message.get(KEY_CONTENT);
            return content == null ? null : content.toString();
        } catch (RuntimeException e) {
            log.warn("[LLM] completion failed: {}", e.getMessage());
            return null;
        }
    }
}
