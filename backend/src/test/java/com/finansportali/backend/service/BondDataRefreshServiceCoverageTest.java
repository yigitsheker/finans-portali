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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Supplemental coverage tests for {@link BondDataRefreshService}.
 *
 * <p>Targets branches/paths the primary {@code BondDataRefreshServiceTest} does
 * not exercise: multiple dtos sharing one symbol (instrument upserted once,
 * several quotes saved), mixed prefetch hit+miss, the per-quote inner try/catch
 * being swallowed while siblings proceed, every {@code deactivateStale} branch
 * (non-empty stale list, empty/continue, exception swallow, multiple sources,
 * null-source fallback to active provider, all-null-symbol early return),
 * null-symbol dto skipping in applyQuotes, upsertInstrument null-field branches,
 * null quoteDate handling, and the disabled-configured-then-fallback /
 * fallback-disabled selectProvider arms.
 */
@ExtendWith(MockitoExtension.class)
class BondDataRefreshServiceCoverageTest {

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

    /** Fully-populated dto (every optional field non-null). */
    private static BondQuoteDto dto(String symbol, LocalDate date, String source) {
        BondQuoteDto d = new BondQuoteDto();
        d.setSymbol(symbol);
        d.setName(symbol + " name");
        d.setType(DebtInstrumentType.GOVERNMENT_BOND);
        d.setIssuer("Hazine");
        d.setCurrency("TRY");
        d.setIsin("ISIN-" + symbol);
        d.setMaturityDate(LocalDate.now().plusYears(2));
        d.setCouponRate(new BigDecimal("8.5"));
        d.setCouponType("Sabit");
        d.setQuoteDate(date);
        d.setPrice(new BigDecimal("100"));
        d.setYieldRate(new BigDecimal("12"));
        d.setCleanPrice(new BigDecimal("99"));
        d.setDirtyPrice(new BigDecimal("101"));
        d.setVolume(new BigDecimal("1000"));
        d.setChangeRate(new BigDecimal("0.5"));
        d.setSource(source);
        return d;
    }

    // ------------------------------------------------------------------
    // applyQuotes: grouping by symbol — one instrument, many quotes
    // ------------------------------------------------------------------

