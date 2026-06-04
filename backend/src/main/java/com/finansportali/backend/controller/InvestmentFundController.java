package com.finansportali.backend.controller;

import com.finansportali.backend.entity.InvestmentFund;
import com.finansportali.backend.service.InvestmentFundService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoints for TEFAS investment funds: listing, filtering by type or
 * management company, lookup, search, top performers, and an admin refresh.
 */
@RestController
@RequestMapping("/api/v1/investment-funds")
public class InvestmentFundController {

    private final InvestmentFundService service;

    public InvestmentFundController(InvestmentFundService service) {
        this.service = service;
    }

    /** All known investment funds. */
    @GetMapping
    public List<InvestmentFund> getAllFunds() {
        return service.getAllFunds();
    }

    /** Distinct fund type categories available for filtering. */
    @GetMapping("/types")
    public List<String> getFundTypes() {
        return service.getFundTypes();
    }

    /** Distinct management company names available for filtering. */
    @GetMapping("/companies")
    public List<String> getManagementCompanies() {
        return service.getManagementCompanies();
    }

    /** Funds of a given type. */
    @GetMapping("/type/{fundType}")
    public List<InvestmentFund> getFundsByType(@PathVariable String fundType) {
        return service.getFundsByType(fundType);
    }

    /** Funds managed by a given company. */
    @GetMapping("/company/{company}")
    public List<InvestmentFund> getFundsByCompany(@PathVariable String company) {
        return service.getFundsByCompany(company);
    }

    /** Single fund by its TEFAS fund code, if present. */
    @GetMapping("/{fundCode}")
    public Optional<InvestmentFund> getFundByCode(@PathVariable String fundCode) {
        return service.getFundByCode(fundCode);
    }

    /** Best-returning funds for the home/dashboard widgets. */
    @GetMapping("/top-performers")
    public List<InvestmentFund> getTopPerformers() {
        return service.getTopPerformers();
    }

    /** Free-text search over fund code/name. */
    @GetMapping("/search")
    public List<InvestmentFund> searchFunds(@RequestParam String q) {
        return service.searchFunds(q);
    }

    /**
     * Admin-only refresh of fund prices from TEFAS. Returns a status map with
     * before/after counts; on failure reports {@code success=false} with the message.
     */
    @PostMapping("/admin/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> refreshFundData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            int beforeCount = service.getAllFunds().size();
            service.updateFundPrices();
            int afterCount = service.getAllFunds().size();
            
            response.put("success", true);
            response.put("message", "Fon verileri başarıyla güncellendi");
            response.put("fundsBefore", beforeCount);
            response.put("fundsAfter", afterCount);
            response.put("fundsAdded", afterCount - beforeCount);
            response.put("timestamp", java.time.Instant.now().toString());
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Güncelleme sırasında hata oluştu: " + e.getMessage());
        }
        
        return response;
    }
}