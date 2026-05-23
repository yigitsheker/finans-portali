package com.finansportali.backend.service.client.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class NewsContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(NewsContentFetcher.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    // Selectors are ordered: site-specific first, then generic fallbacks.
    private static final String[] CONTENT_SELECTORS = {
            // Investing.com (current and legacy layouts)
            "div#article",
            "div[data-test=article-content]",
            "div.WYSIWYG.articlePage",
            "div.articlePage",
            // BloombergHT
            "div.haber-icerik",
            "div.news-detail-content",
            // Dunya.com
            "div.haber-detay-content",
            "div.haber-detay",
            // Hürriyet / Milliyet / Sabah
            "div.news-detail__content",
            "div.detay-icerik",
            "div.article-content",
            // Reuters / international
            "div[data-testid=ArticleBody]",
            "div.article-body__content",
            // NTV / CNN Türk
            "div#contentDetail",
            "div.detail-content",
            // Schema.org
            "div[itemprop=articleBody]",
            "[itemprop=articleBody]",
            // Generic conventions
            "article .article-body",
            "article .post-content",
            "article .entry-content",
            "article",
            ".article-content",
            ".article-body",
            ".post-content",
            ".entry-content",
            ".content-body",
            ".story-body",
            ".article__body",
            "main",
    };

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient playwrightClient;
    private final boolean playwrightEnabled;

    public NewsContentFetcher(
            @Value("${app.playwright.service-url:}") String playwrightServiceUrl) {
        this.playwrightEnabled = playwrightServiceUrl != null && !playwrightServiceUrl.isBlank();
        this.playwrightClient = playwrightEnabled
                ? RestClient.builder().baseUrl(playwrightServiceUrl).build()
                : null;
    }

    /**
     * A page looks blocked when the status is one of the typical bot-block
     * codes or when the body is too small to plausibly contain article text
     * (Cloudflare challenges are ~5KB; real articles are tens of KB).
     */
    private boolean looksBlocked(int status, String body) {
        return status == 403 || status == 401 || status == 503
                || body == null || body.length() < 5_000;
    }

    /**
     * Last-resort fetch via the playwright-service sidecar — a real headless
     * Chromium that bypasses Cloudflare JS challenges and TLS fingerprinting.
     * No-op when the service URL is not configured.
     */
    private String fetchWithPlaywright(String url) {
        if (!playwrightEnabled) return null;
        try {
            Map<String, Object> resp = playwrightClient.post()
                    .uri("/fetch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", url, "waitMs", 3000))
                    .retrieve()
                    .body(Map.class);
            if (resp == null) return null;
            Object html = resp.get("html");
            return html instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            log.warn("playwright fallback failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    public String fetchArticleContent(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            log.info("Fetching article content from: {}", url);

            // Minimal browser-like headers. Sites with Cloudflare bot detection
            // (e.g. investing.com) reject requests with too many headers (e.g.
            // Sec-Fetch-*, brotli encoding) when paired with Java's TLS fingerprint.
            // Keep this list short and aligned with what plain curl sends.
            Connection.Response resp = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .timeout(20000)
                    .method(Connection.Method.GET)
                    .execute();

            String body = resp.body();
            int status = resp.statusCode();
            log.info("Fetched {} bytes (status {}) from {}",
                    body == null ? 0 : body.length(), status, url);

            // Some sites (investing.com via Cloudflare) TLS-fingerprint Java
            // and serve a 403 challenge page. Fall back to system curl, which
            // has a different TLS stack that these CDNs already accept.
            if (looksBlocked(status, body)) {
                String curlBody = fetchWithCurl(url);
                if (curlBody != null && curlBody.length() > (body == null ? 0 : body.length())) {
                    log.info("curl fallback succeeded: {} bytes from {}", curlBody.length(), url);
                    body = curlBody;
                }
            }

            // Final fallback: a real headless Chrome. Slower (~2-5s) but
            // bypasses sites that fingerprint both Java and curl. Only used
            // when both prior tiers came back empty or challenge-page-sized.
            if (body == null || body.length() < 10_000) {
                String pwBody = fetchWithPlaywright(url);
                if (pwBody != null && pwBody.length() > (body == null ? 0 : body.length())) {
                    log.info("playwright fallback succeeded: {} bytes from {}", pwBody.length(), url);
                    body = pwBody;
                }
            }

            Document doc = Jsoup.parse(body == null ? "" : body, url);

            // 1) Most reliable: schema.org NewsArticle JSON-LD with articleBody.
            String fromJsonLd = extractFromJsonLd(doc);
            if (isSubstantial(fromJsonLd)) {
                log.info("Extracted {} chars via JSON-LD from {}", fromJsonLd.length(), url);
                return clip(fromJsonLd);
            }

            // 2) Site-specific / known content containers.
            String fromSelectors = extractFromSelectors(doc);
            if (isSubstantial(fromSelectors)) {
                log.info("Extracted {} chars via selector from {}", fromSelectors.length(), url);
                return clip(fromSelectors);
            }

            // 3) Last resort: collect all body paragraphs.
            String fromBody = extractFromBodyParagraphs(doc);
            if (isSubstantial(fromBody)) {
                log.info("Extracted {} chars via body paragraphs from {}", fromBody.length(), url);
                return clip(fromBody);
            }

            log.warn("Could not extract substantial content from: {}", url);
            return null;

        } catch (IOException e) {
            log.error("Failed to fetch article from {}: {}", url, e.getMessage());
            return null;
        }
    }

    // Restrict the curl fallback to plain http(s) URLs so we can never feed
    // ProcessBuilder anything that looks like a flag or shell metacharacter.
    // Belt + braces against S2076: ProcessBuilder's argv form already blocks
    // shell expansion, but Sonar still flags external input flowing into a
    // process exec — the pre-check here is the simplest way to silence the
    // finding while documenting the intent.
    private static final java.util.regex.Pattern SAFE_HTTP_URL =
            java.util.regex.Pattern.compile("^https?://[A-Za-z0-9._~%!$&'()*+,;=:@/?#\\-\\[\\]]+$");

    /**
     * Fallback fetcher using system curl. Cloudflare and similar CDNs
     * fingerprint Java's TLS stack and serve a 403 to Jsoup; curl has a
     * different TLS handshake that already passes those checks.
     * Returns null if curl is missing, times out, or returns non-2xx.
     */
    private String fetchWithCurl(String url) {
        if (url == null || !SAFE_HTTP_URL.matcher(url).matches()) {
            log.warn("curl fallback refused for non-http(s) URL: {}",
                    com.finansportali.backend.util.LogSanitizer.sanitize(url));
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-sSL",
                    "--max-time", "20",
                    "-A", USER_AGENT,
                    "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "-H", "Accept-Language: tr-TR,tr;q=0.9,en;q=0.8",
                    "--",
                    url
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    out.append(buf, 0, n);
                    if (out.length() > 5_000_000) break;
                }
            }
            if (!process.waitFor(25, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("curl timeout for {}", url);
                return null;
            }
            if (process.exitValue() != 0) {
                log.warn("curl exit {} for {}", process.exitValue(), url);
                return null;
            }
            String body = out.toString();
            return body.isBlank() ? null : body;
        } catch (Exception e) {
            log.warn("curl fallback failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Most modern news sites emit a <script type="application/ld+json"> block
     * with schema.org NewsArticle metadata. The articleBody field, when present,
     * is the cleanest possible source of the article text.
     */
    private String extractFromJsonLd(Document doc) {
        Elements scripts = doc.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String raw = script.data();
            if (raw == null || raw.isBlank()) continue;
            try {
                JsonNode node = JSON.readTree(raw);
                String body = findArticleBody(node);
                if (body != null && !body.isBlank()) {
                    return normalizeParagraphs(body);
                }
            } catch (Exception e) {
                // Some sites embed multiple JSON blobs or invalid JSON; ignore and try next.
            }
        }
        return null;
    }

    private String findArticleBody(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findArticleBody(child);
                if (found != null) return found;
            }
            return null;
        }
        if (node.isObject()) {
            JsonNode body = node.get("articleBody");
            if (body != null && body.isTextual() && !body.asText().isBlank()) {
                return body.asText();
            }
            // Some sites nest under @graph
            JsonNode graph = node.get("@graph");
            if (graph != null) {
                String found = findArticleBody(graph);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String extractFromSelectors(Document doc) {
        for (String selector : CONTENT_SELECTORS) {
            Elements elements = doc.select(selector);
            if (elements.isEmpty()) continue;
            Element container = elements.first();
            if (container == null) continue;
            String text = extractParagraphsFrom(container);
            if (isSubstantial(text)) return text;
        }
        return null;
    }

    private String extractParagraphsFrom(Element container) {
        // Strip noise — navigation, ads, related, scripts, social widgets.
        container.select(
                "script, style, iframe, nav, aside, footer, header, "
                        + ".advertisement, .ad, .ads, [class*=advert], "
                        + ".social-share, [class*=share], [class*=related], "
                        + "[class*=newsletter], [class*=subscribe], [class*=tag], "
                        + "figure, figcaption, .breadcrumb"
        ).remove();

        StringBuilder out = new StringBuilder();
        // Prefer <p> tags but also accept <li> inside lists and <h2/h3> as subheads.
        Elements blocks = container.select("p, li, h2, h3");
        for (Element b : blocks) {
            String text = b.text().trim();
            // Drop short fragments (captions, button labels) but keep mid-length sentences.
            if (text.length() >= 40) {
                out.append(text).append("\n\n");
            }
        }
        // If no <p> tags survived, fall back to raw container text.
        if (out.length() == 0) {
            String raw = container.text().trim();
            if (raw.length() >= 200) return raw;
            return null;
        }
        return out.toString().trim();
    }

    private String extractFromBodyParagraphs(Document doc) {
        Elements bodyParagraphs = doc.select("body p");
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (Element p : bodyParagraphs) {
            String text = p.text().trim();
            if (text.length() >= 60 && count < 30) {
                out.append(text).append("\n\n");
                count++;
            }
        }
        return out.toString().trim();
    }

    /**
     * Normalize whitespace and split clearly-separable sentences into paragraphs
     * when JSON-LD gives a single long string (common on Investing.com).
     */
    private String normalizeParagraphs(String raw) {
        String cleaned = raw.replace("\r", "")
                .replaceAll("[\\t\\u00A0]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
        // If already paragraph-broken, leave alone.
        if (cleaned.contains("\n\n") || cleaned.contains("\n")) {
            return cleaned.replaceAll("\n{3,}", "\n\n");
        }
        // Otherwise, break into ~3-sentence paragraphs at sentence boundaries.
        String[] sentences = cleaned.split("(?<=[.!?])\\s+(?=[A-ZÇĞİÖŞÜ\"'])");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sentences.length; i++) {
            out.append(sentences[i]);
            if ((i + 1) % 3 == 0) out.append("\n\n");
            else out.append(' ');
        }
        return out.toString().trim();
    }

    private boolean isSubstantial(String text) {
        return text != null && text.length() >= 200;
    }

    private String clip(String text) {
        if (text.length() <= 10000) return text;
        return text.substring(0, 10000);
    }
}