    @Test
    void multiple_dtos_same_symbol_upsert_instrument_once_and_save_each_quote() {
        LocalDate d1 = LocalDate.of(2026, 1, 10);
        LocalDate d2 = LocalDate.of(2026, 1, 11);
        LocalDate d3 = LocalDate.of(2026, 1, 12);
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(
                dto("TR2YT", d1, "TCMB"),
                dto("TR2YT", d2, "TCMB"),
                dto("TR2YT", d3, "TCMB")));
        when(instrumentRepo.findBySymbol("TR2YT")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any())).thenReturn(List.of());
        when(instrumentRepo.findActiveManagedBySourceExcluding(eq("TCMB"), anyCollection()))
                .thenReturn(List.of());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);

        // One bond -> updatedCount == 1, instrument saved once, a quote per day (3).
        assertThat(service.refreshBondData()).isEqualTo(1);
        verify(instrumentRepo, times(1)).save(any(DebtInstrument.class));
        verify(quoteRepo, times(3)).save(any(DebtInstrumentQuote.class));
        // Prefetch window spans the min..max of the group's dates.
        verify(quoteRepo).findByInstrumentAndQuoteDateBetween(any(), eq(d1), eq(d3));
    }

    // ------------------------------------------------------------------
    // upsertQuote: prefetch map hit AND miss within the same bond group
    // ------------------------------------------------------------------

    @Test
    void prefetch_hit_updates_existing_and_miss_creates_new_quote() {
        LocalDate hitDate = LocalDate.of(2026, 2, 1);
        LocalDate missDate = LocalDate.of(2026, 2, 2);
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(
                dto("TR5YT", hitDate, "TCMB"),
                dto("TR5YT", missDate, "TCMB")));

        DebtInstrument existing = new DebtInstrument("TR5YT", "old", DebtInstrumentType.GOVERNMENT_BOND);
        existing.setId(7L);
        when(instrumentRepo.findBySymbol("TR5YT")).thenReturn(Optional.of(existing));
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));

        // Prefetch contains only the hitDate quote -> hit reuses it, miss creates new.
        DebtInstrumentQuote existingQuote = new DebtInstrumentQuote();
        existingQuote.setQuoteDate(hitDate);
        existingQuote.setSource("TCMB");
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any()))
                .thenReturn(List.of(existingQuote));
        when(instrumentRepo.findActiveManagedBySourceExcluding(eq("TCMB"), anyCollection()))
                .thenReturn(List.of());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        assertThat(service.refreshBondData()).isEqualTo(1);

        ArgumentCaptor<DebtInstrumentQuote> captor = ArgumentCaptor.forClass(DebtInstrumentQuote.class);
        verify(quoteRepo, times(2)).save(captor.capture());
        List<DebtInstrumentQuote> saved = captor.getAllValues();
        // The pre-existing quote object is reused for the hit date.
        assertThat(saved).contains(existingQuote);
        // A brand-new quote object (not the prefetched one) was created for the miss date.
        DebtInstrumentQuote createdForMiss = saved.stream()
                .filter(q -> q != existingQuote)
                .findFirst().orElseThrow();
        assertThat(createdForMiss.getQuoteDate()).isEqualTo(missDate);
        assertThat(createdForMiss.getSource()).isEqualTo("TCMB");
        assertThat(createdForMiss.getInstrument()).isSameAs(existing);
    }

    // ------------------------------------------------------------------
    // upsertBond: per-quote inner try/catch — one bad quote swallowed,
    // sibling quotes in the SAME bond still saved, bond still counts.
    // ------------------------------------------------------------------

    @Test
    void per_quote_save_failure_is_swallowed_and_siblings_still_saved() {
        LocalDate bad = LocalDate.of(2026, 3, 1);
        LocalDate good = LocalDate.of(2026, 3, 2);
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(
                dto("TR1YT", bad, "TCMB"),
                dto("TR1YT", good, "TCMB")));
        when(instrumentRepo.findBySymbol("TR1YT")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any())).thenReturn(List.of());
        // First quote save throws (inner catch), second succeeds.
        when(quoteRepo.save(any(DebtInstrumentQuote.class)))
                .thenThrow(new RuntimeException("constraint violation"))
                .thenReturn(new DebtInstrumentQuote());
        when(instrumentRepo.findActiveManagedBySourceExcluding(eq("TCMB"), anyCollection()))
                .thenReturn(List.of());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        // upsertBond completes despite the bad quote -> applyBondSafely true -> count 1.
        assertThat(service.refreshBondData()).isEqualTo(1);
        verify(quoteRepo, times(2)).save(any(DebtInstrumentQuote.class));
    }

    // ------------------------------------------------------------------
    // applyQuotes: null-symbol dto is skipped
    // ------------------------------------------------------------------

    @Test
    void null_symbol_dto_is_skipped_only_named_one_processed() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        BondQuoteDto nullSymbol = dto("X", LocalDate.of(2026, 4, 1), "TCMB");
        nullSymbol.setSymbol(null);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(
                nullSymbol,
                dto("REAL", LocalDate.of(2026, 4, 1), "TCMB")));
        when(instrumentRepo.findBySymbol("REAL")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any())).thenReturn(List.of());
        when(instrumentRepo.findActiveManagedBySourceExcluding(eq("TCMB"), anyCollection()))
                .thenReturn(List.of());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        // Only "REAL" is grouped/processed; null-symbol entry never reaches findBySymbol.
        assertThat(service.refreshBondData()).isEqualTo(1);
        verify(instrumentRepo).findBySymbol("REAL");
        verify(instrumentRepo, times(1)).save(any(DebtInstrument.class));
    }

    // ------------------------------------------------------------------
    // upsertInstrument: optional fields null (false side of each guard),
    // null quoteDate (uses LocalDate.now()), new-instrument create path.
    // ------------------------------------------------------------------

    @Test
    void minimal_dto_with_null_optionals_and_null_quote_date_creates_instrument_and_quote() {
        BondQuoteDto minimal = new BondQuoteDto();
        minimal.setSymbol("MIN");
        minimal.setName("Minimal");
        minimal.setType(DebtInstrumentType.TREASURY_BILL);
        // isin/issuer/currency/maturity/coupon/quoteDate/price/.../source all null.
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(minimal));
        when(instrumentRepo.findBySymbol("MIN")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any())).thenReturn(List.of());
        // dto.getSource() == null -> source falls back to active provider name "DEMO".
        when(instrumentRepo.findActiveManagedBySourceExcluding(eq("DEMO"), anyCollection()))
                .thenReturn(List.of());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        assertThat(service.refreshBondData()).isEqualTo(1);

        ArgumentCaptor<DebtInstrument> instCaptor = ArgumentCaptor.forClass(DebtInstrument.class);
        verify(instrumentRepo).save(instCaptor.capture());
        DebtInstrument saved = instCaptor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("MIN");
        assertThat(saved.getName()).isEqualTo("Minimal");
        assertThat(saved.getType()).isEqualTo(DebtInstrumentType.TREASURY_BILL);
        // Optional fields stayed null because their guards were false.
        assertThat(saved.getIsin()).isNull();
        assertThat(saved.getIssuer()).isNull();
        assertThat(saved.getCurrency()).isNull();
        assertThat(saved.getMaturityDate()).isNull();
        assertThat(saved.getCouponRate()).isNull();
        assertThat(saved.getCouponType()).isNull();
        assertThat(saved.getActive()).isTrue();

        ArgumentCaptor<DebtInstrumentQuote> qCaptor = ArgumentCaptor.forClass(DebtInstrumentQuote.class);
        verify(quoteRepo).save(qCaptor.capture());
        DebtInstrumentQuote q = qCaptor.getValue();
        // null quoteDate -> defaulted to today; null source -> active provider name.
        assertThat(q.getQuoteDate()).isEqualTo(LocalDate.now());
        assertThat(q.getSource()).isEqualTo("DEMO");
        // Null measure fields left unset.
        assertThat(q.getPrice()).isNull();
        assertThat(q.getYieldRate()).isNull();
    }

    // ------------------------------------------------------------------
    // deactivateStale: non-empty stale list -> deactivate + saveAll
    // ------------------------------------------------------------------

    @Test
    void deactivate_stale_marks_inactive_and_saves_when_stale_found() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(
                dto("FRESH", LocalDate.of(2026, 5, 1), "TCMB")));
        when(instrumentRepo.findBySymbol("FRESH")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any())).thenReturn(List.of());

        DebtInstrument stale = new DebtInstrument("OLD", "Retired", DebtInstrumentType.GOVERNMENT_BOND);
        stale.setActive(true);
        when(instrumentRepo.findActiveManagedBySourceExcluding(eq("TCMB"), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(stale)));

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        assertThat(service.refreshBondData()).isEqualTo(1);

        assertThat(stale.getActive()).isFalse();
        verify(instrumentRepo).saveAll(List.of(stale));
        // The exclusion set must carry the fresh symbol.
        ArgumentCaptor<Collection<String>> symbolsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(instrumentRepo).findActiveManagedBySourceExcluding(eq("TCMB"), symbolsCaptor.capture());
        assertThat(symbolsCaptor.getValue()).containsExactly("FRESH");
    }

    // ------------------------------------------------------------------
    // deactivateStale: empty stale list -> continue (no saveAll)
    // ------------------------------------------------------------------

    @Test
    void deactivate_stale_no_op_when_nothing_stale() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(
                dto("FRESH", LocalDate.of(2026, 5, 1), "TCMB")));
        when(instrumentRepo.findBySymbol("FRESH")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any())).thenReturn(List.of());
        when(instrumentRepo.findActiveManagedBySourceExcluding(eq("TCMB"), anyCollection()))
                .thenReturn(List.of());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        assertThat(service.refreshBondData()).isEqualTo(1);
        verify(instrumentRepo, never()).saveAll(any());
    }

    // ------------------------------------------------------------------
    // deactivateStale: exception swallowed, refresh still succeeds
    // ------------------------------------------------------------------

    @Test
    void deactivate_stale_exception_is_swallowed_refresh_still_succeeds() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(
                dto("FRESH", LocalDate.of(2026, 5, 1), "TCMB")));
        when(instrumentRepo.findBySymbol("FRESH")).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any())).thenReturn(List.of());
        when(instrumentRepo.findActiveManagedBySourceExcluding(eq("TCMB"), anyCollection()))
                .thenThrow(new RuntimeException("query failed"));

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        // Exception inside deactivateStale's per-source try/catch must not bubble.
        assertThat(service.refreshBondData()).isEqualTo(1);
        verify(instrumentRepo, never()).saveAll(any());
    }

    // ------------------------------------------------------------------
    // deactivateStale: multiple distinct sources -> loop runs per source,
    // null source falls back to active provider name.
    // ------------------------------------------------------------------

    @Test
    void deactivate_stale_iterates_each_source_including_null_source_fallback() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        BondQuoteDto fromTcmb = dto("A", LocalDate.of(2026, 5, 1), "TCMB");
        BondQuoteDto nullSource = dto("B", LocalDate.of(2026, 5, 1), null); // -> "DEMO"
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(fromTcmb, nullSource));
        when(instrumentRepo.findBySymbol(any())).thenReturn(Optional.empty());
        when(instrumentRepo.save(any(DebtInstrument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepo.findByInstrumentAndQuoteDateBetween(any(), any(), any())).thenReturn(List.of());
        when(instrumentRepo.findActiveManagedBySourceExcluding(any(), anyCollection()))
                .thenReturn(List.of());

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        assertThat(service.refreshBondData()).isEqualTo(2);

        // Both the explicit source and the active-provider fallback are queried.
        verify(instrumentRepo).findActiveManagedBySourceExcluding(eq("TCMB"), anyCollection());
        verify(instrumentRepo).findActiveManagedBySourceExcluding(eq("DEMO"), anyCollection());
    }

    // ------------------------------------------------------------------
    // deactivateStale: all dtos have null symbol -> freshSymbols empty ->
    // early return; (applyQuotes also skips them -> updatedCount 0).
    // ------------------------------------------------------------------

    @Test
    void all_null_symbol_dtos_skip_apply_and_skip_deactivation() {
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(true);
        BondQuoteDto a = dto("A", LocalDate.of(2026, 5, 1), "TCMB");
        a.setSymbol(null);
        BondQuoteDto b = dto("B", LocalDate.of(2026, 5, 2), "TCMB");
        b.setSymbol(null);
        when(primaryProvider.fetchLatestBondQuotes()).thenReturn(List.of(a, b));

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", true);
        // Nothing applied; deactivateStale's freshSymbols-empty guard returns early.
        assertThat(service.refreshBondData()).isZero();
        verify(instrumentRepo, never()).save(any());
        verify(instrumentRepo, never()).findActiveManagedBySourceExcluding(any(), anyCollection());
        verify(instrumentRepo, never()).saveAll(any());
    }

    // ------------------------------------------------------------------
    // selectProvider: configured provider present but DISABLED, then the
    // fallback picks the first OTHER enabled provider.
    // ------------------------------------------------------------------

    @Test
    void configured_provider_disabled_falls_back_to_other_enabled() {
        // Configured "DEMO" exists but is disabled, so the .filter(isEnabled)
        // drops it -> fallback path runs and selects the enabled secondary.
        when(primaryProvider.getProviderName()).thenReturn("DEMO");
        when(primaryProvider.isEnabled()).thenReturn(false);
        when(secondaryProvider.getProviderName()).thenReturn("BIST");
        when(secondaryProvider.isEnabled()).thenReturn(true);
        when(secondaryProvider.fetchLatestBondQuotes()).thenReturn(List.of());

        BondDataRefreshService service = newService(
                List.of(primaryProvider, secondaryProvider), "DEMO", true);
        assertThat(service.refreshBondData()).isZero();
        verify(secondaryProvider).fetchLatestBondQuotes();
        verify(primaryProvider, never()).fetchLatestBondQuotes();
    }

    // ------------------------------------------------------------------
    // selectProvider: configured missing AND fallback disabled -> null ->
    // runRefresh returns 0 without fetching.
    // ------------------------------------------------------------------

    @Test
    void configured_missing_and_fallback_disabled_returns_zero() {
        when(primaryProvider.getProviderName()).thenReturn("OTHER");
        // isEnabled may or may not be invoked depending on stream short-circuit;
        // keep it lenient so strict stubbing doesn't fail either way.
        lenient().when(primaryProvider.isEnabled()).thenReturn(true);

        BondDataRefreshService service = newService(List.of(primaryProvider), "DEMO", false);
        assertThat(service.refreshBondData()).isZero();
        verify(primaryProvider, never()).fetchLatestBondQuotes();
        verify(instrumentRepo, never()).save(any());
    }
}
