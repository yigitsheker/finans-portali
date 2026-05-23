package com.finansportali.backend.service;

import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentQuote;
import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.dto.response.bond.BondDetailDto;
import com.finansportali.backend.dto.response.bond.BondHistoryPointDto;
import com.finansportali.backend.dto.response.bond.BondListItemDto;
import com.finansportali.backend.dto.response.bond.BondSummaryDto;
import com.finansportali.backend.repository.DebtInstrumentQuoteRepository;
import com.finansportali.backend.repository.DebtInstrumentRepository;
import com.finansportali.backend.repository.DebtInstrumentSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class DebtInstrumentService {

    private static final Logger log = LoggerFactory.getLogger(DebtInstrumentService.class);

    private final DebtInstrumentRepository instrumentRepo;
    private final DebtInstrumentQuoteRepository quoteRepo;

    public DebtInstrumentService(DebtInstrumentRepository instrumentRepo,
                                 DebtInstrumentQuoteRepository quoteRepo) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
    }

    /**
     * Tüm aktif borçlanma araçlarını listeler (filtrelerle).
     */
    @Transactional(readOnly = true)
    public List<BondListItemDto> listBonds(DebtInstrumentType type, String currency,
                                           LocalDate maturityFrom, LocalDate maturityTo,
                                           String search) {
        log.info("Listing bonds with filters: type={}, currency={}, maturityFrom={}, maturityTo={}, search={}",
                type, currency, maturityFrom, maturityTo, search);

        // Use Specification for dynamic query building
        List<DebtInstrument> instruments = instrumentRepo.findAll(
            DebtInstrumentSpecifications.withFilters(type, currency, maturityFrom, maturityTo, search)
        );

        return instruments.stream()
            .map(this::mapToListItem)
            .toList();
    }

    /**
     * Borçlanma aracı detayını getirir.
     */
    @Transactional(readOnly = true)
    public BondDetailDto getBondDetail(Long id) {
        DebtInstrument instrument = instrumentRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Bond not found: " + id));

        DebtInstrumentQuote latestQuote = quoteRepo.findLatestByInstrument(instrument).orElse(null);

        BondDetailDto dto = new BondDetailDto();
        dto.setId(instrument.getId());
        dto.setSymbol(instrument.getSymbol());
        dto.setIsin(instrument.getIsin());
        dto.setName(instrument.getName());
        dto.setType(instrument.getType());
        dto.setIssuer(instrument.getIssuer());
        dto.setCurrency(instrument.getCurrency());
        dto.setMaturityDate(instrument.getMaturityDate());
        dto.setCouponRate(instrument.getCouponRate());
        dto.setCouponType(instrument.getCouponType());

        if (latestQuote != null) {
            dto.setLatestPrice(latestQuote.getPrice());
            dto.setLatestYieldRate(latestQuote.getYieldRate());
            dto.setCleanPrice(latestQuote.getCleanPrice());
            dto.setDirtyPrice(latestQuote.getDirtyPrice());
            dto.setVolume(latestQuote.getVolume());
            dto.setChangeRate(latestQuote.getChangeRate());
            dto.setLastUpdatedAt(latestQuote.getCreatedAt());
            dto.setSource(latestQuote.getSource());
        }

        // Calculate days to maturity
        if (instrument.getMaturityDate() != null) {
            long daysToMaturity = ChronoUnit.DAYS.between(LocalDate.now(), instrument.getMaturityDate());
            dto.setDaysToMaturity(daysToMaturity);
        }

        return dto;
    }

    /**
     * Tarihsel getiri verilerini getirir.
     */
    @Transactional(readOnly = true)
    public List<BondHistoryPointDto> getBondHistory(Long id, LocalDate from, LocalDate to) {
        DebtInstrument instrument = instrumentRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Bond not found: " + id));

        List<DebtInstrumentQuote> quotes = quoteRepo.findHistoricalQuotes(instrument, from, to);

        return quotes.stream()
            .map(q -> {
                BondHistoryPointDto dto = new BondHistoryPointDto();
                dto.setDate(q.getQuoteDate());
                dto.setPrice(q.getPrice());
                dto.setYieldRate(q.getYieldRate());
                dto.setVolume(q.getVolume());
                return dto;
            })
            .toList();
    }

    /**
     * Özet istatistikleri hesaplar.
     */
    @Transactional(readOnly = true)
    public BondSummaryDto getSummary() {
        List<DebtInstrument> allInstruments = instrumentRepo.findByActiveTrue();

        BondSummaryDto summary = new BondSummaryDto();
        summary.setTotalInstruments(allInstruments.size());

        SummaryStats stats = new SummaryStats();
        for (DebtInstrument inst : allInstruments) {
            accumulateYield(stats, inst);
            accumulateMaturity(stats, inst.getMaturityDate());
        }
        stats.applyTo(summary);

        quoteRepo.findLatestQuoteDate().ifPresent(summary::setLastUpdateDate);
        return summary;
    }

    private void accumulateYield(SummaryStats stats, DebtInstrument inst) {
        DebtInstrumentQuote quote = quoteRepo.findLatestByInstrument(inst).orElse(null);
        if (quote == null || quote.getYieldRate() == null) return;
        stats.yields.add(quote.getYieldRate());
        if (quote.getYieldRate().compareTo(stats.maxYield) > 0) {
            stats.maxYield = quote.getYieldRate();
        }
    }

    private static void accumulateMaturity(SummaryStats stats, LocalDate maturity) {
        if (maturity == null) return;
        if (stats.nearestMaturity == null || maturity.isBefore(stats.nearestMaturity)) {
            stats.nearestMaturity = maturity;
        }
        if (stats.farthestMaturity == null || maturity.isAfter(stats.farthestMaturity)) {
            stats.farthestMaturity = maturity;
        }
    }

    /**
     * Mutable accumulator threaded through the per-instrument loop in
     * getSummary(). Kept local because nothing outside the service needs to
     * see these intermediate stats.
     */
    private static final class SummaryStats {
        final List<BigDecimal> yields = new ArrayList<>();
        BigDecimal maxYield = BigDecimal.ZERO;
        LocalDate nearestMaturity;
        LocalDate farthestMaturity;

        void applyTo(BondSummaryDto summary) {
            if (!yields.isEmpty()) {
                BigDecimal avg = yields.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(yields.size()), 2, RoundingMode.HALF_UP);
                summary.setAverageYield(avg);
            }
            summary.setHighestYield(maxYield);
            summary.setNearestMaturity(nearestMaturity);
            summary.setFarthestMaturity(farthestMaturity);
        }
    }

    private BondListItemDto mapToListItem(DebtInstrument instrument) {
        DebtInstrumentQuote latestQuote = quoteRepo.findLatestByInstrument(instrument).orElse(null);

        BondListItemDto dto = new BondListItemDto();
        dto.setId(instrument.getId());
        dto.setSymbol(instrument.getSymbol());
        dto.setIsin(instrument.getIsin());
        dto.setName(instrument.getName());
        dto.setType(instrument.getType());
        dto.setCurrency(instrument.getCurrency());
        dto.setMaturityDate(instrument.getMaturityDate());
        dto.setCouponRate(instrument.getCouponRate());

        if (latestQuote != null) {
            dto.setLatestPrice(latestQuote.getPrice());
            dto.setLatestYieldRate(latestQuote.getYieldRate());
            dto.setChangeRate(latestQuote.getChangeRate());
            dto.setSource(latestQuote.getSource());
            dto.setLastUpdatedAt(latestQuote.getCreatedAt());
        }

        return dto;
    }
}
