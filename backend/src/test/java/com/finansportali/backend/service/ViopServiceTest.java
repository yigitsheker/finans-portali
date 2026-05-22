package com.finansportali.backend.service;

import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.repository.ViopContractRepository;
import com.finansportali.backend.service.client.viop.IsYatirimViopFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViopServiceTest {

    @Mock private IsYatirimViopFetcher fetcher;
    @Mock private ViopContractRepository repo;
    @InjectMocks private ViopService service;

    private static ViopContract contract(String symbol, String name, BigDecimal lastPrice) {
        ViopContract c = new ViopContract();
        c.setSymbol(symbol);
        c.setName(name);
        c.setUnderlying("AKBNK");
        c.setMaturityMonth(5);
        c.setMaturityYear(2026);
        c.setCategory(ViopContract.Category.STOCK);
        c.setLastPrice(lastPrice);
        c.setChangePct(BigDecimal.ZERO);
        c.setChangeAbs(BigDecimal.ZERO);
        c.setVolumeTl(BigDecimal.ZERO);
        c.setVolumeLots(0L);
        return c;
    }

    @Test
    void findAll_returns_repo_results() {
        List<ViopContract> rows = List.of(contract("F_AKBNK0526", "AKBNK", new BigDecimal("50")));
        when(repo.findAllByOrderByCategoryAscUnderlyingAscMaturityYearAscMaturityMonthAsc())
                .thenReturn(rows);
        assertThat(service.findAll()).isEqualTo(rows);
    }

    @Test
    void findByCategory_delegates() {
        when(repo.findByCategoryOrderByMaturityYearAscMaturityMonthAscUnderlyingAsc(ViopContract.Category.STOCK))
                .thenReturn(List.of());
        assertThat(service.findByCategory(ViopContract.Category.STOCK)).isEmpty();
    }

    @Test
    void refresh_leaves_db_untouched_when_fetcher_returns_empty() {
        when(fetcher.fetchAll()).thenReturn(List.of());
        service.refresh();
        verify(repo, never()).save(any());
    }

    @Test
    void refresh_inserts_new_contracts() {
        ViopContract fresh = contract("F_AKBNK0526", "AKBNK Mayıs", new BigDecimal("50"));
        when(fetcher.fetchAll()).thenReturn(List.of(fresh));
        when(repo.findAll()).thenReturn(List.of());   // no existing → insert

        service.refresh();

        verify(repo, times(1)).save(any(ViopContract.class));
        assertThat(fresh.getUpdatedAt()).isNotNull();
    }

    @Test
    void refresh_updates_existing_contract_in_place() {
        ViopContract existing = contract("F_AKBNK0526", "Old Name", new BigDecimal("45"));
        ViopContract fresh = contract("F_AKBNK0526", "New Name", new BigDecimal("55"));

        when(fetcher.fetchAll()).thenReturn(List.of(fresh));
        when(repo.findAll()).thenReturn(List.of(existing));

        service.refresh();

        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getLastPrice()).isEqualByComparingTo("55");
        assertThat(existing.getUpdatedAt()).isNotNull();
        verify(repo).save(existing);
    }
}
