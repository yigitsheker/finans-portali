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

@Service
public class InvestmentFundService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentFundService.class);

    private final InvestmentFundRepository repository;
    private final TefasFundFetcher tefasFundFetcher;

    public InvestmentFundService(InvestmentFundRepository repository, TefasFundFetcher tefasFundFetcher) {
        this.repository = repository;
        this.tefasFundFetcher = tefasFundFetcher;
    }

    @Cacheable("investment-funds")
    public List<InvestmentFund> getAllFunds() {
        return repository.findAllOrderByTotalValueDesc();
    }

    @Cacheable("funds-by-type")
    public List<InvestmentFund> getFundsByType(String fundType) {
        return repository.findByFundTypeOrderByTotalValueDesc(fundType);
    }

    public List<String> getFundTypes() {
        return repository.findDistinctFundTypes();
    }

    public List<String> getManagementCompanies() {
        return repository.findDistinctManagementCompanies();
    }

    public List<InvestmentFund> getFundsByCompany(String company) {
        return repository.findByManagementCompanyOrderByTotalValueDesc(company);
    }

    public Optional<InvestmentFund> getFundByCode(String fundCode) {
        return repository.findByFundCode(fundCode);
    }

    public List<InvestmentFund> getTopPerformers() {
        return repository.findTopPerformers();
    }

    public List<InvestmentFund> searchFunds(String query) {
        return repository.searchFunds(query);
    }

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
                    // Mevcut fonu güncelle
                    InvestmentFund fund = existingFund.get();
                    fund.setUnitPrice(freshFund.getUnitPrice());
                    fund.setTotalValue(freshFund.getTotalValue());
                    fund.setPriceDate(freshFund.getPriceDate());
                    fund.setDailyReturn(freshFund.getDailyReturn());
                    fund.setWeeklyReturn(freshFund.getWeeklyReturn());
                    fund.setMonthlyReturn(freshFund.getMonthlyReturn());
                    fund.setYearlyReturn(freshFund.getYearlyReturn());
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