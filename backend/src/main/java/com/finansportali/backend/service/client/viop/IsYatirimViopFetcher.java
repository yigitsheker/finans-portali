package com.finansportali.backend.service.client.viop;

import com.finansportali.backend.entity.ViopContract;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes İş Yatırım's public VIOP page via the playwright-service sidecar
 * (Akamai bot-blocks both Java's HTTP stack and system curl on this host;
 * a real headless Chromium gets through).
 *
 * The page renders ten DataTables — seven futures (id 0..6) and three options
 * (7..9). We only ingest the futures categories for now.
 */
@Component
public class IsYatirimViopFetcher {

    private static final Logger log = LoggerFactory.getLogger(IsYatirimViopFetcher.class);

    private static final String VIOP_URL =
            "https://www.isyatirim.com.tr/tr-tr/analiz/Sayfalar/viop.aspx";

    /**
     * Symbol parsed out of the {@code title="F_AKBNK0526 | AKBNK Mayis 2026 Vadeli"} attribute.
     * Underlying is the variable middle (AKBNK / XU030 / USDTRY / ...).
     */
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("^F_([A-Z0-9]{3,10})(\\d{2})(\\d{2})$");

    /** Table id (DataTables data-id) → contract category. Options tables (7..9) skipped. */
    private static final Map<Integer, ViopContract.Category> CATEGORY_BY_TABLE = Map.of(
            0, ViopContract.Category.STOCK,
            1, ViopContract.Category.INDEX,
            2, ViopContract.Category.FX_TRY,
            3, ViopContract.Category.FX_USD,
            4, ViopContract.Category.METAL_TRY,
            5, ViopContract.Category.METAL_USD,
            6, ViopContract.Category.METAL
    );

    private final RestClient playwrightClient;
    private final boolean enabled;

    public IsYatirimViopFetcher(
            @Value("${app.playwright.service-url:}") String playwrightUrl) {
        this.enabled = playwrightUrl != null && !playwrightUrl.isBlank();
        this.playwrightClient = enabled
                ? RestClient.builder().baseUrl(playwrightUrl).build()
                : null;
    }

    /**
     * Fetch and parse the VIOP page. Returns one transient {@link ViopContract}
     * per row — caller is responsible for upserting by symbol.
     */
    public List<ViopContract> fetchAll() {
        if (!enabled) {
            log.warn("VIOP fetch skipped — playwright-service URL is not configured");
            return List.of();
        }

        String html;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = playwrightClient.post()
                    .uri("/fetch")
                    .contentType(MediaType.APPLICATION_JSON)
                    // Wait 5s after DOMContentLoaded so the DataTables JS finishes
                    // mounting rows. Without this we sometimes grab an empty <tbody>.
                    .body(Map.of("url", VIOP_URL, "waitMs", 5000))
                    .retrieve()
                    .body(Map.class);
            if (resp == null) {
                log.warn("VIOP fetch: empty response from playwright-service");
                return List.of();
            }
            Object h = resp.get("html");
            html = h instanceof String s ? s : null;
        } catch (Exception e) {
            log.error("VIOP fetch via playwright-service failed: {}", e.getMessage());
            return List.of();
        }

        if (html == null || html.isBlank()) return List.of();

        Document doc = Jsoup.parse(html);
        List<ViopContract> all = new ArrayList<>();
        for (Map.Entry<Integer, ViopContract.Category> e : CATEGORY_BY_TABLE.entrySet()) {
            int tableId = e.getKey();
            ViopContract.Category category = e.getValue();
            // The page renders each table twice (DataTables fixed-header clone),
            // so we deliberately target the canonical id="DataTables_Table_N" copy.
            Element table = doc.selectFirst(
                    "table[data-csvname=viop][data-id=" + tableId + "]#DataTables_Table_" + tableId);
            if (table == null) {
                log.debug("VIOP table {} not found in HTML", tableId);
                continue;
            }
            int rowsBefore = all.size();
            for (Element tr : table.select("tbody > tr")) {
                ViopContract c = parseRow(tr, category);
                if (c != null) all.add(c);
            }
            log.info("VIOP table {} ({}): parsed {} rows", tableId, category, all.size() - rowsBefore);
        }
        return all;
    }

    private ViopContract parseRow(Element tr, ViopContract.Category category) {
        Elements tds = tr.select("td");
        if (tds.size() < 6) return null;

        // First <td> carries title="F_<SYMBOL>NNNN | <Display Name>"
        Element first = tds.get(0);
        String title = first.attr("title");
        int pipe = title.indexOf('|');
        if (pipe < 0) return null;
        String symbol = title.substring(0, pipe).trim();
        String displayName = title.substring(pipe + 1).trim();

        Matcher m = SYMBOL_PATTERN.matcher(symbol);
        if (!m.matches()) {
            log.debug("Skipping unrecognised VIOP symbol: {}", symbol);
            return null;
        }
        String underlying = m.group(1);
        int month = Integer.parseInt(m.group(2));
        int yy = Integer.parseInt(m.group(3));
        int year = 2000 + yy;   // VIOP symbols use 2-digit year; this codebase will outlive 2099 anyway

        ViopContract c = new ViopContract();
        c.setSymbol(symbol);
        c.setName(displayName);
        c.setUnderlying(underlying);
        c.setMaturityMonth(month);
        c.setMaturityYear(year);
        c.setCategory(category);
        c.setLastPrice(parseNumber(tds.get(1).text()));
        // The change% cell contains a <span class="value down|up">-0,31</span>.
        // .text() gives just the number; sign is already included.
        c.setChangePct(parseNumber(tds.get(2).text()));
        c.setChangeAbs(parseNumber(tds.get(3).text()));
        c.setVolumeTl(parseNumber(tds.get(4).text()));
        BigDecimal lots = parseNumber(tds.get(5).text());
        c.setVolumeLots(lots == null ? null : lots.longValue());
        return c;
    }

    /**
     * Turkish number format on this page: thousands separated by ".", decimal by ",".
     * Empty / "-" / non-numeric cells return null so the row can still be saved
     * with whatever fields did parse.
     */
    private BigDecimal parseNumber(String raw) {
        if (raw == null) return null;
        String s = raw.replace(" ", "").trim();
        if (s.isEmpty() || s.equals("-")) return null;
        s = s.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
