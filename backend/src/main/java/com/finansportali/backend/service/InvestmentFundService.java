package com.finansportali.backend.service;

import com.finansportali.backend.entity.InvestmentFund;
import com.finansportali.backend.repository.InvestmentFundRepository;
import com.finansportali.backend.service.client.fund.TefasFundFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Manages investment fund (TEFAS) data: cached read queries (listing, filtering
 * by type/company, search, top performers) and write operations that refresh
 * fund prices and returns from the TEFAS fetcher.
 */
@Service
public class InvestmentFundService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentFundService.class);

    private final InvestmentFundRepository repository;
    private final TefasFundFetcher tefasFundFetcher;

    public InvestmentFundService(InvestmentFundRepository repository, TefasFundFetcher tefasFundFetcher) {
        this.repository = repository;
        this.tefasFundFetcher = tefasFundFetcher;
    }

    /** All funds ordered by total value, descending. */
    @Cacheable("investment-funds")
    public List<InvestmentFund> getAllFunds() {
        return repository.findAllOrderByTotalValueDesc();
    }

    /** Funds of a given type, ordered by total value descending. */
    @Cacheable("funds-by-type")
    public List<InvestmentFund> getFundsByType(String fundType) {
        return repository.findByFundTypeOrderByTotalValueDesc(fundType);
    }

    /** Distinct fund types present in the catalog. */
    public List<String> getFundTypes() {
        return repository.findDistinctFundTypes();
    }

    /** Distinct fund management companies present in the catalog. */
    public List<String> getManagementCompanies() {
        return repository.findDistinctManagementCompanies();
    }

    /** Funds managed by the given company, ordered by total value descending. */
    public List<InvestmentFund> getFundsByCompany(String company) {
        return repository.findByManagementCompanyOrderByTotalValueDesc(company);
    }

    /** Looks up a single fund by its TEFAS code. */
    public Optional<InvestmentFund> getFundByCode(String fundCode) {
        return repository.findByFundCode(fundCode);
    }

    /** Funds ranked by performance (top yearly returns). */
    public List<InvestmentFund> getTopPerformers() {
        return repository.findTopPerformers();
    }

    /** Free-text search across fund code/name. */
    public List<InvestmentFund> searchFunds(String query) {
        return repository.searchFunds(query);
    }

    /**
     * Fetches the latest fund data from TEFAS and upserts it: updates prices and
     * returns for existing funds, inserts new ones. Logs and swallows failures.
     */
    public void updateFundPrices() {
        log.info("Updating investment fund prices from TEFAS...");
        
        try {
            // TEFAS'tan gerçek fon verilerini çek
            List<InvestmentFund> freshFunds = tefasFundFetcher.fetchAllFunds();
            
            if (freshFunds.isEmpty()) {
                log.warn("No funds fetched from TEFAS, skipping update");
                return;
            }
            
            int updated = 0;
            int added = 0;
            
            for (InvestmentFund freshFund : freshFunds) {
                Optional<InvestmentFund> existingFund = repository.findByFundCode(freshFund.getFundCode());
                
                if (existingFund.isPresent()) {
                    // Mevcut fonu güncelle.
                    InvestmentFund fund = existingFund.get();
                    // Birim fiyat + günlük getiri ayrı bir per-fon TEFAS çağrısından
                    // gelir; o çağrı bu tazelemede başarısız olduysa fresh değer 0/null
                    // gelir. Eski (bilinen) değeri 0 ile EZME — aksi halde geçici bir
                    // TEFAS hatası önceden doğru olan fiyatı siler ("—" görünür).
                    if (freshFund.getUnitPrice() != null && freshFund.getUnitPrice().signum() > 0) {
                        fund.setUnitPrice(freshFund.getUnitPrice());
                        fund.setPriceDate(freshFund.getPriceDate());
                    }
                    if (freshFund.getTotalValue() != null && freshFund.getTotalValue().signum() > 0) {
                        fund.setTotalValue(freshFund.getTotalValue());
                    }
                    if (freshFund.getDailyReturn() != null) {
                        fund.setDailyReturn(freshFund.getDailyReturn());
                    }
                    fund.setWeeklyReturn(freshFund.getWeeklyReturn());
                    fund.setMonthlyReturn(freshFund.getMonthlyReturn());
                    fund.setThreeMonthReturn(freshFund.getThreeMonthReturn());
                    fund.setSixMonthReturn(freshFund.getSixMonthReturn());
                    fund.setYearlyReturn(freshFund.getYearlyReturn());
                    fund.setThreeYearReturn(freshFund.getThreeYearReturn());
                    fund.setFiveYearReturn(freshFund.getFiveYearReturn());
                    fund.setRiskLevel(freshFund.getRiskLevel());
                    fund.setFundType(freshFund.getFundType());
                    fund.setManagementCompany(freshFund.getManagementCompany());
                    fund.setFundName(freshFund.getFundName());
                    repository.save(fund);
                    updated++;
                } else {
                    // Yeni fon ekle
                    repository.save(freshFund);
                    added++;
                }
            }
            
            log.info("Updated {} funds, added {} new funds from TEFAS", updated, added);
            
        } catch (Exception e) {
            log.error("Error updating fund prices from TEFAS: {}", e.getMessage(), e);
        }
    }

    /**
     * Wipe all funds from DB. Used by admin "reset" flow before refreshing
     * from a real source (e.g. when migrating off demo data).
     */
    public int wipeAll() {
        long count = repository.count();
        repository.deleteAllInBatch();
        log.info("Wiped {} investment funds from DB", count);
        return (int) count;
    }

    /** Populates the fund catalog from TEFAS on first boot when the table is empty. */
    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Investment funds already exist in database, skipping seed");
            return;
        }

        log.info("No investment funds found, fetching from TEFAS...");
        
        try {
            // TEFAS'tan gerçek fon verilerini çek
            List<InvestmentFund> funds = tefasFundFetcher.fetchAllFunds();
            
            if (funds.isEmpty()) {
                log.error("Could not fetch any funds from TEFAS. Database will remain empty until manual refresh or next scheduled update.");
                log.error("To manually refresh, call POST /api/v1/admin/investment-funds/refresh as ADMIN user");
                return;
            }
            
            // Fonları veritabanına kaydet
            repository.saveAll(funds);
            log.info("Seeded {} investment funds from TEFAS", funds.size());
            
        } catch (Exception e) {
            log.error("Error fetching funds from TEFAS: {}", e.getMessage(), e);
            log.error("Database will remain empty until manual refresh or next scheduled update.");
            log.error("To manually refresh, call POST /api/v1/admin/investment-funds/refresh as ADMIN user");
        }
    }
}