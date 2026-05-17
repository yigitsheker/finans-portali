package com.finansportali.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "investment_funds")
public class InvestmentFund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String fundCode;

    @Column(nullable = false, length = 200)
    private String fundName;

    @Column(nullable = false, length = 100)
    private String fundType; // Hisse Senedi Fonu, Borçlanma Araçları Fonu, Karma Fon, etc.

    @Column(nullable = false, length = 100)
    private String managementCompany;

    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal totalValue; // Toplam fon büyüklüğü

    @Column(precision = 12, scale = 4)
    private BigDecimal managementFee; // Yönetim ücreti (%)

    @Column(precision = 12, scale = 4)
    private BigDecimal performanceFee; // Performans ücreti (%)

    @Column(nullable = false)
    private LocalDate priceDate;

    @Column(precision = 12, scale = 4)
    private BigDecimal dailyReturn; // Günlük getiri (%)

    @Column(precision = 12, scale = 4)
    private BigDecimal weeklyReturn; // Haftalık getiri (%)

    @Column(precision = 12, scale = 4)
    private BigDecimal monthlyReturn; // 1 aylık getiri (%)

    @Column(name = "three_month_return", precision = 12, scale = 4)
    private BigDecimal threeMonthReturn; // 3 aylık getiri (%)

    @Column(name = "six_month_return", precision = 12, scale = 4)
    private BigDecimal sixMonthReturn; // 6 aylık getiri (%)

    @Column(precision = 12, scale = 4)
    private BigDecimal yearlyReturn; // 1 yıllık getiri (%)

    @Column(name = "three_year_return", precision = 12, scale = 4)
    private BigDecimal threeYearReturn; // 3 yıllık getiri (%)

    @Column(name = "five_year_return", precision = 12, scale = 4)
    private BigDecimal fiveYearReturn; // 5 yıllık getiri (%)

    @Column(length = 10)
    private String riskLevel; // DÜŞÜK, ORTA, YÜKSEK

    public InvestmentFund() {
    }

    public InvestmentFund(String fundCode, String fundName, String fundType, String managementCompany,
                         BigDecimal unitPrice, BigDecimal totalValue, LocalDate priceDate) {
        this.fundCode = fundCode;
        this.fundName = fundName;
        this.fundType = fundType;
        this.managementCompany = managementCompany;
        this.unitPrice = unitPrice;
        this.totalValue = totalValue;
        this.priceDate = priceDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getFundCode() { return fundCode; }
    public String getFundName() { return fundName; }
    public String getFundType() { return fundType; }
    public String getManagementCompany() { return managementCompany; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalValue() { return totalValue; }
    public BigDecimal getManagementFee() { return managementFee; }
    public BigDecimal getPerformanceFee() { return performanceFee; }
    public LocalDate getPriceDate() { return priceDate; }
    public BigDecimal getDailyReturn() { return dailyReturn; }
    public BigDecimal getWeeklyReturn() { return weeklyReturn; }
    public BigDecimal getMonthlyReturn() { return monthlyReturn; }
    public BigDecimal getThreeMonthReturn() { return threeMonthReturn; }
    public BigDecimal getSixMonthReturn() { return sixMonthReturn; }
    public BigDecimal getYearlyReturn() { return yearlyReturn; }
    public BigDecimal getThreeYearReturn() { return threeYearReturn; }
    public BigDecimal getFiveYearReturn() { return fiveYearReturn; }
    public String getRiskLevel() { return riskLevel; }

    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public void setFundType(String fundType) { this.fundType = fundType; }
    public void setManagementCompany(String managementCompany) { this.managementCompany = managementCompany; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }
    public void setManagementFee(BigDecimal managementFee) { this.managementFee = managementFee; }
    public void setPerformanceFee(BigDecimal performanceFee) { this.performanceFee = performanceFee; }
    public void setPriceDate(LocalDate priceDate) { this.priceDate = priceDate; }
    public void setDailyReturn(BigDecimal dailyReturn) { this.dailyReturn = dailyReturn; }
    public void setWeeklyReturn(BigDecimal weeklyReturn) { this.weeklyReturn = weeklyReturn; }
    public void setMonthlyReturn(BigDecimal monthlyReturn) { this.monthlyReturn = monthlyReturn; }
    public void setThreeMonthReturn(BigDecimal threeMonthReturn) { this.threeMonthReturn = threeMonthReturn; }
    public void setSixMonthReturn(BigDecimal sixMonthReturn) { this.sixMonthReturn = sixMonthReturn; }
    public void setYearlyReturn(BigDecimal yearlyReturn) { this.yearlyReturn = yearlyReturn; }
    public void setThreeYearReturn(BigDecimal threeYearReturn) { this.threeYearReturn = threeYearReturn; }
    public void setFiveYearReturn(BigDecimal fiveYearReturn) { this.fiveYearReturn = fiveYearReturn; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
}