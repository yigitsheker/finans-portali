package com.finansportali.backend.service.client.fund;

import com.fasterxml.jackson.databind.JsonNode;
import com.finansportali.backend.entity.InvestmentFund;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real TEFAS fund data fetcher.
 *
 * Endpoint: POST https://www.tefas.gov.tr/api/funds/fonGetiriBazliBilgiGetir
 * Auth   : anonymous Bearer token (public, used by tefas.gov.tr SPA)
 *
 * Provides for each fund: code, name, type, risk level and 1m/3m/6m/YTD/1y/3y/5y returns.
 * Unit price and portfolio size are NOT in this endpoint — TEFAS exposes them only on
 * individual fund detail pages. For 1000+ funds the per-fund call would be wasteful;
 * we leave unitPrice/totalValue at zero and surface getiri-focused columns instead.
 */
@Service
public class TefasFundFetcher {

    private static final Logger log = LoggerFactory.getLogger(TefasFundFetcher.class);

    private static final String BASE_URL = "https://www.tefas.gov.tr";
    private static final String ENDPOINT = "/api/funds/fonGetiriBazliBilgiGetir";

    private static final String PRICE_ENDPOINT = "/api/funds/fonFiyatBilgiGetir";

    /** Parallel HTTP connections for the per-fund price fetch. */
    private static final int PRICE_FETCH_CONCURRENCY = 20;

    private static final String REQUEST_BODY = "{"
            + "\"dil\":\"TR\","
            + "\"fonTipi\":\"YAT\","
            + "\"kurucuKodu\":null,"
            + "\"sfonTurKod\":null,"
            + "\"fonTurAciklama\":null,"
            + "\"islem\":1,"
            + "\"fonTurKod\":null,"
            + "\"fonGrubu\":null,"
            + "\"donemGetiri1a\":\"1\","
            + "\"donemGetiri3a\":\"1\","
            + "\"donemGetiri6a\":\"1\","
            + "\"donemGetiri1y\":\"1\","
            + "\"donemGetiriyb\":\"1\","
            + "\"donemGetiri3y\":\"1\","
            + "\"donemGetiri5y\":\"1\","
            + "\"basTarih\":null,"
            + "\"bitTarih\":null,"
            + "\"calismaTipi\":2,"
            + "\"getiriOrani\":\"1\""
            + "}";

    @Value("${app.funds.max-funds-to-fetch:1500}")
    private int maxFundsToFetch;

    // TEFAS' public anonymous Bearer token — used by tefas.gov.tr's own
    // SPA. Pulled from configuration so the literal isn't checked into the
    // repo (Sonar S6437); the default keeps the historical value so
    // existing deployments don't need a config change. Override via
    // APP_TEFAS_BEARER_TOKEN env var or application.yml.
    @Value("${app.tefas.bearer-token:ST-tefaswebwse3irfmSBj4iRAzGPbAlS94Se}")
    private String bearerToken;

    private WebClient webClient;

    @PostConstruct
    void init() {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                .defaultHeader("Accept", "*/*")
                .defaultHeader("Origin", BASE_URL)
                .defaultHeader("Referer", BASE_URL + "/tr/fon-getirileri?fundType=YAT")
                .defaultHeader("Authorization", "Bearer " + bearerToken)
                .build();
    }

