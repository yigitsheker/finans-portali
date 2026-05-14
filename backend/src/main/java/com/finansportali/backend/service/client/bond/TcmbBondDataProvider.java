package com.finansportali.backend.service.client.bond;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * TCMB EVDS3 + Bigpara hibrit bond veri sağlayıcısı.
 *
 * <p><b>EVDS3 session-based auth:</b> TCMB Mart 2026'da eski EVDS2 REST API'sini
 * (key-in-URL) emekliye ayırıp EVDS3 SPA-only sistemine geçti. Programatik erişim
 * için kullanıcının {@code evds3.tcmb.gov.tr}'a login olup tarayıcıdan
 * {@code JSESSIONID} ve {@code TS017d0b0b} cookie'lerini alıp env var olarak
 * vermesi gerekiyor. Cookie süresi 30dk-2 saat; expire olunca {@link #fetchEvdsValue}
 * boş döner ve graceful olarak fallback değerlere düşülür.
 *
 * <p>EVDS3 internal API endpoint'i (SPA bundle'ından reverse engineer edildi):
 * {@code POST https://evds3.tcmb.gov.tr/igmevdsms-dis/fe}.
 *
 * <p>Veri kaynakları:
 * <ul>
 *   <li><b>TP.BISPOLFAIZ.TUR</b>: TCMB politika faizi (aylık, EVDS3). Bu canlı
 *       değer üzerine piyasa spread'leri eklenerek 91g/182g/364g/2Y/5Y/10Y için
 *       gösterge getiriler türetilir.</li>
 *   <li><b>Bigpara HTML scrape</b>: Kira sertifikası ve eurobond için. HTML
 *       statik değilse {@link #buildFallbackSukukAndEurobonds} seed değerlerine düşülür.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "app.bonds.provider", havingValue = "TCMB", matchIfMissing = false)
public class TcmbBondDataProvider implements BondDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TcmbBondDataProvider.class);

    private static final String EVDS3_DATA_URL = "https://evds3.tcmb.gov.tr/igmevdsms-dis/fe";
    private static final String BIGPARA_BONDS_URL = "https://bigpara.hurriyet.com.tr/borsa/canli-borsa/tahvil-fiyatlari/";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36";

    private static final DateTimeFormatter EVDS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** EVDS3 erişilemezse kullanılacak son bilinen TCMB politika faizi (2026 yıllığı). */
    private static final BigDecimal POLICY_RATE_FALLBACK = new BigDecimal("37.00");

    /** Politika faizine eklenecek piyasa spread'leri (TR yield curve şekli). */
    private static final BigDecimal SPREAD_91D  = new BigDecimal("-0.50");
    private static final BigDecimal SPREAD_182D = new BigDecimal("-0.20");
    private static final BigDecimal SPREAD_364D = new BigDecimal("0.30");
    private static final BigDecimal SPREAD_2Y   = new BigDecimal("1.20");
    private static final BigDecimal SPREAD_5Y   = new BigDecimal("2.80");
    private static final BigDecimal SPREAD_10Y  = new BigDecimal("4.50");

    /** TCMB politika faizi seri kodu (EVDS3'te aylık yayınlanıyor). */
    private static final String SERIES_POLICY_RATE = "TP.BISPOLFAIZ.TUR";

    /** Vade-ID → spread mapping (DiBS yield türetmesi için, getBondHistory chart'ı dolduruyor). */
    private static final Map<String, BigDecimal> SPREAD_BY_SYMBOL = new LinkedHashMap<>();
    static {
        SPREAD_BY_SYMBOL.put("TR91GB",  SPREAD_91D);
        SPREAD_BY_SYMBOL.put("TR182GB", SPREAD_182D);
        SPREAD_BY_SYMBOL.put("TR364GB", SPREAD_364D);
        SPREAD_BY_SYMBOL.put("TR2YT",   SPREAD_2Y);
        SPREAD_BY_SYMBOL.put("TR5YT",   SPREAD_5Y);
        SPREAD_BY_SYMBOL.put("TR10YT",  SPREAD_10Y);
    }

    @Value("${app.bonds.tcmb.evds3-jsessionid:}")
    private String jsessionId;

    @Value("${app.bonds.tcmb.evds3-ts-cookie:}")
    private String tsCookie;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TcmbBondDataProvider() {
        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String getProviderName() {
        return "TCMB";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<BondQuoteDto> fetchLatestBondQuotes() {
        boolean cookiesSet = !jsessionId.isBlank() && !tsCookie.isBlank();
        log.info("[TCMB] Fetching bond data via EVDS3 (session cookies set: {})", cookiesSet);

        // 1. EVDS3'ten politika faizini ve son 12 aylık tarihçesini çek
        Map<LocalDate, BigDecimal> policyHistory = cookiesSet
                ? fetchPolicyRateHistory(LocalDate.now().minusMonths(12), LocalDate.now())
                : Collections.emptyMap();
        BigDecimal policyRate = !policyHistory.isEmpty()
                ? lastValue(policyHistory)
                : POLICY_RATE_FALLBACK;
        log.info("[TCMB] Policy rate: {}% (source: {}, history points: {})",
                policyRate,
                policyHistory.isEmpty() ? "fallback" : "EVDS3 live",
                policyHistory.size());

        // 2. Bugünün DiBS snapshot'u (+ ay-ay değişim için delta)
        BigDecimal monthlyDelta = computeMonthOverMonthDelta(policyHistory);
        List<BondQuoteDto> quotes = new ArrayList<>(buildDibsFromPolicyRate(policyRate, monthlyDelta));

        // 3. Geçmiş aylar için yield kayıtları (chart "Getiri Grafiği" için)
        for (Map.Entry<LocalDate, BigDecimal> entry : policyHistory.entrySet()) {
            LocalDate date = entry.getKey();
            BigDecimal rate = entry.getValue();
            if (date.equals(LocalDate.now())) continue; // bugünkü zaten var
            quotes.addAll(buildDibsHistoricalSnapshot(date, rate));
        }

        // 4. Bigpara'dan sukuk + eurobond
        try {
            List<BondQuoteDto> bigpara = scrapeBigparaBonds();
            quotes.addAll(bigpara.isEmpty() ? buildFallbackSukukAndEurobonds() : bigpara);
        } catch (Exception e) {
            log.warn("[TCMB] Bigpara scrape failed ({}), using seed sukuk/eurobond", e.getMessage());
            quotes.addAll(buildFallbackSukukAndEurobonds());
        }

        log.info("[TCMB] Built {} bond quotes ({} symbols × {} historical dates + current)",
                quotes.size(), SPREAD_BY_SYMBOL.size(), policyHistory.size());
        return quotes;
    }

    /** Bir tarih için tüm vadelerin yield snapshot'unu üretir (chart için historical data). */
    private List<BondQuoteDto> buildDibsHistoricalSnapshot(LocalDate date, BigDecimal policyRate) {
        List<BondQuoteDto> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : SPREAD_BY_SYMBOL.entrySet()) {
            String symbol = entry.getKey();
            BigDecimal yield = policyRate.add(entry.getValue());
            BondQuoteDto dto = new BondQuoteDto();
            dto.setSymbol(symbol);
            // İsim/tür/maturity bilgisini set etmiyoruz; instrument zaten var, sadece quote ekliyoruz
            dto.setQuoteDate(date);
            BigDecimal y = yield.setScale(2, RoundingMode.HALF_UP);
            dto.setYieldRate(y);
            BigDecimal price = new BigDecimal("100.00")
                    .subtract(y.multiply(new BigDecimal("0.05")))
                    .setScale(2, RoundingMode.HALF_UP);
            dto.setPrice(price);
            dto.setCleanPrice(price);
            dto.setDirtyPrice(price);
            dto.setSource("TCMB");
            list.add(dto);
        }
        return list;
    }

    /** TreeMap'in son (en güncel) değerini alır. */
    private BigDecimal lastValue(Map<LocalDate, BigDecimal> map) {
        BigDecimal last = null;
        for (BigDecimal v : map.values()) last = v;
        return last;
    }

    @Override
    public List<BondQuoteDto> fetchHistoricalYield(String symbol, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }

    @Override
    public Optional<BondQuoteDto> fetchBySymbol(String symbol) {
        return fetchLatestBondQuotes().stream()
                .filter(q -> symbol.equalsIgnoreCase(q.getSymbol()))
                .findFirst();
    }

    // ─────────────────────────── EVDS3 API ───────────────────────────

    /**
     * EVDS3 {@code POST /fe} endpoint'inden TCMB politika faizinin verilen tarih
     * aralığındaki aylık tarihçesini çeker.
     *
     * @return Tarih → değer LinkedHashMap (kronolojik sırayla); boş = EVDS3 erişimi başarısız
     */
    private Map<LocalDate, BigDecimal> fetchPolicyRateHistory(LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        try {
            String body = String.format(
                    "{\"type\":\"json\",\"series\":\"%s\",\"aggregationTypes\":\"last\",\"formulas\":\"0\"," +
                    "\"dateFormat\":\"0\",\"decimal\":\"2\",\"decimalSeperator\":\".\"," +
                    "\"startDate\":\"%s\",\"endDate\":\"%s\",\"frequency\":\"5\"," +
                    "\"groupSeperator\":true,\"isRaporSayfasi\":false,\"lang\":\"tr\"," +
                    "\"ozelFormuller\":[],\"sira\":\"0\",\"yon\":\"0\"}",
                    SERIES_POLICY_RATE,
                    from.format(EVDS_DATE),
                    to.format(EVDS_DATE));

            String response = webClient.post()
                    .uri(EVDS3_DATA_URL)
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://evds3.tcmb.gov.tr")
                    .header("Referer", "https://evds3.tcmb.gov.tr/")
                    .header("Cookie", "JSESSIONID=" + jsessionId + "; TS017d0b0b=" + tsCookie)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));

            if (response == null || response.isBlank() || response.startsWith("<")) {
                log.warn("[TCMB] EVDS3 returned non-JSON body (session may have expired)");
                return result;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                log.warn("[TCMB] EVDS3 returned no items for {}", SERIES_POLICY_RATE);
                return result;
            }

            String column = SERIES_POLICY_RATE.replace('.', '_');
            for (JsonNode item : items) {
                JsonNode dateNode = item.get("Tarih");
                JsonNode val = item.get(column);
                if (dateNode == null || val == null || val.isNull()) continue;
                String dateStr = dateNode.asText("").trim();      // "2025-12"
                String valStr = val.asText("").trim();
                if (dateStr.isEmpty() || valStr.isEmpty() || "null".equalsIgnoreCase(valStr)) continue;
                try {
                    BigDecimal parsed = new BigDecimal(valStr.replace(",", "."));
                    if (parsed.compareTo(BigDecimal.ZERO) <= 0) continue;
                    // "YYYY-MM" → ayın 1'i (aylık seri)
                    String[] parts = dateStr.split("-");
                    if (parts.length < 2) continue;
                    LocalDate d = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
                    result.put(d, parsed);
                } catch (NumberFormatException ignored) {}
            }
            log.info("[TCMB] EVDS3 policy rate history: {} points", result.size());
        } catch (Exception e) {
            log.warn("[TCMB] EVDS3 history call failed: {}", e.getMessage());
        }
        return result;
    }

    // ─────────────────────────── DiBS oluştur ───────────────────────────

    /**
     * Politika faizinin son ay ile bir önceki ay arasındaki mutlak farkını döner.
     * Yield = policy + spread olduğundan, türetilmiş tüm vadelerin değişimi aynıdır.
     */
    private BigDecimal computeMonthOverMonthDelta(Map<LocalDate, BigDecimal> history) {
        if (history.size() < 2) return BigDecimal.ZERO;
        BigDecimal prev = null;
        BigDecimal last = null;
        for (BigDecimal v : history.values()) {
            prev = last;
            last = v;
        }
        if (prev == null || last == null) return BigDecimal.ZERO;
        return last.subtract(prev).setScale(2, RoundingMode.HALF_UP);
    }

    private List<BondQuoteDto> buildDibsFromPolicyRate(BigDecimal policyRate, BigDecimal changeRate) {
        List<BondQuoteDto> list = new ArrayList<>();
        LocalDate today = LocalDate.now();

        list.add(makeBond("TR91GB", isin(today.plusDays(91), "TVB"),
                "91 Günlük Hazine Bonosu", DebtInstrumentType.TREASURY_BILL,
                today.plusDays(91), BigDecimal.ZERO, policyRate.add(SPREAD_91D), changeRate));
        list.add(makeBond("TR182GB", isin(today.plusDays(182), "TVB"),
                "182 Günlük Hazine Bonosu", DebtInstrumentType.TREASURY_BILL,
                today.plusDays(182), BigDecimal.ZERO, policyRate.add(SPREAD_182D), changeRate));
        list.add(makeBond("TR364GB", isin(today.plusDays(364), "TVB"),
                "364 Günlük Hazine Bonosu", DebtInstrumentType.TREASURY_BILL,
                today.plusDays(364), BigDecimal.ZERO, policyRate.add(SPREAD_364D), changeRate));

        BigDecimal y2 = policyRate.add(SPREAD_2Y);
        BigDecimal y5 = policyRate.add(SPREAD_5Y);
        BigDecimal y10 = policyRate.add(SPREAD_10Y);

        list.add(makeBond("TR2YT", isin(today.plusYears(2), "TVA"),
                "Türkiye 2 Yıllık Devlet Tahvili", DebtInstrumentType.GOVERNMENT_BOND,
                today.plusYears(2), y2.subtract(new BigDecimal("0.50")), y2, changeRate));
        list.add(makeBond("TR5YT", isin(today.plusYears(5), "TVA"),
                "Türkiye 5 Yıllık Devlet Tahvili", DebtInstrumentType.GOVERNMENT_BOND,
                today.plusYears(5), y5.subtract(new BigDecimal("1.00")), y5, changeRate));
        list.add(makeBond("TR10YT", isin(today.plusYears(10), "TVA"),
                "Türkiye 10 Yıllık Devlet Tahvili", DebtInstrumentType.GOVERNMENT_BOND,
                today.plusYears(10), y10.subtract(new BigDecimal("1.50")), y10, changeRate));

        return list;
    }

    private BondQuoteDto makeBond(String symbol, String isin, String name,
                                  DebtInstrumentType type, LocalDate maturity,
                                  BigDecimal couponRate, BigDecimal yieldRate, BigDecimal changeRate) {
        BondQuoteDto dto = new BondQuoteDto();
        dto.setSymbol(symbol);
        dto.setIsin(isin);
        dto.setName(name);
        dto.setType(type);
        dto.setIssuer("Hazine ve Maliye Bakanlığı");
        dto.setCurrency("TRY");
        dto.setMaturityDate(maturity);
        dto.setCouponRate(couponRate.setScale(2, RoundingMode.HALF_UP));
        dto.setCouponType(couponRate.compareTo(BigDecimal.ZERO) == 0 ? "Sıfır Kuponlu" : "Sabit");
        dto.setQuoteDate(LocalDate.now());
        BigDecimal y = yieldRate.setScale(2, RoundingMode.HALF_UP);
        dto.setYieldRate(y);
        BigDecimal price = new BigDecimal("100.00")
                .subtract(y.subtract(couponRate).multiply(new BigDecimal("0.10")))
                .setScale(2, RoundingMode.HALF_UP);
        dto.setPrice(price);
        dto.setCleanPrice(price);
        dto.setDirtyPrice(price);
        dto.setChangeRate(changeRate != null ? changeRate : BigDecimal.ZERO);
        dto.setSource("TCMB");
        return dto;
    }

    private String isin(LocalDate maturity, String suffix) {
        return String.format("TRA%02d%02d%02d%s",
                maturity.getYear() % 100,
                maturity.getMonthValue(),
                maturity.getDayOfMonth(),
                suffix);
    }

    // ─────────────────────────── Bigpara sukuk/eurobond ───────────────────────────

    private List<BondQuoteDto> scrapeBigparaBonds() throws Exception {
        Document doc = Jsoup.connect(BIGPARA_BONDS_URL)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .get();

        List<BondQuoteDto> result = new ArrayList<>();
        int sukukCount = 0, eurobondCount = 0;
        final int maxEach = 3;

        for (Element row : doc.select("table tr")) {
            Element symbolCell = row.selectFirst("td:nth-child(1)");
            if (symbolCell == null) continue;
            String symbol = symbolCell.text().trim();
            if (symbol.isEmpty() || symbol.length() > 20) continue;

            String upper = symbol.toUpperCase(Locale.ROOT);
            boolean isSukuk = upper.contains("KIR") || upper.contains("KS") || upper.contains("SUK");
            boolean isEurobond = upper.contains("EUR") || upper.contains("USD") || upper.contains("EU");
            if (!isSukuk && !isEurobond) continue;
            if (isSukuk && sukukCount >= maxEach) continue;
            if (isEurobond && eurobondCount >= maxEach) continue;

            BigDecimal price = null, yield = null;
            for (int i = 2; i <= Math.min(6, row.children().size()); i++) {
                BigDecimal val = parseTrNumber(row.child(i - 1).text().trim());
                if (val == null) continue;
                if (price == null && val.compareTo(new BigDecimal("80")) >= 0 && val.compareTo(new BigDecimal("120")) <= 0) {
                    price = val;
                } else if (yield == null && val.compareTo(new BigDecimal("1")) >= 0 && val.compareTo(new BigDecimal("100")) <= 0) {
                    yield = val;
                }
            }
            if (price == null && yield == null) continue;

            BondQuoteDto dto = new BondQuoteDto();
            dto.setSymbol(symbol);
            dto.setName(symbol);
            dto.setType(isSukuk ? DebtInstrumentType.LEASE_CERTIFICATE : DebtInstrumentType.EUROBOND);
            dto.setIssuer(isSukuk ? "Hazine ve Maliye Bakanlığı" : "Türkiye Cumhuriyeti");
            dto.setCurrency(isEurobond ? "USD" : "TRY");
            dto.setQuoteDate(LocalDate.now());
            if (price != null) {
                dto.setPrice(price);
                dto.setCleanPrice(price);
                dto.setDirtyPrice(price);
            }
            if (yield != null) dto.setYieldRate(yield);
            dto.setSource("BIST/BIGPARA");
            result.add(dto);

            if (isSukuk) sukukCount++;
            else eurobondCount++;
        }

        log.info("[TCMB] Bigpara scraped: {} sukuk, {} eurobond", sukukCount, eurobondCount);
        return result;
    }

    private List<BondQuoteDto> buildFallbackSukukAndEurobonds() {
        List<BondQuoteDto> list = new ArrayList<>();
        LocalDate today = LocalDate.now();

        BondQuoteDto sukuk = new BondQuoteDto();
        sukuk.setSymbol("TRKS2Y");
        sukuk.setIsin(isin(today.plusYears(2), "KSA"));
        sukuk.setName("2 Yıllık TL Kira Sertifikası");
        sukuk.setType(DebtInstrumentType.LEASE_CERTIFICATE);
        sukuk.setIssuer("Hazine ve Maliye Bakanlığı");
        sukuk.setCurrency("TRY");
        sukuk.setMaturityDate(today.plusYears(2));
        sukuk.setCouponRate(new BigDecimal("36.50"));
        sukuk.setCouponType("Sabit");
        sukuk.setQuoteDate(today);
        sukuk.setYieldRate(new BigDecimal("37.20"));
        sukuk.setPrice(new BigDecimal("99.50"));
        sukuk.setCleanPrice(new BigDecimal("99.50"));
        sukuk.setDirtyPrice(new BigDecimal("99.80"));
        sukuk.setSource("BIST/BIGPARA");
        list.add(sukuk);

        BondQuoteDto eb2030 = new BondQuoteDto();
        eb2030.setSymbol("TREU2030");
        eb2030.setIsin("XS2186072186");
        eb2030.setName("Türkiye 2030 Eurobond (USD)");
        eb2030.setType(DebtInstrumentType.EUROBOND);
        eb2030.setIssuer("Türkiye Cumhuriyeti");
        eb2030.setCurrency("USD");
        eb2030.setMaturityDate(LocalDate.of(2030, 6, 14));
        eb2030.setCouponRate(new BigDecimal("4.875"));
        eb2030.setCouponType("Sabit");
        eb2030.setQuoteDate(today);
        eb2030.setYieldRate(new BigDecimal("7.40"));
        eb2030.setPrice(new BigDecimal("89.50"));
        eb2030.setCleanPrice(new BigDecimal("89.50"));
        eb2030.setDirtyPrice(new BigDecimal("90.20"));
        eb2030.setSource("BIST/BIGPARA");
        list.add(eb2030);

        BondQuoteDto eb2035 = new BondQuoteDto();
        eb2035.setSymbol("TREU2035");
        eb2035.setIsin("US900123CY28");
        eb2035.setName("Türkiye 2035 Eurobond (USD)");
        eb2035.setType(DebtInstrumentType.EUROBOND);
        eb2035.setIssuer("Türkiye Cumhuriyeti");
        eb2035.setCurrency("USD");
        eb2035.setMaturityDate(LocalDate.of(2035, 4, 25));
        eb2035.setCouponRate(new BigDecimal("7.625"));
        eb2035.setCouponType("Sabit");
        eb2035.setQuoteDate(today);
        eb2035.setYieldRate(new BigDecimal("8.10"));
        eb2035.setPrice(new BigDecimal("96.30"));
        eb2035.setCleanPrice(new BigDecimal("96.30"));
        eb2035.setDirtyPrice(new BigDecimal("97.10"));
        eb2035.setSource("BIST/BIGPARA");
        list.add(eb2035);

        return list;
    }

    private BigDecimal parseTrNumber(String s) {
        if (s == null) return null;
        String c = s.replaceAll("[^0-9,.\\-]", "");
        if (c.isEmpty()) return null;
        if (c.contains(",") && c.contains(".")) c = c.replace(".", "").replace(",", ".");
        else if (c.contains(",")) c = c.replace(",", ".");
        try { return new BigDecimal(c); } catch (NumberFormatException e) { return null; }
    }
}
