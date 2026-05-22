package com.finansportali.backend.controller;

import com.finansportali.backend.entity.InvestmentFund;
import com.finansportali.backend.service.InvestmentFundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestmentFundController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class InvestmentFundControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private InvestmentFundService service;

    private InvestmentFund fund(String code, String type, double price) {
        InvestmentFund f = new InvestmentFund();
        f.setFundCode(code);
        f.setFundName(code + " Fund");
        f.setFundType(type);
        f.setUnitPrice(BigDecimal.valueOf(price));
        return f;
    }

    @Test
    void getAll_returns_funds() throws Exception {
        when(service.getAllFunds()).thenReturn(List.of(fund("FUA", "HSF", 1.0)));

        mvc.perform(get("/api/v1/investment-funds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fundCode").value("FUA"));
    }

    @Test
    void types_returns_string_list() throws Exception {
        when(service.getFundTypes()).thenReturn(List.of("HSF", "DGF"));

        mvc.perform(get("/api/v1/investment-funds/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void companies_returns_string_list() throws Exception {
        when(service.getManagementCompanies()).thenReturn(List.of("Ak Portföy"));

        mvc.perform(get("/api/v1/investment-funds/companies"))
                .andExpect(status().isOk());
    }

    @Test
    void by_type_path_var_passes_through() throws Exception {
        when(service.getFundsByType("HSF")).thenReturn(List.of(fund("F1", "HSF", 2.0)));

        mvc.perform(get("/api/v1/investment-funds/type/HSF"))
                .andExpect(status().isOk());

        verify(service).getFundsByType("HSF");
    }

    @Test
    void by_company_path_var_passes_through() throws Exception {
        when(service.getFundsByCompany("Ak Portföy")).thenReturn(List.of());

        mvc.perform(get("/api/v1/investment-funds/company/Ak Portföy"))
                .andExpect(status().isOk());
    }

    @Test
    void by_code_path_var_passes_through() throws Exception {
        when(service.getFundByCode("FUA")).thenReturn(Optional.of(fund("FUA", "HSF", 5.0)));

        mvc.perform(get("/api/v1/investment-funds/FUA"))
                .andExpect(status().isOk());

        verify(service).getFundByCode("FUA");
    }

    @Test
    void top_performers_returns_list() throws Exception {
        when(service.getTopPerformers()).thenReturn(List.of(fund("F1", "HSF", 10)));

        mvc.perform(get("/api/v1/investment-funds/top-performers"))
                .andExpect(status().isOk());
    }

    @Test
    void search_requires_q_param() throws Exception {
        when(service.searchFunds("ak")).thenReturn(List.of());

        mvc.perform(get("/api/v1/investment-funds/search").param("q", "ak"))
                .andExpect(status().isOk());

        verify(service).searchFunds("ak");
    }

    @Test
    void search_without_q_returns_400() throws Exception {
        mvc.perform(get("/api/v1/investment-funds/search"))
                .andExpect(status().isBadRequest());
    }
}
