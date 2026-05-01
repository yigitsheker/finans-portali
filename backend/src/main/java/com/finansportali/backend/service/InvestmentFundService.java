package com.finansportali.backend.service;

import com.finansportali.backend.domain.InvestmentFund;
import com.finansportali.backend.repo.InvestmentFundRepository;
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

    public InvestmentFundService(InvestmentFundRepository repository) {
        this.repository = repository;
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

    @Scheduled(initialDelay = 15_000, fixedDelay = 6 * 60 * 60 * 1000L) // Every 6 hours
    public void updateFundPrices() {
        log.info("Updating investment fund prices...");
        // Bu kısımda gerçek fon verilerini çekecek API entegrasyonu yapılabilir
        // Şimdilik örnek veri güncelleme yapıyoruz
        
        List<InvestmentFund> funds = repository.findAll();
        int updated = 0;
        
        for (InvestmentFund fund : funds) {
            // Simulated price update (gerçek uygulamada API'den gelecek)
            BigDecimal currentPrice = fund.getUnitPrice();
            BigDecimal change = currentPrice.multiply(BigDecimal.valueOf((Math.random() - 0.5) * 0.02)); // ±1% değişim
            BigDecimal newPrice = currentPrice.add(change);
            
            fund.setUnitPrice(newPrice);
            fund.setPriceDate(LocalDate.now());
            
            // Günlük getiri hesapla
            BigDecimal dailyReturn = change.divide(currentPrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            fund.setDailyReturn(dailyReturn);
            
            repository.save(fund);
            updated++;
        }
        
        log.info("Updated {} investment fund prices", updated);
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) return;

        LocalDate today = LocalDate.now();

        // Örnek fon verileri
        repository.save(new InvestmentFund(
                "AKB001", "Akbank Hisse Senedi Fonu", "Hisse Senedi Fonu", "Akbank Portföy",
                new BigDecimal("15.4567"), new BigDecimal("125000000"), today
        ));

        repository.save(new InvestmentFund(
                "GAR002", "Garanti Borçlanma Araçları Fonu", "Borçlanma Araçları Fonu", "Garanti Portföy",
                new BigDecimal("8.9876"), new BigDecimal("89000000"), today
        ));

        repository.save(new InvestmentFund(
                "ISB003", "İş Bankası Karma Fon", "Karma Fon", "İş Portföy",
                new BigDecimal("12.3456"), new BigDecimal("156000000"), today
        ));

        repository.save(new InvestmentFund(
                "YKB004", "Yapı Kredi Altın Fonu", "Emtia Fonu", "Yapı Kredi Portföy",
                new BigDecimal("45.6789"), new BigDecimal("67000000"), today
        ));

        repository.save(new InvestmentFund(
                "TEB005", "TEB Teknoloji Fonu", "Sektör Fonu", "TEB Portföy",
                new BigDecimal("23.4567"), new BigDecimal("78000000"), today
        ));

        // Performans verilerini güncelle
        List<InvestmentFund> funds = repository.findAll();
        for (InvestmentFund fund : funds) {
            fund.setManagementFee(BigDecimal.valueOf(1.5 + Math.random() * 1.5)); // 1.5-3% arası
            fund.setPerformanceFee(BigDecimal.valueOf(Math.random() * 20)); // 0-20% arası
            fund.setDailyReturn(BigDecimal.valueOf((Math.random() - 0.5) * 4)); // ±2% arası
            fund.setWeeklyReturn(BigDecimal.valueOf((Math.random() - 0.5) * 10)); // ±5% arası
            fund.setMonthlyReturn(BigDecimal.valueOf((Math.random() - 0.5) * 20)); // ±10% arası
            fund.setYearlyReturn(BigDecimal.valueOf((Math.random() - 0.3) * 40)); // -12% ile +28% arası
            
            String[] riskLevels = {"DÜŞÜK", "ORTA", "YÜKSEK"};
            fund.setRiskLevel(riskLevels[(int) (Math.random() * 3)]);
            
            repository.save(fund);
        }

        log.info("Seeded {} investment funds", funds.size());
    }
}