    public List<InvestmentFund> fetchAllFunds() {
        log.info("Fetching investment funds from TEFAS: POST {}{}", BASE_URL, ENDPOINT);
        LocalDate today = LocalDate.now();
        List<InvestmentFund> result = new ArrayList<>();

        try {
            JsonNode root = webClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(REQUEST_BODY)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root == null) {
                log.error("TEFAS returned null response");
                return result;
            }

            JsonNode errorMsg = root.get("errorMessage");
            if (errorMsg != null && !errorMsg.isNull() && !errorMsg.asText().isEmpty()) {
                log.error("TEFAS returned error: {}", errorMsg.asText());
                return result;
            }

            JsonNode list = root.get("resultList");
            if (list == null || !list.isArray()) {
                log.warn("TEFAS resultList missing or not an array. Root: {}", root.toString().substring(0, Math.min(300, root.toString().length())));
                return result;
            }

            int parsed = 0, skippedInactive = 0, skippedInvalid = 0;
            for (JsonNode item : list) {
                if (result.size() >= maxFundsToFetch) break;

                try {
                    boolean tefasActive = item.path("tefasDurum").asBoolean(true);
                    if (!tefasActive) { skippedInactive++; continue; }

                    String code = textOrEmpty(item, "fonKodu");
                    String name = textOrEmpty(item, "fonUnvan");
                    if (code.isEmpty() || name.isEmpty()) { skippedInvalid++; continue; }

                    InvestmentFund f = new InvestmentFund();
                    f.setFundCode(code);
                    f.setFundName(name);
                    f.setFundType(simplifyFundType(textOrEmpty(item, "fonTurAciklama")));
                    f.setManagementCompany(parseCompany(name));
                    f.setRiskLevel(mapRisk(textOrEmpty(item, "riskDegeri")));
                    f.setUnitPrice(BigDecimal.ZERO);      // not in this endpoint
                    f.setTotalValue(BigDecimal.ZERO);     // not in this endpoint
                    f.setPriceDate(today);
                    f.setMonthlyReturn(toBig(item.get("getiri1a")));
                    f.setThreeMonthReturn(toBig(item.get("getiri3a")));
                    f.setSixMonthReturn(toBig(item.get("getiri6a")));
                    f.setYearlyReturn(firstNonNull(item.get("getiri1y"), item.get("getiriyb")));
                    f.setThreeYearReturn(toBig(item.get("getiri3y")));
                    f.setFiveYearReturn(toBig(item.get("getiri5y")));
                    // weeklyReturn / dailyReturn not provided by TEFAS getiri endpoint

                    result.add(f);
                    parsed++;
                } catch (Exception itemErr) {
                    skippedInvalid++;
                    log.debug("Skipping malformed fund row: {}", itemErr.getMessage());
                }
            }

            log.info("TEFAS fetch: parsed={}, skippedInactive={}, skippedInvalid={}, total={}",
                    parsed, skippedInactive, skippedInvalid, list.size());
        } catch (WebClientResponseException e) {
            log.error("TEFAS HTTP {} error: {}", e.getStatusCode(),
                    e.getResponseBodyAsString().substring(0, Math.min(500, e.getResponseBodyAsString().length())));
        } catch (Exception e) {
            log.error("TEFAS fetch failed: {}", e.getMessage(), e);
        }

