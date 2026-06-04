package com.finansportali.backend.service.client.news;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

/**
 * Thin client over the in-cluster LibreTranslate HTTP API.
 *
 * <p>LibreTranslate exposes two endpoints we care about:
 * <ul>
 *   <li>{@code POST /detect} — body {@code {"q": "..."}} returns a list of
 *       {@code {confidence, language}} candidates sorted descending by score.
 *   <li>{@code POST /translate} — body {@code {"q": "...", "source": "tr",
 *       "target": "en", "format": "text"}} returns
 *       {@code {"translatedText": "..."}}.
 * </ul>
 *
 * <p>Failure mode: every call returns {@code null} on any error (timeout,
 * non-2xx, blank base URL, etc.) and logs at warn. The caller — NewsService
 * — falls back to the original-language text so the user never sees an empty
 * article.
 */
@Component
public class LibreTranslateClient {

    private static final Logger log = LoggerFactory.getLogger(LibreTranslateClient.class);

    // Bigger payloads — full article bodies can run a few KB. The default
    // 256KB WebClient codec limit would have to be raised for paragraphs
    // longer than that; 2MB is plenty for any realistic article.
    private static final int MAX_IN_MEMORY_BYTES = 2 * 1024 * 1024;

    private final WebClient webClient;
    private final boolean enabled;

    /** Wires the WebClient to the configured LibreTranslate base URL; disables the client when the URL is blank. */
    public LibreTranslateClient(@Value("${app.libretranslate.url:}") String baseUrl) {
        this.enabled = baseUrl != null && !baseUrl.isBlank();
        if (!enabled) {
            this.webClient = null;
            log.info("[Translate] LibreTranslate disabled (app.libretranslate.url is empty)");
            return;
        }

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(30)));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
        log.info("[Translate] LibreTranslate client wired to {}", baseUrl);
    }

    /** Whether a LibreTranslate base URL is configured (the client is active). */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Detect the dominant language of {@code text}. Returns the two-letter
     * ISO code ("tr", "en", …) or {@code null} on any failure. Only the
     * first ~200 chars are inspected — long articles overshoot the detector's
     * internal cost without improving accuracy.
     */
    @SuppressWarnings("unchecked")
    public String detect(String text) {
        if (!enabled || text == null || text.isBlank()) return null;
        String sample = text.length() > 200 ? text.substring(0, 200) : text;
        try {
            Object[] result = webClient.post()
                    .uri("/detect")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("q", sample))
                    .retrieve()
                    .bodyToMono(Object[].class)
                    .block();
            if (result == null || result.length == 0) return null;
            Map<String, Object> top = (Map<String, Object>) result[0];
            Object lang = top.get("language");
            return lang != null ? lang.toString() : null;
        } catch (RuntimeException e) {
            log.warn("[Translate] detect failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Translate {@code text} from {@code source} to {@code target} (both
     * two-letter ISO codes). Returns the translated string, or {@code null}
     * if LibreTranslate fails or is disabled — callers should treat null as
     * "fall back to original".
     */
    @SuppressWarnings("unchecked")
    public String translate(String text, String source, String target) {
        if (!enabled || text == null || text.isBlank()) return null;
        if (source != null && source.equalsIgnoreCase(target)) return text;
        try {
            Map<String, Object> result = webClient.post()
                    .uri("/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "q", text,
                            "source", source != null ? source : "auto",
                            "target", target,
                            "format", "text"))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (result == null) return null;
            Object translated = result.get("translatedText");
            return translated != null ? translated.toString() : null;
        } catch (RuntimeException e) {
            log.warn("[Translate] translate {}→{} failed: {}", source, target, e.getMessage());
            return null;
        }
    }
}
