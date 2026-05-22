package com.finansportali.backend.service;

import com.finansportali.backend.entity.InvestmentFund;
import com.finansportali.backend.repository.InvestmentFundRepository;
import com.finansportali.backend.service.client.fund.TefasFundFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentFundServiceTest {

    @Mock private InvestmentFundRepository repository;
    @Mock private TefasFundFetcher tefasFundFetcher;
    @InjectMocks private InvestmentFundService service;

    private static InvestmentFund fund(String code, String name) {
        InvestmentFund f = new InvestmentFund();
        f.setFundCode(code);
        f.setFundName(name);
        f.setFundType("Hisse");
        f.setManagementCompany("ABC Portföy");
        f.setUnitPrice(new BigDecimal("1.5"));
        f.setTotalValue(new BigDecimal("1000000"));
        f.setPriceDate(LocalDate.now());
        return f;
    }

    @Test
    void getAllFunds_delegates() {
        when(repository.findAllOrderByTotalValueDesc()).thenReturn(List.of());
        assertThat(service.getAllFunds()).isEmpty();
    }

    @Test
    void getFundsByType_delegates() {
        when(repository.findByFundTypeOrderByTotalValueDesc("Hisse")).thenReturn(List.of());
        assertThat(service.getFundsByType("Hisse")).isEmpty();
    }

    @Test
    void getFundTypes_delegates() {
        when(repository.findDistinctFundTypes()).thenReturn(List.of("Hisse", "Borçlanma"));
        assertThat(service.getFundTypes()).containsExactly("Hisse", "Borçlanma");
    }

    @Test
    void getManagementCompanies_delegates() {
        when(repository.findDistinctManagementCompanies()).thenReturn(List.of("ABC"));
        assertThat(service.getManagementCompanies()).containsExactly("ABC");
    }

    @Test
    void getFundsByCompany_delegates() {
        when(repository.findByManagementCompanyOrderByTotalValueDesc("ABC"))
                .thenReturn(List.of());
        assertThat(service.getFundsByCompany("ABC")).isEmpty();
    }

    @Test
    void getFundByCode_delegates() {
        when(repository.findByFundCode("AFA")).thenReturn(Optional.of(fund("AFA", "X")));
        assertThat(service.getFundByCode("AFA")).isPresent();
    }

    @Test
    void getTopPerformers_delegates() {
        when(repository.findTopPerformers()).thenReturn(List.of());
        assertThat(service.getTopPerformers()).isEmpty();
    }

    @Test
    void searchFunds_delegates() {
        when(repository.searchFunds("xyz")).thenReturn(List.of());
        assertThat(service.searchFunds("xyz")).isEmpty();
    }

    @Test
    void updateFundPrices_skips_when_fetcher_returns_empty() {
        when(tefasFundFetcher.fetchAllFunds()).thenReturn(List.of());
        service.updateFundPrices();
        verify(repository, never()).save(any());
    }

    @Test
    void updateFundPrices_updates_existing_and_inserts_new() {
        InvestmentFund existing = fund("AFA", "Existing");
        existing.setUnitPrice(new BigDecimal("1.0"));

        InvestmentFund fresh1 = fund("AFA", "Updated Name");
        fresh1.setUnitPrice(new BigDecimal("2.0"));
        InvestmentFund fresh2 = fund("BFB", "New Fund");

        when(tefasFundFetcher.fetchAllFunds()).thenReturn(List.of(fresh1, fresh2));
        when(repository.findByFundCode("AFA")).thenReturn(Optional.of(existing));
        when(repository.findByFundCode("BFB")).thenReturn(Optional.empty());

        service.updateFundPrices();

        // Existing updated in-place
        assertThat(existing.getUnitPrice()).isEqualByComparingTo("2.0");
        assertThat(existing.getFundName()).isEqualTo("Updated Name");
        // Both saved
        verify(repository).save(existing);
        verify(repository).save(fresh2);
    }

    @Test
    void updateFundPrices_swallows_fetcher_exception() {
        when(tefasFundFetcher.fetchAllFunds()).thenThrow(new RuntimeException("network"));
        service.updateFundPrices();   // no throw
    }

    @Test
    void wipeAll_returns_count() {
        when(repository.count()).thenReturn(42L);
        assertThat(service.wipeAll()).isEqualTo(42);
        verify(repository).deleteAllInBatch();
    }

    @Test
    void seedIfEmpty_skips_when_db_not_empty() {
        when(repository.count()).thenReturn(5L);
        service.seedIfEmpty();
        verify(tefasFundFetcher, never()).fetchAllFunds();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void seedIfEmpty_skips_save_when_fetcher_returns_empty() {
        when(repository.count()).thenReturn(0L);
        when(tefasFundFetcher.fetchAllFunds()).thenReturn(List.of());
        service.seedIfEmpty();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void seedIfEmpty_persists_fetched_funds() {
        when(repository.count()).thenReturn(0L);
        when(tefasFundFetcher.fetchAllFunds()).thenReturn(List.of(fund("AFA", "X")));
        service.seedIfEmpty();
        verify(repository).saveAll(any());
    }

    @Test
    void seedIfEmpty_swallows_fetcher_exception() {
        when(repository.count()).thenReturn(0L);
        when(tefasFundFetcher.fetchAllFunds()).thenThrow(new RuntimeException("network"));
        service.seedIfEmpty();    // no throw
    }
}
