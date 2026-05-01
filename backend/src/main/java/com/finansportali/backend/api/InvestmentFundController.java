package com.finansportali.backend.api;

import com.finansportali.backend.domain.InvestmentFund;
import com.finansportali.backend.service.InvestmentFundService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/investment-funds")
public class InvestmentFundController {

    private final InvestmentFundService service;

    public InvestmentFundController(InvestmentFundService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvestmentFund> getAllFunds() {
        return service.getAllFunds();
    }

    @GetMapping("/types")
    public List<String> getFundTypes() {
        return service.getFundTypes();
    }

    @GetMapping("/companies")
    public List<String> getManagementCompanies() {
        return service.getManagementCompanies();
    }

    @GetMapping("/type/{fundType}")
    public List<InvestmentFund> getFundsByType(@PathVariable String fundType) {
        return service.getFundsByType(fundType);
    }

    @GetMapping("/company/{company}")
    public List<InvestmentFund> getFundsByCompany(@PathVariable String company) {
        return service.getFundsByCompany(company);
    }

    @GetMapping("/{fundCode}")
    public Optional<InvestmentFund> getFundByCode(@PathVariable String fundCode) {
        return service.getFundByCode(fundCode);
    }

    @GetMapping("/top-performers")
    public List<InvestmentFund> getTopPerformers() {
        return service.getTopPerformers();
    }

    @GetMapping("/search")
    public List<InvestmentFund> searchFunds(@RequestParam String q) {
        return service.searchFunds(q);
    }
}