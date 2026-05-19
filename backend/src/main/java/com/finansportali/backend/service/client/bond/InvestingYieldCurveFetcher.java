package com.finansportali.backend.service.client.bond;

import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import com.finansportali.backend.entity.DebtInstrumentType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scrapes Investing.com TR's "Türkiye Devlet Tahvilleri" page for the full
 * Turkish TL gösterge yield curve (3M / 6M / 9M / 1Y / 2Y / 5Y / 10Y / 30Y).
 *
 * <p>The page is Akamai-protected against direct HTTP, so the fetch goes
 * through the existing {@code playwright-service} sidecar — the same
 * mechanism we use for VIOP and Cloudflare-blocked news sites.
 *
 * <p>Curve points use synthetic symbols (TR3MO, TR6MO, …) so they don't
 * clash with the specific-bond EVDS rows (TR2YT/TR3YT/TR4YT). Maturity
 * is computed from today + tenor; price is left at par 100 because the
 * page only publishes the yield, not a clean price.
 *
 * <p>This was added because USD-denominated Turkey Eurobonds aren't
 * publicly addressable on Investing.com (no per-issue page or
 * country-filtered eurobond listing), so we ship the TR TL curve as the
 * next-best public source instead. Operator may swap in a paid USD
 * eurobond feed later.
 */
@Component
public class InvestingYieldCurveFetcher {

    private static final Logger log = LoggerFactory.getLogger(InvestingYieldCurveFetcher.class);

    private static final String URL =
            "https://tr.investing.com/rates-bonds/turkey-government-bonds";

    /** Investing.com row label → our internal curve-point definition. */
    record CurvePoint(String symbol, String label, int daysToMaturity) {}

    private static final Map<String, CurvePoint> POINTS = new LinkedHashMap<>();
    static {
        // Investing.com's label is "Türkiye 3 Aylık" etc. so we match
        // by suffix to be robust to leading-/trailing-whitespace changes.
        POINTS.put("3 Aylık",  new CurvePoint("TR3MO",  "Türkiye 3 Aylık Gösterge",  91));
        POINTS.put("6 Aylık",  new CurvePoint("TR6MO",  "Türkiye 6 Aylık Gösterge",  182));
        POINTS.put("9 Aylık",  new CurvePoint("TR9MO",  "Türkiye 9 Aylık Gösterge",  273));
        POINTS.put("1 Yıllık", new CurvePoint("TR1YR",  "Türkiye 1 Yıllık Gösterge", 365));
        POINTS.put("2 Yıllık", new CurvePoint("TR2YR",  "Türkiye 2 Yıllık Gösterge", 730));
        POINTS.put("3 Yıllık", new CurvePoint("TR3YR",  "Türkiye 3 Yıllık Gösterge", 1095));
        POINTS.put("5 Yıllık", new CurvePoint("TR5YR",  "Türkiye 5 Yıllık Gösterge", 1825));
        POINTS.put("10 Yıllık", new CurvePoint("TR10YR", "Türkiye 10 Yıllık Gösterge", 3650));
        POINTS.put("30 Yıllık", new CurvePoint("TR30YR", "Türkiye 30 Yıllık Gösterge", 10950));
    }

    private final RestClient playwrightClient;
    private final boolean enabled;

    public InvestingYieldCurveFetcher(
            @Value("${app.playwright.service-url:}") String playwrightUrl) {
        this.enabled = playwrightUrl != null && !playwrightUrl.isBlank();
        this.playwrightClient = enabled
                ? RestClient.builder().baseUrl(playwrightUrl).build()
                : null;
    }

