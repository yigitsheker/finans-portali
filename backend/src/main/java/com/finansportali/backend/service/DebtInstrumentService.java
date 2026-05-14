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
import java.util.stream.Collectors;

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
            .collect(Collectors.toList());
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
            .collect(Collectors.toList());
    }

    /**
     * Özet istatistikleri hesaplar.
     */
    @Transactional(readOnly = true)
    public BondSummaryDto getSummary() {
        List<DebtInstrument> allInstruments = instrumentRepo.findByActiveTrue();
        
        BondSummaryDto summary = new BondSummaryDto();
        summary.setTotalInstruments(allInstruments.size());

        // Calculate average yield
        List<BigDecimal> yields = new ArrayList<>();
        BigDecimal maxYield = BigDecimal.ZERO;
        LocalDate nearestMaturity = null;
        LocalDate farthestMaturity = null;

        for (DebtInstrument inst : allInstruments) {
            DebtInstrumentQuote quote = quoteRepo.findLatestByInstrument(inst).orElse(null);
            if (quote != null && quote.getYieldRate() != null) {
                yields.add(quote.getYieldRate());
                if (quote.getYieldRate().compareTo(maxYield) > 0) {
                    maxYield = quote.getYieldRate();
                }
            }

            if (inst.getMaturityDate() != null) {
                if (nearestMaturity == null || inst.getMaturityDate().isBefore(nearestMaturity)) {
                    nearestMaturity = inst.getMaturityDate();
                }
                if (farthestMaturity == null || inst.getMaturityDate().isAfter(farthestMaturity)) {
                    farthestMaturity = inst.getMaturityDate();
                }
            }
        }

        if (!yields.isEmpty()) {
            BigDecimal avgYield = yields.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(yields.size()), 2, RoundingMode.HALF_UP);
            summary.setAverageYield(avgYield);
        }

        summary.setHighestYield(maxYield);
        summary.setNearestMaturity(nearestMaturity);
        summary.setFarthestMaturity(farthestMaturity);

        // Last update time
        quoteRepo.findLatestQuoteDate().ifPresent(summary::setLastUpdateDate);

        return summary;
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
