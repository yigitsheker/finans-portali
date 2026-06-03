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
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches REAL Turkish government bond prices and coupons from TCMB EVDS3 and
 * derives a Yield-to-Maturity by discounting the bond's actual cash flows.
 *
 * <p><b>Source.</b> The veri grubu {@code bie_pydibs} — "Devlet İç Borçlanma
 * Senetlerinin Gösterge Niteliğindeki Değerleri". EVDS3 publishes, per bond
 * ISIN, a daily indicative <em>dirty</em> price ("Değer") and a static coupon
 * ("Kupon Faiz Oranı"). It does <b>not</b> publish a yield — we compute it.
 *
 * <p><b>No hardcoded ISIN list.</b> Earlier this class carried a handful of
 * hand-picked ISINs that had to be rotated by hand as bonds matured. Instead we
 * now enumerate the whole datagroup at runtime via the EVDS3 catalog endpoint
 * <pre>GET /igmevdsms-dis/serieList/fe/type=json&amp;code=bie_pydibs</pre>
 * and keep every <em>active</em> nominal fixed-coupon bond. Sukuk
 * ("Kira Getirisi Oranı") and CPI-linkers ("Reel Kupon Oranı") are skipped:
 * their "Değer" is not a comparable per-100 price and would pollute the curve.
 *
 * <p><b>Why a proper YTM and not the textbook approximation.</b> "Değer" is a
 * <em>dirty</em> price (clean price + accrued coupon). Two otherwise-identical
 * bonds quote very differently depending on how close they are to a coupon
 * date, so the simple {@code (C + (F-P)/n) / ((F+P)/2)} approximation produces
 * an incoherent curve. We instead rebuild the semi-annual coupon schedule from
 * the issue/maturity dates, strip accrued interest to recover the clean price,
 * and solve for the rate that discounts the remaining cash flows back to the
 * dirty price. Validated against the live API this yields a coherent ~16-22%
 * curve across maturities (mid-2026).
 *
 * <p>The old {@link #computeYtm} approximation is retained (and still unit
 * tested) as a documented fallback, but the live path uses
 * {@link #computeProperYtm}.
 */
@Component
public class EvdsBondYieldFetcher {

    private static final Logger log = LoggerFactory.getLogger(EvdsBondYieldFetcher.class);

    private static final String EVDS3_FE_URL = "https://evds3.tcmb.gov.tr/igmevdsms-dis/fe";
    private static final String EVDS3_SERIELIST_URL =
            "https://evds3.tcmb.gov.tr/igmevdsms-dis/serieList/fe/type=json&code=bie_pydibs";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final DateTimeFormatter EVDS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter NAME_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Parses an EVDS3 SERIE_NAME like
     * {@code "TRD050128T18 ( 07.01.2026 05.01.2028 )  Değer (24D2)"}
     * into ISIN + issue date + maturity date.
     */
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "^(\\S+)\\s*\\(\\s*(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s*\\)");

    /** Only nominal fixed-coupon bonds; their coupon series name carries this label. */
    private static final String FIXED_COUPON_LABEL = "Kupon Faiz Oranı";

    /** A single resolved bond: the two EVDS series that describe it plus dates. */
    public record BondDef(
            String isin,
            String priceSeries,   // e.g. TP.TRD050128T18           (daily dirty price)
            String couponSeries,  // e.g. TP.TRD050128T18.ORAN      (fixed coupon %)
            LocalDate issue,
            LocalDate maturity) {}

    /** Output of the proper-YTM computation. */
    public record YtmResult(BigDecimal cleanPrice, BigDecimal accrued, BigDecimal ytm) {}

    // Preferred auth: persistent API key from evds3.tcmb.gov.tr Profilim.
    // Session cookies remain as a fallback for users who haven't generated a key.
    @Value("${app.evds.api-key:}")
    private String apiKey;

    @Value("${app.bonds.tcmb.evds3-jsessionid:}")
    private String jsessionId;

    @Value("${app.bonds.tcmb.evds3-ts-cookie:}")
    private String tsCookie;

    // Display/quality knobs. Defaults validated against live EVDS3 data.
    @Value("${app.bonds.tcmb.max-bonds:160}")
    private int maxBonds;
    @Value("${app.bonds.tcmb.min-clean-price:40}")
    private double minCleanPrice;
    @Value("${app.bonds.tcmb.max-clean-price:140}")
    private double maxCleanPrice;
    @Value("${app.bonds.tcmb.min-ytm:6}")
    private double minYtm;
    @Value("${app.bonds.tcmb.max-ytm:60}")
    private double maxYtm;

    /** Series codes sent per /fe request (price + coupon together → ~40 bonds/request). */
    private static final int FETCH_CHUNK = 80;
    /** Catalog rarely changes; re-enumerate at most once a day. */
    private static final Duration CATALOG_TTL = Duration.ofHours(24);

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cached datagroup catalog (the 1.8 MB serieList parsed into bond defs).
    private volatile List<BondDef> cachedDefs;
    private volatile Instant cachedAt;

    public EvdsBondYieldFetcher() {
        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                // serieList for bie_pydibs is ~1.8 MB; give the codec headroom.
                .codecs(c -> c.defaultCodecs().maxInMemorySize(12 * 1024 * 1024))
                .build();
    }

    private boolean hasAuth() {
        boolean haveKey = apiKey != null && !apiKey.isBlank();
        boolean haveCookies = jsessionId != null && !jsessionId.isBlank()
                && tsCookie != null && !tsCookie.isBlank();
        return haveKey || haveCookies;
    }

    /**
     * Enumerate every active nominal fixed-coupon bond in {@code bie_pydibs},
     * fetch its latest price + coupon in batches, derive a clean-price YTM, and
     * return one quote per bond that survives the sanity filters.
     */
    public List<BondQuoteDto> fetchAll() {
        if (!hasAuth()) {
            log.warn("[EVDS-BOND] no EVDS auth (api-key or session cookies) configured; skipping");
            return List.of();
        }

        List<BondDef> defs = loadCatalog();
        if (defs.isEmpty()) {
            log.warn("[EVDS-BOND] catalog enumeration returned no active bonds");
            return List.of();
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(12); // span weekends/holidays

        // Batch-fetch every price + coupon series in one set of requests.
        List<String> allCodes = new ArrayList<>(defs.size() * 2);
        for (BondDef d : defs) {
            allCodes.add(d.priceSeries());
            allCodes.add(d.couponSeries());
        }
        Map<String, BigDecimal> values = batchFetchLatest(allCodes, startDate, today);

        List<BondQuoteDto> out = new ArrayList<>();
        for (BondDef bond : defs) {
            try {
                BigDecimal dirty = values.get(bond.priceSeries());
                BigDecimal coupon = values.get(bond.couponSeries());
                if (dirty == null) continue;

                YtmResult r = computeProperYtm(dirty, coupon, bond.issue(), bond.maturity(), today);
                if (r == null || r.ytm() == null || r.cleanPrice() == null) continue;

                double clean = r.cleanPrice().doubleValue();
                double y = r.ytm().doubleValue();
                if (clean < minCleanPrice || clean > maxCleanPrice) continue;
                if (y < minYtm || y > maxYtm) continue;

                BigDecimal couponEffective = coupon != null ? coupon : BigDecimal.ZERO;

                BondQuoteDto dto = new BondQuoteDto();
                dto.setSymbol(bond.isin());                 // ISIN is the natural unique key
                dto.setIsin(bond.isin());
                dto.setName(displayName(bond));
                dto.setType(DebtInstrumentType.GOVERNMENT_BOND);
                dto.setIssuer("Hazine ve Maliye Bakanlığı");
                dto.setCurrency("TRY");
                dto.setMaturityDate(bond.maturity());
                dto.setCouponRate(couponEffective);
                dto.setCouponType("FIXED");
                dto.setQuoteDate(today);
                dto.setPrice(r.cleanPrice());               // show the clean (market) price
                dto.setCleanPrice(r.cleanPrice());
                dto.setDirtyPrice(dirty);
                dto.setYieldRate(r.ytm());
                dto.setSource("TCMB_EVDS3");
                out.add(dto);
            } catch (RuntimeException e) {
                log.debug("[EVDS-BOND] {} compute failed: {}", bond.isin(), e.getMessage());
            }
        }

        // Collapse exact economic duplicates: TCMB reopens bonds under several
        // ISIN tranches (…T19 / …T27) that share maturity, coupon and price.
        // Listing them as separate rows looks like a bug, so keep one per
        // (maturity, coupon, clean price). Catalog order is stable → deterministic.
        Map<String, BondQuoteDto> unique = new LinkedHashMap<>();
        for (BondQuoteDto q : out) {
            String key = q.getMaturityDate() + "|" + q.getCouponRate() + "|" + q.getCleanPrice();
            unique.putIfAbsent(key, q);
        }
        out = new ArrayList<>(unique.values());

        // Stable, useful ordering; bound the count so the page never explodes.
        out.sort((a, b) -> a.getMaturityDate().compareTo(b.getMaturityDate()));
        if (out.size() > maxBonds) {
            out = new ArrayList<>(out.subList(0, maxBonds));
        }
        log.info("[EVDS-BOND] {} active fixed-coupon bonds → {} unique quotes after YTM + sanity filters",
                defs.size(), out.size());
        return out;
    }

    private static String displayName(BondDef b) {
        return String.format("Devlet Tahvili %s — Vade %s",
                b.isin(), b.maturity().format(NAME_DATE));
    }

    // ── Catalog enumeration ────────────────────────────────────────────────

    /** Returns the cached bond defs, re-enumerating from EVDS3 when stale. */
    private List<BondDef> loadCatalog() {
        List<BondDef> cached = cachedDefs;
        Instant at = cachedAt;
        if (cached != null && at != null
                && Duration.between(at, Instant.now()).compareTo(CATALOG_TTL) < 0) {
            return cached;
        }
        synchronized (this) {
            if (cachedDefs != null && cachedAt != null
                    && Duration.between(cachedAt, Instant.now()).compareTo(CATALOG_TTL) < 0) {
                return cachedDefs;
            }
            List<BondDef> fresh = enumerateCatalog();
            if (!fresh.isEmpty()) {
                cachedDefs = fresh;
                cachedAt = Instant.now();
                return fresh;
            }
            // On a transient failure keep serving the previous catalog if any.
            return cachedDefs != null ? cachedDefs : List.of();
        }
    }

    /**
     * GET the {@code bie_pydibs} series list and resolve it into nominal
     * fixed-coupon {@link BondDef}s whose maturity is still in the future.
     */
    private List<BondDef> enumerateCatalog() {
        try {
            WebClient.RequestHeadersSpec<?> get = webClient.get()
                    .uri(URI.create(EVDS3_SERIELIST_URL))
                    .header("Accept", "application/json");
            get = applyAuth(get);
            String response = get
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
            if (response == null || response.isBlank() || response.startsWith("<")) {
                log.warn("[EVDS-BOND] serieList returned no/HTML body");
                return List.of();
            }
            JsonNode arr = objectMapper.readTree(response);
            if (!arr.isArray()) return List.of();

            // First pass: index price series and coupon series by ISIN.
            Map<String, BondDef> priceByIsin = new LinkedHashMap<>();
            Map<String, String> couponByIsin = new LinkedHashMap<>();
            for (JsonNode item : arr) {
                String code = item.path("SERIE_CODE").asText("");
                String name = item.path("SERIE_NAME").asText("");
                if (code.isEmpty() || name.isEmpty()) continue;

                if (code.endsWith(".ORAN")) {
                    // Only keep nominal fixed-coupon rates (skip Kira / Reel Kupon).
                    if (!name.contains(FIXED_COUPON_LABEL)) continue;
                    String isin = parseIsin(name);
                    if (isin != null) couponByIsin.put(isin, code);
                } else {
                    Matcher m = NAME_PATTERN.matcher(name);
                    if (!m.find()) continue;
                    String isin = m.group(1);
                    LocalDate issue = parseDate(m.group(2));
                    LocalDate maturity = parseDate(m.group(3));
                    if (issue == null || maturity == null) continue;
                    priceByIsin.put(isin, new BondDef(isin, code, null, issue, maturity));
                }
            }

            LocalDate today = LocalDate.now();
            List<BondDef> defs = new ArrayList<>();
            for (Map.Entry<String, BondDef> e : priceByIsin.entrySet()) {
                String coupon = couponByIsin.get(e.getKey());
                if (coupon == null) continue;                 // not a fixed-coupon bond
                BondDef p = e.getValue();
                if (!p.maturity().isAfter(today)) continue;    // expired
                defs.add(new BondDef(p.isin(), p.priceSeries(), coupon, p.issue(), p.maturity()));
            }
            log.info("[EVDS-BOND] catalog: {} series → {} active fixed-coupon bonds",
                    arr.size(), defs.size());
            return defs;
        } catch (Exception e) {
            log.warn("[EVDS-BOND] catalog enumeration failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static String parseIsin(String name) {
        Matcher m = NAME_PATTERN.matcher(name);
        if (m.find()) return m.group(1);
        int sp = name.indexOf(' ');
        return sp > 0 ? name.substring(0, sp) : null;
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s, NAME_DATE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ── Batched data fetch ─────────────────────────────────────────────────

    /**
     * Fetch the latest non-null observation for each series code. EVDS3's /fe
     * accepts many series at once when {@code series}, {@code aggregationTypes}
     * and {@code formulas} are dash-joined with matching arity (this is exactly
     * how the EVDS3 SPA batches its basket), so we chunk to keep bodies sane.
     */
    private Map<String, BigDecimal> batchFetchLatest(List<String> codes, LocalDate from, LocalDate to) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (int i = 0; i < codes.size(); i += FETCH_CHUNK) {
            List<String> chunk = codes.subList(i, Math.min(i + FETCH_CHUNK, codes.size()));
            try {
                out.putAll(fetchChunk(chunk, from, to));
            } catch (RuntimeException e) {
                log.warn("[EVDS-BOND] chunk [{}..{}) failed: {}", i, i + chunk.size(), e.getMessage());
            }
        }
        return out;
    }

    private Map<String, BigDecimal> fetchChunk(List<String> codes, LocalDate from, LocalDate to) {
        String series = String.join("-", codes);
        StringBuilder aggs = new StringBuilder();
        StringBuilder formulas = new StringBuilder();
        for (int k = 0; k < codes.size(); k++) {
            if (k > 0) { aggs.append('-'); formulas.append('-'); }
            aggs.append("last");
            formulas.append('0');
        }
        String body = String.format(
                "{\"type\":\"json\",\"series\":\"%s\",\"aggregationTypes\":\"%s\","
                        + "\"formulas\":\"%s\",\"dateFormat\":\"0\",\"decimal\":\"4\","
                        + "\"decimalSeperator\":\".\",\"startDate\":\"%s\",\"endDate\":\"%s\","
                        + "\"frequency\":\"1\",\"groupSeperator\":true,"
                        + "\"isRaporSayfasi\":false,\"lang\":\"tr\","
                        + "\"ozelFormuller\":[],\"sira\":\"0\",\"yon\":\"0\"}",
                series, aggs, formulas, from.format(EVDS_DATE), to.format(EVDS_DATE));

        WebClient.RequestBodySpec post = webClient.post()
                .uri(EVDS3_FE_URL)
                .header("Content-Type", "application/json")
                .header("Origin", "https://evds3.tcmb.gov.tr")
                .header("Referer", "https://evds3.tcmb.gov.tr/");
        if (apiKey != null && !apiKey.isBlank()) {
            post = post.header("key", apiKey);
        } else {
            post = post.header("Cookie", "JSESSIONID=" + jsessionId + "; TS017d0b0b=" + tsCookie);
        }
        String response = post
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(20));

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (response == null || response.isBlank() || response.startsWith("<")) {
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");
            if (!items.isArray()) return result;

            // EVDS returns oldest-first; the last non-null per column is "latest".
            // Column names are the series codes with dots replaced by underscores.
            for (String code : codes) {
                String column = code.replace('.', '_');
                BigDecimal latest = null;
                for (JsonNode item : items) {
                    JsonNode val = item.get(column);
                    if (val == null || val.isNull()) continue;
                    String s = val.asText("").trim();
                    if (s.isEmpty() || "null".equalsIgnoreCase(s)) continue;
                    try {
                        latest = new BigDecimal(s.replace(",", "."));
                    } catch (NumberFormatException ignored) { /* keep prior */ }
                }
                if (latest != null) result.put(code, latest);
            }
        } catch (Exception e) {
            log.debug("[EVDS-BOND] chunk parse failed: {}", e.getMessage());
        }
        return result;
    }

    /** Apply EVDS auth (api-key header, or session cookies as fallback) to a GET. */
    private WebClient.RequestHeadersSpec<?> applyAuth(WebClient.RequestHeadersSpec<?> spec) {
        if (apiKey != null && !apiKey.isBlank()) {
            return spec.header("key", apiKey);
        }
        return spec.header("Cookie", "JSESSIONID=" + jsessionId + "; TS017d0b0b=" + tsCookie);
    }

    // ── Yield math ─────────────────────────────────────────────────────────

    /**
     * Proper YTM: rebuild the semi-annual coupon schedule, strip accrued
     * interest from the dirty price to recover the clean price, and solve (by
     * bisection) for the annual rate that discounts the remaining cash flows
     * back to the dirty (full) price. Returns null if the bond has no future
     * coupon, the math degenerates, or no root brackets in a sane range.
     */
    static YtmResult computeProperYtm(BigDecimal dirtyPrice, BigDecimal annualCouponPct,
                                      LocalDate issue, LocalDate maturity, LocalDate today) {
        if (dirtyPrice == null || dirtyPrice.signum() <= 0) return null;
        if (maturity == null || !maturity.isAfter(today)) return null;
        double dirty = dirtyPrice.doubleValue();
        double annualCoupon = annualCouponPct == null ? 0.0 : annualCouponPct.doubleValue();
        double half = annualCoupon / 2.0;

        // Semi-annual coupon dates within (issue, maturity], ascending.
        List<LocalDate> schedule = new ArrayList<>();
        LocalDate d = maturity;
        LocalDate floor = issue != null ? issue : maturity.minusYears(50);
        while (d.isAfter(floor)) {
            schedule.add(d);
            d = d.minusMonths(6);
        }
        if (schedule.isEmpty()) return null;
        java.util.Collections.reverse(schedule);

        // Accrued interest since the last coupon (or issue) before today.
        LocalDate lastCoupon = issue != null ? issue : schedule.get(0);
        LocalDate nextCoupon = null;
        for (LocalDate c : schedule) {
            if (!c.isAfter(today)) {
                lastCoupon = c;
            } else {
                nextCoupon = c;
                break;
            }
        }
        if (nextCoupon == null) return null; // nothing left to pay
        double periodDays = ChronoUnit.DAYS.between(lastCoupon, nextCoupon);
        double accDays = ChronoUnit.DAYS.between(lastCoupon, today);
        double accrued = periodDays > 0 ? half * (accDays / periodDays) : 0.0;
        double clean = dirty - accrued;

        // Future cash flows: each remaining coupon, plus principal at maturity.
        List<double[]> cf = new ArrayList<>(); // [yearsFromToday, amount]
        for (LocalDate c : schedule) {
            if (!c.isAfter(today)) continue;
            double years = ChronoUnit.DAYS.between(today, c) / 365.25;
            double amount = half + (c.equals(maturity) ? 100.0 : 0.0);
            cf.add(new double[]{years, amount});
        }
        if (cf.isEmpty()) return null;

        double ytm = solveYtm(cf, dirty);
        if (Double.isNaN(ytm)) return null;

        return new YtmResult(
                BigDecimal.valueOf(clean).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(accrued).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(ytm * 100.0).setScale(2, RoundingMode.HALF_UP));
    }

    /** Bisection root-find for the annual rate y where PV(cashflows, y) == price. */
    private static double solveYtm(List<double[]> cf, double price) {
        double lo = -0.5, hi = 5.0;
        double flo = pv(cf, lo) - price;
        double fhi = pv(cf, hi) - price;
        if (flo == 0) return lo;
        if (fhi == 0) return hi;
        if (flo * fhi > 0) return Double.NaN; // no sign change → unsolvable in range
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2.0;
            double fmid = pv(cf, mid) - price;
            if (Math.abs(fmid) < 1e-7) return mid;
            if (flo * fmid < 0) {
                hi = mid;
            } else {
                lo = mid; flo = fmid;
            }
        }
        return (lo + hi) / 2.0;
    }

    private static double pv(List<double[]> cf, double y) {
        double sum = 0.0;
        for (double[] c : cf) {
            sum += c[1] / Math.pow(1.0 + y, c[0]);
        }
        return sum;
    }

    /**
     * Legacy simple-approximation YTM, retained for reference and covered by
     * {@code EvdsBondYieldFetcherTest}. The live path uses
     * {@link #computeProperYtm}; this under/over-states yield near coupon dates
     * because it treats the dirty price as if it were the clean price.
     *
     * <pre>YTM ≈ (C + (F − P) / n) / ((F + P) / 2) × 100</pre>
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
        double ytm = (C + (F - P) / years) / ((F + P) / 2.0) * 100.0;
        return BigDecimal.valueOf(ytm).setScale(2, RoundingMode.HALF_UP);
    }
}