    public List<BondQuoteDto> fetchAll() {
        if (!enabled) {
            log.warn("[INVESTING-CURVE] playwright-service not configured; skipping");
            return List.of();
        }

        String html;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = playwrightClient.post()
                    .uri("/fetch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", URL, "waitMs", 5000))
                    .retrieve()
                    .body(Map.class);
            if (resp == null) {
                log.warn("[INVESTING-CURVE] empty response from playwright-service");
                return List.of();
            }
            Object h = resp.get("html");
            html = h instanceof String s ? s : null;
        } catch (Exception e) {
            log.warn("[INVESTING-CURVE] fetch failed: {}", e.getMessage());
            return List.of();
        }

        if (html == null || html.isBlank()) return List.of();

        Document doc = Jsoup.parse(html);
        // Investing.com renders the country-bond table with this exact class
        // string. There may be more than one table on the page, so pick the
        // one whose <th> headers include "Getiri" (yield) — the canonical
        // crossRatesTbl shape.
        Element table = null;
        for (Element t : doc.select("table.crossRatesTbl")) {
            Elements headers = t.select("thead th");
            boolean hasYield = false;
            for (Element th : headers) {
                if (th.text().trim().equalsIgnoreCase("Getiri")) {
                    hasYield = true;
                    break;
                }
            }
            if (hasYield) {
                table = t;
                break;
            }
        }
        if (table == null) {
            log.warn("[INVESTING-CURVE] yield-curve table not found in DOM");
            return List.of();
        }

        List<BondQuoteDto> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Element tr : table.select("tbody > tr")) {
            Elements tds = tr.select("td");
            if (tds.size() < 8) continue;
            // Column layout observed: [flag] [name] [yield] [prev] [high] [low] [chg] [chg%] [time]
            String name = tds.get(1).text().trim();
            CurvePoint cp = matchCurvePoint(name);
            if (cp == null) continue;
            BigDecimal yield = parseTrNumber(tds.get(2).text());
            if (yield == null) continue;
            BigDecimal changeAbs = parseTrNumber(tds.get(6).text());
            BigDecimal changePct = parseTrPercent(tds.get(7).text());

            BondQuoteDto dto = new BondQuoteDto();
            dto.setSymbol(cp.symbol);
            dto.setName(cp.label);
            dto.setType(DebtInstrumentType.GOVERNMENT_BOND);
            dto.setIssuer("Hazine ve Maliye Bakanlığı");
            dto.setCurrency("TRY");
            dto.setMaturityDate(today.plusDays(cp.daysToMaturity));
            dto.setCouponRate(null);
            dto.setCouponType(null);
            dto.setQuoteDate(today);
            // The page publishes yields, not clean prices. Setting price to
            // par 100 makes the row render meaningfully on the bonds table
            // without pretending we know a market price we don't have.
            dto.setPrice(new BigDecimal("100.00"));
            dto.setCleanPrice(new BigDecimal("100.00"));
            dto.setDirtyPrice(new BigDecimal("100.00"));
            dto.setYieldRate(yield.setScale(2, RoundingMode.HALF_UP));
            if (changeAbs != null) dto.setChangeRate(changeAbs);
            dto.setSource("INVESTING_TR");
            out.add(dto);
            log.info("[INVESTING-CURVE] {}: yield={}%", cp.symbol, yield);
            // Silence unused variable warnings; changePct is captured for
            // future use (e.g. driving a sparkline) but not stored today.
            if (changePct != null) { /* future */ }
        }
        log.info("[INVESTING-CURVE] parsed {} curve points", out.size());
        return out;
    }

    private CurvePoint matchCurvePoint(String label) {
        if (label == null) return null;
        for (Map.Entry<String, CurvePoint> e : POINTS.entrySet()) {
            if (label.endsWith(e.getKey())) return e.getValue();
        }
        return null;
    }

    /**
     * Investing.com TR uses Turkish locale formatting: "37.723" is thirty-
     * seven point seven (decimal comma replaced by "."? actually the page
     * uses ',' as the decimal separator and '.' as thousands). Strip the
     * thousands separator and re-anchor the decimal mark.
     */
    private static BigDecimal parseTrNumber(String raw) {
        if (raw == null) return null;
        String s = raw.replace(" ", "").replace(" ", "").trim();
        if (s.isEmpty() || s.equals("-")) return null;
        // Two-format pass: prefer "1.234,56" → "1234.56"; if no comma and a
        // dot is the decimal mark (e.g. "37.723"), keep the dot.
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        }
        // Drop trailing % / sign chars the cell may carry.
        s = s.replaceAll("[^0-9.\\-+]", "");
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseTrPercent(String raw) {
        if (raw == null) return null;
        return parseTrNumber(raw.replace("%", ""));
    }
}
