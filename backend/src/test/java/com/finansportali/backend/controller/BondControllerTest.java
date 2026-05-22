package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.bond.BondListItemDto;
import com.finansportali.backend.dto.response.bond.BondSummaryDto;
import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.service.BondDataRefreshService;
import com.finansportali.backend.service.DebtInstrumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BondController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class BondControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private DebtInstrumentService debtInstrumentService;
    @MockitoBean private BondDataRefreshService refreshService;

    private BondListItemDto sampleBond() {
        BondListItemDto b = new BondListItemDto();
        b.setId(1L);
        b.setSymbol("TR2YT");
        b.setIsin("TRD171127T13");
        b.setName("Türkiye 2 Yıllık Devlet Tahvili");
        b.setType(DebtInstrumentType.GOVERNMENT_BOND);
        b.setCurrency("TRY");
        b.setMaturityDate(LocalDate.of(2027, 11, 17));
        b.setCouponRate(new BigDecimal("19.00"));
        b.setLatestPrice(new BigDecimal("120.61"));
        b.setLatestYieldRate(new BigDecimal("4.75"));
        return b;
    }

    @Test
    void list_returns_bonds_and_forwards_filters() throws Exception {
        when(debtInstrumentService.listBonds(any(), any(), any(), any(), any()))
                .thenReturn(List.of(sampleBond()));

        mvc.perform(get("/api/v1/bonds")
                        .param("type", "GOVERNMENT_BOND")
                        .param("currency", "TRY")
                        .param("search", "TR2YT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("TR2YT"))
                .andExpect(jsonPath("$[0].isin").value("TRD171127T13"))
                .andExpect(jsonPath("$[0].type").value("GOVERNMENT_BOND"))
                .andExpect(jsonPath("$[0].latestYieldRate").value(4.75));

        verify(debtInstrumentService).listBonds(
                eq(DebtInstrumentType.GOVERNMENT_BOND),
                eq("TRY"),
                eq(null),
                eq(null),
                eq("TR2YT"));
    }

    @Test
    void list_normalises_blank_search_to_null() throws Exception {
        when(debtInstrumentService.listBonds(any(), any(), any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/bonds").param("search", "   "))
                .andExpect(status().isOk());

        // The blank-string guard in the controller hands a null `search`
        // to the service so the JPA query uses LIKE '%null%' workarounds
        // correctly.
        verify(debtInstrumentService).listBonds(any(), any(), any(), any(), eq(null));
    }

    @Test
    void history_routes_dates_to_service() throws Exception {
        when(debtInstrumentService.getBondHistory(eq(7L), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/bonds/7/history")
                        .param("from", "2026-01-01")
                        .param("to",   "2026-05-01"))
                .andExpect(status().isOk());

        verify(debtInstrumentService).getBondHistory(7L,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-05-01"));
    }

    @Test
    void history_without_required_params_returns_400() throws Exception {
        mvc.perform(get("/api/v1/bonds/7/history"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summary_serialises_to_json() throws Exception {
        BondSummaryDto sum = new BondSummaryDto();
        sum.setTotalInstruments(15);
        sum.setAverageYield(new BigDecimal("12.34"));
        sum.setHighestYield(new BigDecimal("33.10"));
        when(debtInstrumentService.getSummary()).thenReturn(sum);

        mvc.perform(get("/api/v1/bonds/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInstruments").value(15))
                .andExpect(jsonPath("$.averageYield").value(12.34));
    }
}
