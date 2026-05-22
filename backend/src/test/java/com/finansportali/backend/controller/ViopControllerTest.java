package com.finansportali.backend.controller;

import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.service.ViopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ViopController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class ViopControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private ViopService service;

    private ViopContract contract(String symbol, ViopContract.Category cat) {
        ViopContract c = new ViopContract();
        c.setSymbol(symbol);
        c.setCategory(cat);
        c.setLastPrice(BigDecimal.valueOf(100.0));
        return c;
    }

    @Test
    void list_without_category_returns_all() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                contract("F_XU0300626", ViopContract.Category.INDEX),
                contract("F_USDTRY0626", ViopContract.Category.FX_TRY)));

        mvc.perform(get("/api/v1/viop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(service).findAll();
    }

    @Test
    void blank_category_falls_through_to_findAll() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/v1/viop").param("category", "   "))
                .andExpect(status().isOk());

        verify(service).findAll();
    }

    @Test
    void category_param_routes_to_findByCategory() throws Exception {
        when(service.findByCategory(ViopContract.Category.INDEX))
                .thenReturn(List.of(contract("F_XU0300626", ViopContract.Category.INDEX)));

        mvc.perform(get("/api/v1/viop").param("category", "index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("F_XU0300626"));

        verify(service).findByCategory(ViopContract.Category.INDEX);
    }

    @Test
    void unknown_category_returns_empty_without_calling_service_strict() throws Exception {
        mvc.perform(get("/api/v1/viop").param("category", "MADE_UP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Unknown enum value short-circuits to an empty list; the
        // service-by-category call never happens. findAll() isn't either.
        verifyNoInteractions(service);
    }
}