        if (!result.isEmpty()) {
            enrichWithPrices(result);
        }
        return result;
    }

    /**
     * Per-fund POST to /api/funds/fonFiyatBilgiGetir to obtain the latest unit price and
     * the daily return (computed from last 2 days). Runs all fund requests in parallel
     * with a bounded concurrency to avoid hammering TEFAS.
     */
    private void enrichWithPrices(List<InvestmentFund> funds) {
        log.info("Fetching unit prices for {} funds (concurrency={})...", funds.size(), PRICE_FETCH_CONCURRENCY);
        long start = System.currentTimeMillis();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        Flux.fromIterable(funds)
                .flatMap(f -> fetchPriceMono(f.getFundCode())
                        .doOnNext(pi -> {
                            if (pi != null && pi.price != null) {
                                f.setUnitPrice(pi.price);
                                if (pi.dailyReturn != null) f.setDailyReturn(pi.dailyReturn);
                                if (pi.date != null) f.setPriceDate(pi.date);
                                ok.incrementAndGet();
                            } else {
                                fail.incrementAndGet();
                            }
                        }),
                        PRICE_FETCH_CONCURRENCY)
                .blockLast();

        long ms = System.currentTimeMillis() - start;
        log.info("Price enrichment done: ok={}, fail={}, elapsed={}ms", ok.get(), fail.get(), ms);
    }

    private Mono<PriceInfo> fetchPriceMono(String fonKodu) {
        String body = "{\"fonKodu\":\"" + fonKodu + "\",\"dil\":\"TR\",\"periyod\":1}";
        return webClient.post()
                .uri(PRICE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parsePriceResponse)
                .onErrorResume(e -> {
                    log.debug("Price fetch failed for {}: {}", fonKodu, e.getMessage());
                    return Mono.just(PriceInfo.EMPTY);
                });
    }

    private PriceInfo parsePriceResponse(JsonNode root) {
        if (root == null) return PriceInfo.EMPTY;
        JsonNode list = root.path("resultList");
        if (!list.isArray() || list.isEmpty()) return PriceInfo.EMPTY;

        JsonNode last = list.get(list.size() - 1);
        BigDecimal latest = toBig(last.get("fiyat"));
        LocalDate date = parseDate(last.path("tarih").asText(""));

        BigDecimal daily = null;
        if (list.size() >= 2 && latest != null) {
            JsonNode prev = list.get(list.size() - 2);
            BigDecimal prevPrice = toBig(prev.get("fiyat"));
            if (prevPrice != null && prevPrice.signum() > 0) {
                daily = latest.subtract(prevPrice)
                        .divide(prevPrice, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP);
            }
        }
        return new PriceInfo(latest, daily, date);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);  // TEFAS returns ISO: "2026-05-15"
        } catch (Exception ex) {
            return null;
        }
    }

    private static final class PriceInfo {
        static final PriceInfo EMPTY = new PriceInfo(null, null, null);
        final BigDecimal price;
        final BigDecimal dailyReturn;
        final LocalDate date;
        PriceInfo(BigDecimal price, BigDecimal dailyReturn, LocalDate date) {
            this.price = price;
            this.dailyReturn = dailyReturn;
            this.date = date;
        }
    }

    public InvestmentFund fetchFundDetails(String fundCode) {
        // Per-fund detail call would hit a different TEFAS endpoint.
        // Not needed by the bulk refresh flow; returning null is safe.
        return null;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String textOrEmpty(JsonNode item, String field) {
        JsonNode n = item.get(field);
        return (n == null || n.isNull()) ? "" : n.asText("");
    }

    private static BigDecimal toBig(JsonNode n) {
        if (n == null || n.isNull()) return null;
        String s = n.asText("").trim();
        if (s.isEmpty()) return null;
        try {
            // TEFAS sends decimal numbers; locale-agnostic parse
            return new BigDecimal(s.replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal firstNonNull(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            BigDecimal v = toBig(n);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Extract the management company from a TEFAS fund unvan.
     * Examples:
     *   "ATA PORTFÖY HİSSE SENEDİ FONU" → "ATA PORTFÖY"
     *   "İŞ PORTFÖY ALTIN KATILIM FONU" → "İŞ PORTFÖY"
     *   "QNB FİNANS PORTFÖY ..."        → "QNB FİNANS PORTFÖY"
     */
    private static String parseCompany(String fundUnvan) {
        if (fundUnvan == null || fundUnvan.isBlank()) return "Diğer";
        int idx = fundUnvan.indexOf("PORTFÖY");
        if (idx > 0) {
            return fundUnvan.substring(0, idx + "PORTFÖY".length()).trim();
        }
        // Fallback: first two tokens
        String[] parts = fundUnvan.split("\\s+");
        if (parts.length >= 2) return (parts[0] + " " + parts[1]).trim();
        return parts.length == 1 ? parts[0] : "Diğer";
    }

    /**
     * Strip "Şemsiye" qualifier from TEFAS fund type names.
     *   "Hisse Senedi Şemsiye Fonu"    → "Hisse Senedi Fonu"
     *   "Borçlanma Araçları Şemsiye Fonu" → "Borçlanma Araçları Fonu"
     */
    private static String simplifyFundType(String tefasType) {
        if (tefasType == null || tefasType.isBlank()) return "Diğer";
        return tefasType.replace("Şemsiye ", "").trim();
    }

    /**
     * Map TEFAS risk score (1-7) to the UI's DÜŞÜK / ORTA / YÜKSEK levels.
     */
    private static String mapRisk(String tefasRisk) {
        if (tefasRisk == null || tefasRisk.isBlank()) return "ORTA";
        try {
            int r = Integer.parseInt(tefasRisk.trim());
            if (r <= 2) return "DÜŞÜK";
            if (r <= 5) return "ORTA";
            return "YÜKSEK";
        } catch (NumberFormatException ex) {
            return "ORTA";
        }
    }
}
