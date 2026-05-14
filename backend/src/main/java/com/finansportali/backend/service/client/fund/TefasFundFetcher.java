package com.finansportali.backend.service.client.fund;

import com.finansportali.backend.entity.InvestmentFund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TEFAS (Türkiye Elektronik Fon Alım Satım Platformu) demo data provider.
 * 
 * NOT: Bu servis gerçekçi demo veriler üretir. Gerçek TEFAS entegrasyonu için
 * Excel export veya Selenium WebDriver kullanılabilir.
 */
@Service
public class TefasFundFetcher {

    private static final Logger log = LoggerFactory.getLogger(TefasFundFetcher.class);
    
    private final Random random = new Random();
    
    // Configuration
    @Value("${app.funds.max-funds-to-fetch:100}")
    private int maxFundsToFetch;

    /**
     * Demo yatırım fonları listesi oluşturur
     */
    public List<InvestmentFund> fetchAllFunds() {
        log.info("Generating demo investment fund data (max: {} funds)...", maxFundsToFetch);
        
        List<InvestmentFund> funds = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        try {
            // Gerçek Türk yatırım fonları (demo verilerle)
            funds.addAll(generateRealFunds(today));
            
            log.info("Successfully generated {} demo investment funds", funds.size());
            
        } catch (Exception e) {
            log.error("Error generating demo funds: {}", e.getMessage(), e);
        }
        
        return funds;
    }
    
