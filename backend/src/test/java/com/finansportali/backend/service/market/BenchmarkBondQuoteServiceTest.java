package com.finansportali.backend.service.market;

import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentQuote;
import com.finansportali.backend.entity.DebtInstrumentType;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketDataProvider;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.DebtInstrumentQuoteRepository;
import com.finansportali.backend.repository.DebtInstrumentRepository;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkBondQuoteServiceTest {

    @Mock private DebtInstrumentRepository debtInstrumentRepo;
    @Mock private DebtInstrumentQuoteRepository debtQuoteRepo;
    @Mock private MarketInstrumentRepository marketInstrumentRepo;
    @Mock private MarketQuoteRepository marketQuoteRepo;
    @Mock private MarketCandleRepository marketCandleRepo;
    @Mock private CacheManager cacheManager;
    @InjectMocks private BenchmarkBondQuoteService service;

    private static DebtInstrument bond(String symbol, LocalDate maturity) {
        DebtInstrument b = new DebtInstrument(symbol, symbol, DebtInstrumentType.GOVERNMENT_BOND);
        b.setMaturityDate(maturity);
        b.setActive(true);
        return b;
    }

    private static DebtInstrumentQuote quote(DebtInstrument b, String yield) {
        DebtInstrumentQuote q = new DebtInstrumentQuote(b, LocalDate.now(), "TCMB");
        q.setYieldRate(new BigDecimal(yield));
        q.setQuoteDate(LocalDate.now());
        return q;
    }

    private static MarketInstrument mi(String symbol) {
        return new MarketInstrument(symbol, symbol, InstrumentType.BOND,
                MarketDataProvider.TCMB, symbol, false);
    }

    @Test
    void refreshBenchmarks_skips_when_no_active_bonds() {
        when(debtInstrumentRepo.findByActiveTrueAndType(DebtInstrumentType.GOVERNMENT_BOND))
                .thenReturn(List.of());

        service.refreshBenchmarks();

        verify(marketQuoteRepo, never()).save(any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    void refreshBenchmarks_publishes_quote_and_evicts_for_each_tenor() {
        DebtInstrument twoYear = bond("TRB2", LocalDate.now().plusYears(2));
        DebtInstrument noMaturity = bond("TRBX", null);   // exercises the null-maturity skip
        when(debtInstrumentRepo.findByActiveTrueAndType(DebtInstrumentType.GOVERNMENT_BOND))
                .thenReturn(List.of(twoYear, noMaturity));

        when(marketInstrumentRepo.findBySymbol(anyString()))
                .thenAnswer(inv -> Optional.of(mi(inv.getArgument(0))));
        when(debtQuoteRepo.findTop2ByInstrument(any()))
                .thenReturn(List.of(quote(twoYear, "42.5"), quote(twoYear, "41.0")));
        when(debtQuoteRepo.findHistoricalQuotes(any(), any(), any()))
                .thenReturn(List.of(quote(twoYear, "40.0")));
        when(marketCandleRepo.findByInstrumentAndDay(any(), any())).thenReturn(Optional.empty());
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(cache);

        service.refreshBenchmarks();

        // One published quote per tenor (TR2Y / TR5Y / TR10Y).
        verify(marketQuoteRepo, times(3)).save(any());
        verify(marketCandleRepo, org.mockito.Mockito.atLeastOnce()).save(any());
        // Caches evicted once (3 cache regions) after at least one tenor changed.
        verify(cacheManager, times(3)).getCache(anyString());
    }

    @Test
    void refreshBenchmarks_skips_tenor_when_instrument_not_seeded() {
        when(debtInstrumentRepo.findByActiveTrueAndType(DebtInstrumentType.GOVERNMENT_BOND))
                .thenReturn(List.of(bond("TRB2", LocalDate.now().plusYears(2))));
        when(marketInstrumentRepo.findBySymbol(anyString())).thenReturn(Optional.empty());

        service.refreshBenchmarks();

        verify(marketQuoteRepo, never()).save(any());
        verifyNoInteractions(cacheManager);   // nothing changed → no eviction
    }

    @Test
    void refreshBenchmarks_skips_tenor_when_no_yield_quotes() {
        when(debtInstrumentRepo.findByActiveTrueAndType(DebtInstrumentType.GOVERNMENT_BOND))
                .thenReturn(List.of(bond("TRB2", LocalDate.now().plusYears(2))));
        when(marketInstrumentRepo.findBySymbol(anyString()))
                .thenAnswer(inv -> Optional.of(mi(inv.getArgument(0))));
        when(debtQuoteRepo.findTop2ByInstrument(any())).thenReturn(List.of());

        service.refreshBenchmarks();

        verify(marketQuoteRepo, never()).save(any());
    }

    @Test
    void scheduledRefresh_delegates_to_refreshBenchmarks() {
        when(debtInstrumentRepo.findByActiveTrueAndType(DebtInstrumentType.GOVERNMENT_BOND))
                .thenReturn(List.of());
        service.scheduledRefresh();
        verify(debtInstrumentRepo).findByActiveTrueAndType(eq(DebtInstrumentType.GOVERNMENT_BOND));
    }

    @Test
    void initialRefresh_delegates_to_refreshBenchmarks() {
        when(debtInstrumentRepo.findByActiveTrueAndType(DebtInstrumentType.GOVERNMENT_BOND))
                .thenReturn(List.of());
        service.initialRefresh();
        verify(debtInstrumentRepo).findByActiveTrueAndType(eq(DebtInstrumentType.GOVERNMENT_BOND));
    }
}
