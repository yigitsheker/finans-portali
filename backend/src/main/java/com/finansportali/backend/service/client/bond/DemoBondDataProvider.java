package com.finansportali.backend.service.client.bond;

import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * Demo/fallback tahvil veri sağlayıcısı.
 * Gerçek veri kaynağı olmadığında veya test amaçlı kullanılır.
 * Türkiye DİBS piyasasına benzer örnek veriler üretir.
 */
@Service
@ConditionalOnProperty(name = "app.bonds.provider", havingValue = "DEMO", matchIfMissing = false)
public class DemoBondDataProvider implements BondDataProvider {

    private static final Logger log = LoggerFactory.getLogger(DemoBondDataProvider.class);
    // SecureRandom (not java.util.Random) per Sonar S2245 — this stream
    // only feeds demo/test rows, but using the crypto-grade RNG keeps the
    // scanner quiet without measurable cost (one-time seeding).
    private final SecureRandom random = new SecureRandom();

    @Override
    public String getProviderName() {
        return "DEMO";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<BondQuoteDto> fetchLatestBondQuotes() {
        log.info("[DEMO] Fetching demo bond data...");
        
        List<BondQuoteDto> quotes = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 2 Yıllık Devlet Tahvili
        quotes.add(createDemoBond(
            "TR2YT", "TRA260514TVA", "Türkiye 2 Yıllık Devlet Tahvili",
            DebtInstrumentType.GOVERNMENT_BOND, today.plusYears(2),
            new BigDecimal("36.80"), new BigDecimal("37.19")
        ));

        // 5 Yıllık Devlet Tahvili
        quotes.add(createDemoBond(
            "TR5YT", "TRA290514TVA", "Türkiye 5 Yıllık Devlet Tahvili",
            DebtInstrumentType.GOVERNMENT_BOND, today.plusYears(5),
            new BigDecimal("38.50"), new BigDecimal("39.12")
        ));

        // 10 Yıllık Devlet Tahvili
        quotes.add(createDemoBond(
            "TR10YT", "TRA340514TVA", "Türkiye 10 Yıllık Devlet Tahvili",
            DebtInstrumentType.GOVERNMENT_BOND, today.plusYears(10),
            new BigDecimal("40.20"), new BigDecimal("41.05")
        ));

        // 91 Günlük Hazine Bonosu
        quotes.add(createDemoBond(
            "TR91GB", "TRA260809TVB", "91 Günlük Hazine Bonosu",
            DebtInstrumentType.TREASURY_BILL, today.plusDays(91),
            BigDecimal.ZERO, new BigDecimal("35.50")
        ));

        // 182 Günlük Hazine Bonosu
        quotes.add(createDemoBond(
            "TR182GB", "TRA261114TVB", "182 Günlük Hazine Bonosu",
            DebtInstrumentType.TREASURY_BILL, today.plusDays(182),
            BigDecimal.ZERO, new BigDecimal("36.20")
        ));

        // 364 Günlük Hazine Bonosu
        quotes.add(createDemoBond(
            "TR364GB", "TRA270509TVB", "364 Günlük Hazine Bonosu",
            DebtInstrumentType.TREASURY_BILL, today.plusDays(364),
            BigDecimal.ZERO, new BigDecimal("37.10")
        ));

        // Kira Sertifikası
        quotes.add(createDemoBond(
            "TRKS2Y", "TRA260514KSA", "2 Yıllık Kira Sertifikası",
            DebtInstrumentType.LEASE_CERTIFICATE, today.plusYears(2),
            new BigDecimal("37.00"), new BigDecimal("37.45")
        ));

        // Eurobond (USD)
        BondQuoteDto eurobond = createDemoBond(
            "TREU2030", "XS2345678901", "Türkiye 2030 Eurobond",
            DebtInstrumentType.EUROBOND, LocalDate.of(2030, 6, 15),
            new BigDecimal("7.50"), new BigDecimal("8.25")
        );
        eurobond.setCurrency("USD");
        quotes.add(eurobond);

        log.info("[DEMO] Generated {} demo bond quotes", quotes.size());
        return quotes;
    }

    @Override
    public List<BondQuoteDto> fetchHistoricalYield(String symbol, LocalDate from, LocalDate to) {
        log.info("[DEMO] Fetching historical data for {} from {} to {}", symbol, from, to);
        
        List<BondQuoteDto> history = new ArrayList<>();
        LocalDate current = from;
        BigDecimal baseYield = new BigDecimal("37.00");

        while (!current.isAfter(to)) {
            // Skip weekends
            if (current.getDayOfWeek().getValue() < 6) {
                BondQuoteDto quote = new BondQuoteDto();
                quote.setSymbol(symbol);
                quote.setQuoteDate(current);
                quote.setYieldRate(baseYield.add(BigDecimal.valueOf(random.nextDouble() * 2 - 1)));
                quote.setPrice(new BigDecimal("98.00").add(BigDecimal.valueOf(random.nextDouble() * 4 - 2)));
                quote.setSource("DEMO");
                history.add(quote);
            }
            current = current.plusDays(1);
        }

        return history;
    }

    @Override
    public Optional<BondQuoteDto> fetchBySymbol(String symbol) {
        return fetchLatestBondQuotes().stream()
            .filter(q -> q.getSymbol().equals(symbol))
            .findFirst();
    }

    private BondQuoteDto createDemoBond(String symbol, String isin, String name,
                                        DebtInstrumentType type, LocalDate maturity,
                                        BigDecimal couponRate, BigDecimal yieldRate) {
        BondQuoteDto dto = new BondQuoteDto();
        dto.setSymbol(symbol);
        dto.setIsin(isin);
        dto.setName(name);
        dto.setType(type);
        dto.setIssuer("Hazine ve Maliye Bakanlığı");
        dto.setCurrency("TRY");
        dto.setMaturityDate(maturity);
        dto.setCouponRate(couponRate);
        dto.setCouponType(couponRate.compareTo(BigDecimal.ZERO) == 0 ? "Sıfır Kuponlu" : "Sabit");
        
        // Quote data
        dto.setQuoteDate(LocalDate.now());
        dto.setYieldRate(yieldRate);
        dto.setPrice(new BigDecimal("98.50").add(BigDecimal.valueOf(random.nextDouble() * 3 - 1.5)));
        dto.setCleanPrice(dto.getPrice());
        dto.setDirtyPrice(dto.getPrice().add(new BigDecimal("0.50")));
        // Long literal forces the addition into long arithmetic so Sonar
        // S2184 doesn't flag an int+int overflow risk on the operand.
        dto.setVolume(BigDecimal.valueOf(random.nextInt(1_000_000) + 500_000L));
        dto.setChangeRate(BigDecimal.valueOf(random.nextDouble() * 1 - 0.5));
        dto.setSource("DEMO");
        
        return dto;
    }
}