    /**
     * Gerçek fon isimlerine dayalı demo fonlar oluşturur
     */
    private List<InvestmentFund> generateRealFunds(LocalDate priceDate) {
        List<InvestmentFund> funds = new ArrayList<>();
        
        // Para Piyasası Fonları
        funds.add(createFund("HLL", "Ziraat Portföy Halkbank Para Piyasası (TL) Fonu", 
                "Para Piyasası Fonu", "Ziraat Portföy Yönetimi A.Ş.", "DÜŞÜK", 
                bd(1.523456), bd(125000000), bd(0.05), bd(1.2), bd(14.5), priceDate));
        
        funds.add(createFund("HSL", "HSBC Portföy Para Piyasası (TL) Fonu", 
                "Para Piyasası Fonu", "HSBC Portföy Yönetimi A.Ş.", "DÜŞÜK", 
                bd(2.145678), bd(98000000), bd(0.04), bd(1.1), bd(13.8), priceDate));
        
        funds.add(createFund("TP2", "Tera Portföy Para Piyasası (TL) Fonu", 
                "Para Piyasası Fonu", "Tera Yatırım Menkul Değerler A.Ş.", "DÜŞÜK", 
                bd(1.876543), bd(87000000), bd(0.06), bd(1.3), bd(15.2), priceDate));
        
        funds.add(createFund("YAP", "Yapı Kredi Portföy Para Piyasası (TL) Fonu", 
                "Para Piyasası Fonu", "Yapı Kredi Portföy Yönetimi A.Ş.", "DÜŞÜK", 
                bd(3.234567), bd(156000000), bd(0.05), bd(1.2), bd(14.1), priceDate));
        
        funds.add(createFund("GAR", "Garanti Portföy Para Piyasası (TL) Fonu", 
                "Para Piyasası Fonu", "Garanti Portföy Yönetimi A.Ş.", "DÜŞÜK", 
                bd(2.987654), bd(142000000), bd(0.04), bd(1.0), bd(13.5), priceDate));
        
        // Borçlanma Araçları Fonları
        funds.add(createFund("TBD", "Tacirler Portföy Borçlanma Araçları (TL) Fonu", 
                "Borçlanma Araçları Fonu", "Tacirler Portföy Yönetimi A.Ş.", "DÜŞÜK", 
                bd(1.654321), bd(76000000), bd(0.08), bd(2.1), bd(18.5), priceDate));
        
        funds.add(createFund("IHT", "İş Portföy Tahvil (Döviz) Fonu", 
                "Borçlanma Araçları Fonu", "İş Portföy Yönetimi A.Ş.", "DÜŞÜK", 
                bd(0.234567), bd(45000000), bd(0.12), bd(3.2), bd(8.7), priceDate));
        
        funds.add(createFund("AKT", "Ak Portföy Tahvil (TL) Fonu", 
                "Borçlanma Araçları Fonu", "Ak Portföy Yönetimi A.Ş.", "DÜŞÜK", 
                bd(2.456789), bd(92000000), bd(0.09), bd(2.3), bd(19.2), priceDate));
        
        // Hisse Senedi Fonları
        funds.add(createFund("AHE", "Ak Portföy Hisse Senedi Fonu", 
                "Hisse Senedi Fonu", "Ak Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(0.876543), bd(134000000), bd(1.25), bd(8.5), bd(45.3), priceDate));
        
        funds.add(createFund("IHS", "İş Portföy Hisse Senedi Fonu", 
                "Hisse Senedi Fonu", "İş Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(1.234567), bd(178000000), bd(1.45), bd(9.2), bd(52.1), priceDate));
        
        funds.add(createFund("YHS", "Yapı Kredi Portföy Hisse Senedi Fonu", 
                "Hisse Senedi Fonu", "Yapı Kredi Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(2.345678), bd(198000000), bd(1.32), bd(8.8), bd(48.7), priceDate));
        
        funds.add(createFund("GHS", "Garanti Portföy Hisse Senedi Fonu", 
                "Hisse Senedi Fonu", "Garanti Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(1.987654), bd(165000000), bd(1.18), bd(7.9), bd(43.2), priceDate));
        
        funds.add(createFund("THS", "Tera Portföy Hisse Senedi Fonu", 
                "Hisse Senedi Fonu", "Tera Yatırım Menkul Değerler A.Ş.", "YÜKSEK", 
                bd(0.765432), bd(89000000), bd(1.52), bd(9.8), bd(55.6), priceDate));
        
        // Karma Fonlar
        funds.add(createFund("AKK", "Ak Portföy Karma Fonu", 
                "Karma Fon", "Ak Portföy Yönetimi A.Ş.", "ORTA", 
                bd(1.456789), bd(112000000), bd(0.65), bd(5.2), bd(28.5), priceDate));
        
        funds.add(createFund("IKF", "İş Portföy Karma Fonu", 
                "Karma Fon", "İş Portföy Yönetimi A.Ş.", "ORTA", 
                bd(2.123456), bd(145000000), bd(0.72), bd(5.8), bd(31.2), priceDate));
        
        funds.add(createFund("YKF", "Yapı Kredi Portföy Karma Fonu", 
                "Karma Fon", "Yapı Kredi Portföy Yönetimi A.Ş.", "ORTA", 
                bd(1.789012), bd(128000000), bd(0.58), bd(4.9), bd(26.8), priceDate));
        
        funds.add(createFund("GKF", "Garanti Portföy Karma Fonu", 
                "Karma Fon", "Garanti Portföy Yönetimi A.Ş.", "ORTA", 
                bd(1.654321), bd(136000000), bd(0.68), bd(5.5), bd(29.7), priceDate));
        
        // Altın Fonları
        funds.add(createFund("AAL", "Ak Portföy Altın Fonu", 
                "Emtia Fonu", "Ak Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(0.123456), bd(67000000), bd(0.85), bd(6.2), bd(35.4), priceDate));
        
        funds.add(createFund("IAL", "İş Portföy Altın Fonu", 
                "Emtia Fonu", "İş Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(0.234567), bd(78000000), bd(0.92), bd(6.8), bd(38.1), priceDate));
        
        funds.add(createFund("YAL", "Yapı Kredi Portföy Altın Fonu", 
                "Emtia Fonu", "Yapı Kredi Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(0.345678), bd(85000000), bd(0.78), bd(5.9), bd(33.7), priceDate));
        
        // Sektör Fonları
        funds.add(createFund("ATK", "Ak Portföy Teknoloji Sektör Fonu", 
                "Sektör Fonu", "Ak Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(1.234567), bd(95000000), bd(1.85), bd(11.2), bd(62.5), priceDate));
        
        funds.add(createFund("IEN", "İş Portföy Enerji Sektör Fonu", 
                "Sektör Fonu", "İş Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(0.987654), bd(72000000), bd(1.42), bd(8.9), bd(48.3), priceDate));
        
        funds.add(createFund("YBN", "Yapı Kredi Portföy Banka Sektör Fonu", 
                "Sektör Fonu", "Yapı Kredi Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(1.456789), bd(108000000), bd(1.68), bd(10.1), bd(55.8), priceDate));
        
        // Fon Sepeti Fonları
        funds.add(createFund("AFS", "Ak Portföy Fon Sepeti Fonu", 
                "Fon Sepeti Fonu", "Ak Portföy Yönetimi A.Ş.", "ORTA", 
                bd(1.345678), bd(58000000), bd(0.48), bd(4.2), bd(22.5), priceDate));
        
        funds.add(createFund("IFS", "İş Portföy Fon Sepeti Fonu", 
                "Fon Sepeti Fonu", "İş Portföy Yönetimi A.Ş.", "ORTA", 
                bd(1.567890), bd(64000000), bd(0.52), bd(4.5), bd(24.1), priceDate));
        
        // Katılım Fonları
        funds.add(createFund("TLV", "Tera Portföy Para Piyasası Katılım (TL) Fonu", 
                "Para Piyasası Fonu", "Tera Yatırım Menkul Değerler A.Ş.", "DÜŞÜK", 
                bd(1.678901), bd(52000000), bd(0.05), bd(1.1), bd(13.2), priceDate));
        
        funds.add(createFund("ALB", "Albaraka Portföy Katılım Fonu", 
                "Karma Fon", "Albaraka Portföy Yönetimi A.Ş.", "ORTA", 
                bd(1.234567), bd(48000000), bd(0.62), bd(5.0), bd(27.3), priceDate));
        
        funds.add(createFund("ZKT", "Ziraat Portföy Katılım Fonu", 
                "Karma Fon", "Ziraat Portföy Yönetimi A.Ş.", "ORTA", 
                bd(1.456789), bd(71000000), bd(0.68), bd(5.3), bd(28.9), priceDate));
        
        // Daha fazla çeşitlilik için ek fonlar
        funds.add(createFund("FNB", "Finans Portföy Birinci Fon Sepeti Fonu", 
                "Fon Sepeti Fonu", "Finans Portföy Yönetimi A.Ş.", "ORTA", 
                bd(1.123456), bd(42000000), bd(0.45), bd(3.9), bd(21.2), priceDate));
        
        funds.add(createFund("QNB", "QNB Finans Portföy Hisse Senedi Fonu", 
                "Hisse Senedi Fonu", "QNB Finans Portföy Yönetimi A.Ş.", "YÜKSEK", 
                bd(0.876543), bd(96000000), bd(1.38), bd(8.7), bd(47.5), priceDate));
        
        // Limit kontrolü
        int limit = Math.min(funds.size(), maxFundsToFetch);
        return funds.subList(0, limit);
    }
    
    /**
     * Fon nesnesi oluşturur
     */
    private InvestmentFund createFund(String code, String name, String type, String company, 
                                     String riskLevel, BigDecimal unitPrice, BigDecimal totalValue,
                                     BigDecimal dailyReturn, BigDecimal monthlyReturn, 
                                     BigDecimal yearlyReturn, LocalDate priceDate) {
        InvestmentFund fund = new InvestmentFund();
        fund.setFundCode(code);
        fund.setFundName(name);
        fund.setFundType(type);
        fund.setManagementCompany(company);
        fund.setRiskLevel(riskLevel);
        fund.setUnitPrice(unitPrice);
        fund.setTotalValue(totalValue);
        fund.setDailyReturn(dailyReturn);
        fund.setMonthlyReturn(monthlyReturn);
        fund.setYearlyReturn(yearlyReturn);
        fund.setPriceDate(priceDate);
        
        // Haftalık getiri hesapla (yaklaşık)
        BigDecimal weeklyReturn = monthlyReturn.divide(bd(4), 2, RoundingMode.HALF_UP);
        fund.setWeeklyReturn(weeklyReturn);
        
        return fund;
    }
    
    /**
     * BigDecimal helper
     */
    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
    
    /**
     * Belirli bir fonun detaylı bilgilerini çeker
     * (Demo implementasyonda gerekli değil)
     */
    public InvestmentFund fetchFundDetails(String fundCode) {
        log.debug("fetchFundDetails called for {} (demo mode)", fundCode);
        return null;
    }
}
