package com.finansportali.backend.service;

import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentQuote;
import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.repository.DebtInstrumentQuoteRepository;
import com.finansportali.backend.repository.DebtInstrumentRepository;
import com.finansportali.backend.service.client.bond.BondDataProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class BondDataRefreshServiceTest {

    @Mock private DebtInstrumentRepository instrumentRepo;
    @Mock private DebtInstrumentQuoteRepository quoteRepo;
    @Mock private BondDataProvider primaryProvider;
    @Mock private BondDataProvider secondaryProvider;

    private BondDataRefreshService newService(List<BondDataProvider> providers,
                                              String activeName, boolean fallback) {
        BondDataRefreshService s = new BondDataRefreshService(
                instrumentRepo, quoteRepo, providers, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(s, "activeProviderName", activeName);
        ReflectionTestUtils.setField(s, "fallbackEnabled", fallback);
        return s;
    }

    private static BondQuoteDto dto(String symbol, String name) {
        BondQuoteDto d = new BondQuoteDto();
        d.setSymbol(symbol);
        d.setName(name);
        d.setType(DebtInstrumentType.GOVERNMENT_BOND);
        d.setIssuer("Hazine");
        d.setCurrency("TRY");
        d.setIsin("ISIN-" + symbol);
        d.setMaturityDate(LocalDate.now().plusYears(2));
        d.setCouponRate(new BigDecimal("8.5"));
        d.setCouponType("Sabit");
        d.setQuoteDate(LocalDate.now());
        d.setPrice(new BigDecimal("100"));
        d.setYieldRate(new BigDecimal("12"));
        d.setCleanPrice(new BigDecimal("99"));
        d.setDirtyPrice(new BigDecimal("101"));
        d.setVolume(new BigDecimal("1000"));
        d.setChangeRate(new BigDecimal("0.5"));
        d.setSource("TCMB");
        return d;
    }

    @Test
    void refresh_returns_zero_when_no_providers() {
        BondDataRefreshService service = newService(List.of(), "DEMO", true);
        assertThat(service.refreshBondData()).isZero();
        verify(instrumentRepo, never()).save(any());
    }

    @Test
    void refresh_returns_zero_when_provider_disabled_and_no_fallback() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(false);
        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", false);
        assertThat(service.refreshBondData()).isZero();
    }

    @Test
    void refresh_uses_configured_provider_when_enabled() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(dto("TR2YT", "2Y")));
        when(instrumentRepo.findBySymbol("TR2YT")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateAndSource(any(), any(), any()))
                .thenReturn(Optional.empty());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        assertThat(service.refreshBondData()).isEqualTo(1);
        verify(instrumentRepo).save(any(DebtInstrument.class));
        verify(quoteRepo).save(any(DebtInstrumentQuote.class));
    }

    @Test
    void refresh_falls_back_to_first_enabled_when_configured_missing() {
        when(primaryProvider.getProviderName()).thenReturn("OTHER");
        when(secondaryProvider.getProviderName()).thenReturn("SECONDARY");
        when(secondaryProvider.isEnabled()).thenReturn(true);
        when(secondaryProvider.fetchLatestBondQuotes()).thenReturn(List.of());

        BondDataRefreshService service = newService(
                List.of(primaryProvider, secondaryProvider), "DEMO", true);
        service.refreshBondData();
        verify(secondaryProvider).fetchLatestBondQuotes();
    }

    @Test
    void refresh_updates_existing_instrument_and_quote() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(dto("TR2YT", "2Y Updated")));

        DebtInstrument existing = new DebtInstrument("TR2YT", "2Y Old", DebtInstrumentType.GOVERNMENT_BOND);
        existing.setId(1L);
        when(instrumentRepo.findBySymbol("TR2YT")).thenReturn(Optional.of(existing));
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));

        DebtInstrumentQuote existingQuote = new DebtInstrumentQuote();
        when(quoteRepo.findByInstrumentAndQuoteDateAndSource(any(), any(), any()))
                .thenReturn(Optional.of(existingQuote));

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        service.refreshBondData();

        assertThat(existing.getName()).isEqualTo("2Y Updated");
        verify(quoteRepo).save(existingQuote);
    }

    @Test
    void refresh_swallows_per_dto_exception_and_continues_with_next() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes())
                .thenReturn(List.of(dto("A", "A"), dto("B", "B")));

        when(instrumentRepo.findBySymbol("A")).thenThrow(new RuntimeException("db down"));
        when(instrumentRepo.findBySymbol("B")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateAndSource(any(), any(), any()))
                .thenReturn(Optional.empty());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        // The second DTO should still be processed.
        assertThat(service.refreshBondData()).isEqualTo(1);
    }

    @Test
    void triggerManualRefresh_returns_success_message() {
        BondDataRefreshService service = newService(List.of(), "DEMO", true);
        String msg = service.triggerManualRefresh();
        assertThat(msg).contains("Updated 0 instruments");
    }

    @Test
    void refresh_returns_zero_when_provider_fetch_throws() {
        // Provider blows up at the fetchLatestBondQuotes() boundary — covers
        // the outer RuntimeException catch in runRefresh() that bumps the
        // failure counter and returns 0 (different code path from the
        // per-DTO catch tested above).
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes())
                .thenThrow(new RuntimeException("upstream down"));

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        assertThat(service.refreshBondData()).isZero();
        verify(instrumentRepo, never()).save(any());
    }
}
