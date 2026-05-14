package com.finansportali.backend.repository;

import com.finansportali.backend.entity.InvestmentFund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvestmentFundRepository extends JpaRepository<InvestmentFund, Long> {

    Optional<InvestmentFund> findByFundCode(String fundCode);

    List<InvestmentFund> findByFundTypeOrderByTotalValueDesc(String fundType);

    List<InvestmentFund> findByManagementCompanyOrderByTotalValueDesc(String managementCompany);

    @Query("SELECT DISTINCT f.fundType FROM InvestmentFund f ORDER BY f.fundType")
    List<String> findDistinctFundTypes();

    @Query("SELECT DISTINCT f.managementCompany FROM InvestmentFund f ORDER BY f.managementCompany")
    List<String> findDistinctManagementCompanies();

    @Query("SELECT f FROM InvestmentFund f ORDER BY f.totalValue DESC")
    List<InvestmentFund> findAllOrderByTotalValueDesc();

    @Query("SELECT f FROM InvestmentFund f WHERE f.yearlyReturn IS NOT NULL ORDER BY f.yearlyReturn DESC")
    List<InvestmentFund> findTopPerformers();

    @Query("SELECT f FROM InvestmentFund f WHERE f.fundName LIKE %:search% OR f.fundCode LIKE %:search% ORDER BY f.totalValue DESC")
    List<InvestmentFund> searchFunds(@Param("search") String search);
}