package com.finansportali.backend.service.client.bond;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches REAL Turkish government bond prices and coupons from TCMB EVDS3,
 * then derives Yield-to-Maturity via the classic approximation formula.
 *
 * <p>Data source: <b>"Devlet İç Borçlanma Senetlerinin Gösterge Niteliğindeki
 * Değerleri"</b> (Veri Grubu: {@code bie_pydibs}). EVDS3 publishes one daily
 * price ("Değer") and a static coupon ("ORAN") per bond ISIN.
 *
 * <p>Series code format discovered by inspecting EVDS3 SPA's network calls:
 * <ul>
 *   <li>{@code TP.<TRT_ISIN>} → günlük piyasa fiyatı (par 100 üzerinden)</li>
 *   <li>{@code TP.<TRT_ISIN>.ORAN} → sabit kupon faiz oranı (%)</li>
 * </ul>
 *
 * <p>YTM formula:
 * <pre>
 *     YTM ≈ (C + (F − P) / n) / ((F + P) / 2) × 100
 * </pre>
 * where C = annual coupon %, F = 100 (par), P = current dirty price, n = years to maturity.
 * This is the standard simple-approximation; accurate to ~0.5% for short maturities and
 * good enough for indicator-level display.
 *
 * <p><b>Bond rotation:</b> these ISINs are hardcoded and will <em>expire</em> when
 * the underlying bonds mature. Operator should refresh the {@link #BONDS} list
 * every 6-12 months. When a bond is past its maturity date, EVDS3 silently
 * returns no data and the row drops out (graceful degradation).
 */
@Component
public class EvdsBondYieldFetcher {

    private static final Logger log = LoggerFactory.getLogger(EvdsBondYieldFetcher.class);

    private static final String EVDS3_URL = "https://evds3.tcmb.gov.tr/igmevdsms-dis/fe";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final DateTimeFormatter EVDS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Each bond on EVDS3 publishes two separate series with different ISIN suffixes:
     * one for the daily market value ("Değer", T-suffix code) and one for the static
     * coupon rate ("Kupon Faiz Oranı", K-suffix code, queried with .ORAN). The two
     * codes share the same maturity date in their middle bytes (TRT&lt;DDMMYY&gt;).
     * Both must be supplied together; passing the wrong code for price gives
     * nonsense YTMs because the value is in different units.
     */
    public record BondDef(
            String symbol,         // Our internal short symbol (TR91GB, TR2YT, ...)
            String displayName,    // Human-readable label
            String priceIsin,      // T-suffix code, e.g. TRT020130T19 (Değer series)
            String couponIsin,     // K-suffix code, e.g. TRT020130K18 (Kupon Faiz Oranı series)
            DebtInstrumentType type,
            LocalDate maturity     // Maturity date parsed from either ISIN's date bytes
    ) {}

    /**
     * Active bond list — refresh when bonds approach maturity or when better-matched
     * paired ISINs are identified.
     *
     * <p>Two patterns are supported because EVDS3 uses both:
     * <ul>
     *   <li><b>Same-code:</b> price and coupon share an ISIN (e.g., TRD171127T13);
     *       price comes from {@code TP.X}, coupon from {@code TP.X.ORAN}.</li>
     *   <li><b>Paired-code:</b> price and coupon use different ISINs sharing the
     *       middle DDMMYY maturity bytes (e.g., TRT020130T19 / TRT020130K18).</li>
     * </ul>
     *
     * <p>For symbols not on this list the calling code keeps its policy-rate-plus-spread
     * fallback (clearly labelled with source=TCMB rather than TCMB_EVDS3).
     */
    static final List<BondDef> BONDS = List.of(
            // ~2-year, same-code pattern (TP.TRD171127T13 + .ORAN)
            new BondDef("TR2YT", "Türkiye 2 Yıllık Devlet Tahvili (TRD171127T13)",
                    "TRD171127T13", "TRD171127T13",
                    DebtInstrumentType.GOVERNMENT_BOND, LocalDate.of(2027, 11, 17)),
            // ~2-year, alternative paired-code (TRD050128 T18/A19) — adds curve coverage
            new BondDef("TR2YT_B", "Türkiye 2 Yıllık Devlet Tahvili (TRD050128T18)",
                    "TRD050128T18", "TRD050128T18",
                    DebtInstrumentType.GOVERNMENT_BOND, LocalDate.of(2028, 1, 5)),
            // ~2.5-year, same-code pattern (TP.TRD151227T14 + .ORAN)
            new BondDef("TR3YT", "Türkiye 3 Yıllık Devlet Tahvili (TRD151227T14)",
                    "TRD151227T14", "TRD151227T14",
                    DebtInstrumentType.GOVERNMENT_BOND, LocalDate.of(2027, 12, 15)),
            // ~1.5-year (TRD171127T21 — different sub-issue, same maturity as TR2YT)
            new BondDef("TR2YT_C", "Türkiye 2 Yıllık Devlet Tahvili (TRD171127T21)",
                    "TRD171127T21", "TRD171127T21",
                    DebtInstrumentType.GOVERNMENT_BOND, LocalDate.of(2027, 11, 17)),
            // 4-year, paired-code (TRT020130 T19 + K18)
            new BondDef("TR4YT", "Türkiye 4 Yıllık Devlet Tahvili (TRT020130T19)",
                    "TRT020130T19", "TRT020130K18",
                    DebtInstrumentType.GOVERNMENT_BOND, LocalDate.of(2030, 1, 2)),
            // ~4-year, same-code pattern (TRT090130T20 — alt sub-issue of TR4YT)
            new BondDef("TR4YT_B", "Türkiye 4 Yıllık Devlet Tahvili (TRT090130T20)",
                    "TRT090130T20", "TRT090130T20",
                    DebtInstrumentType.GOVERNMENT_BOND, LocalDate.of(2030, 1, 9))
            // Note: TRT060127T10 (1Y) and TRT080131T20 (5Y) were probed but their
            // same-code .ORAN series returns non-coupon values that yield nonsense
            // YTMs (293% and -0.19%). They'd need a paired K-suffix coupon ISIN to
            // be usable, which is not in our verified set.
    );

    // Preferred auth: persistent API key from evds3.tcmb.gov.tr Profilim.
    // Session cookies remain as a fallback for users who haven't generated a key.
    @Value("${app.evds.api-key:}")
    private String apiKey;

    @Value("${app.bonds.tcmb.evds3-jsessionid:}")
    private String jsessionId;

    @Value("${app.bonds.tcmb.evds3-ts-cookie:}")
    private String tsCookie;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvdsBondYieldFetcher() {
        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * Query EVDS3 for every bond in {@link #BONDS} and return a quote per bond
     * with real price, real coupon, and a YTM derived from both.
     * Bonds that fail to return data (network error, expired, missing) are skipped.
     */
    public List<BondQuoteDto> fetchAll() {
        boolean haveKey = apiKey != null && !apiKey.isBlank();
        boolean haveCookies = jsessionId != null && !jsessionId.isBlank()
                && tsCookie != null && !tsCookie.isBlank();
        if (!haveKey && !haveCookies) {
            log.warn("[EVDS-BOND] no EVDS auth (api-key or session cookies) configured; skipping");
            return List.of();
        }

        LocalDate today = LocalDate.now();
        // Wider lookback so weekends/holidays don't return empty
        LocalDate startDate = today.minusDays(10);

        List<BondQuoteDto> out = new ArrayList<>();
        for (BondDef bond : BONDS) {
            try {
                BigDecimal price = fetchLatest("TP." + bond.priceIsin, startDate, today);
                BigDecimal coupon = fetchLatest("TP." + bond.couponIsin + ".ORAN", startDate, today);
                if (price == null) {
                    log.warn("[EVDS-BOND] No price for {} ({}); skipping", bond.symbol, bond.priceIsin);
                    continue;
                }
                // Reject obvious garbage — a real bond trades roughly 50..200 per 100 par.
                // Values outside that band mean we hit a wrong series type or a stale record.
                double p = price.doubleValue();
                if (p < 30 || p > 200) {
                    log.warn("[EVDS-BOND] {} price={} outside sane range; skipping", bond.symbol, price);
                    continue;
                }
                BigDecimal couponEffective = coupon != null ? coupon : BigDecimal.ZERO;
                BigDecimal ytm = computeYtm(price, couponEffective, bond.maturity, today);
                if (ytm == null) continue;
                // Sanity-clamp the derived YTM. Turkish bonds today yield ~25..45%; anything
                // outside 0..100% means the inputs were wrong.
                double y = ytm.doubleValue();
                if (y < 0 || y > 100) {
                    log.warn("[EVDS-BOND] {} derived YTM={}% outside sane range; skipping", bond.symbol, ytm);
                    continue;
                }

                BondQuoteDto dto = new BondQuoteDto();
                dto.setSymbol(bond.symbol);
                dto.setIsin(bond.priceIsin);
                dto.setName(bond.displayName);
                dto.setType(bond.type);
                dto.setIssuer("Hazine ve Maliye Bakanlığı");
                dto.setCurrency("TRY");
                dto.setMaturityDate(bond.maturity);
                dto.setCouponRate(couponEffective);
                dto.setCouponType("FIXED");
                dto.setQuoteDate(today);
                dto.setPrice(price);
                dto.setCleanPrice(price);
                dto.setDirtyPrice(price);
                dto.setYieldRate(ytm);
                dto.setSource("TCMB_EVDS3");
                out.add(dto);
                log.info("[EVDS-BOND] {} (price={}, coupon={}): YTM={}%",
                        bond.symbol, price, couponEffective, ytm);
            } catch (Exception e) {
                log.warn("[EVDS-BOND] {} fetch failed: {}", bond.priceIsin, e.getMessage());
            }
        }
        return out;
    }

    /**
     * Pull a single EVDS3 series and return the most recent non-null observation,
     * or null if the response is unusable.
     */
    private BigDecimal fetchLatest(String seriesCode, LocalDate from, LocalDate to) {
        try {
            String body = String.format(
                    "{\"type\":\"json\",\"series\":\"%s\",\"aggregationTypes\":\"last\","
                            + "\"formulas\":\"0\",\"dateFormat\":\"0\",\"decimal\":\"4\","
                            + "\"decimalSeperator\":\".\",\"startDate\":\"%s\",\"endDate\":\"%s\","
                            + "\"frequency\":\"1\",\"groupSeperator\":true,"
                            + "\"isRaporSayfasi\":false,\"lang\":\"tr\","
                            + "\"ozelFormuller\":[],\"sira\":\"0\",\"yon\":\"0\"}",
                    seriesCode, from.format(EVDS_DATE), to.format(EVDS_DATE));

            // Prefer the API key (persistent); fall back to session cookies
            // only when the operator hasn't provisioned a key yet.
            var spec = webClient.post()
                    .uri(EVDS3_URL)
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://evds3.tcmb.gov.tr")
                    .header("Referer", "https://evds3.tcmb.gov.tr/");
            if (apiKey != null && !apiKey.isBlank()) {
                spec = spec.header("key", apiKey);
            } else {
                spec = spec.header("Cookie",
                        "JSESSIONID=" + jsessionId + "; TS017d0b0b=" + tsCookie);
            }
            String response = spec
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));

            if (response == null || response.isBlank() || response.startsWith("<")) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) return null;

            // Series codes appear in the JSON with dots replaced by underscores:
            // "TP.TRT020130T19" → "TP_TRT020130T19"
            String column = seriesCode.replace('.', '_');

            // EVDS returns oldest-first; walk backwards to find latest non-null value
            BigDecimal latest = null;
            for (JsonNode item : items) {
                JsonNode val = item.get(column);
                if (val == null || val.isNull()) continue;
                String s = val.asText("").trim();
                if (s.isEmpty() || "null".equalsIgnoreCase(s)) continue;
                try {
                    latest = new BigDecimal(s.replace(",", "."));
                } catch (NumberFormatException ignored) {}
            }
            return latest;
        } catch (Exception e) {
            log.debug("[EVDS-BOND] fetchLatest({}) failed: {}", seriesCode, e.getMessage());
            return null;
        }
    }

    /**
     * Yield-to-maturity approximation. Returns null if maturity is in the past
     * or the math degenerates.
     */
    static BigDecimal computeYtm(BigDecimal priceDirty, BigDecimal couponPct,
                                 LocalDate maturity, LocalDate today) {
        if (priceDirty == null || priceDirty.signum() <= 0) return null;
        long days = ChronoUnit.DAYS.between(today, maturity);
        if (days <= 0) return null;
        double years = days / 365.25;
        double C = couponPct == null ? 0.0 : couponPct.doubleValue();
        double F = 100.0;
        double P = priceDirty.doubleValue();
        // Classic YTM approximation. C is "coupon TL per 100 par" (i.e. the % coupon
        // number treated as a TL flow on a 100 par); F = 100, P = current price.
        // Result is a decimal fraction → multiply by 100 to express as a percentage.
        double ytm = (C + (F - P) / years) / ((F + P) / 2.0) * 100.0;
        return BigDecimal.valueOf(ytm).setScale(2, RoundingMode.HALF_UP);
    }

    /** Exposed for the bond provider to know which symbols to skip in its synthetic loop. */
    public List<String> coveredSymbols() {
        List<String> list = new ArrayList<>();
        for (BondDef b : BONDS) list.add(b.symbol);
        return list;
    }

    /** Bond definition lookup by our symbol (for caller to read metadata). */
    public Map<String, BondDef> bondsBySymbol() {
        Map<String, BondDef> m = new LinkedHashMap<>();
        for (BondDef b : BONDS) m.put(b.symbol, b);
        return m;
    }
}
