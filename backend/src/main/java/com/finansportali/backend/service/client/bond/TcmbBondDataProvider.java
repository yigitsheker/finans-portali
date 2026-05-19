package com.finansportali.backend.service.client.bond;

import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Bond data provider backed exclusively by TCMB EVDS3 "Devlet İç Borçlanma
 * Senetlerinin Gösterge Niteliğindeki Değerleri" — every quote returned here
 * is a real, daily-updated value with a real TRT/TRD ISIN.
 *
 * <p>An earlier iteration of this class also produced synthetic policy-rate-
 * plus-spread quotes for missing maturities and seeded sukuk/eurobond rows.
 * That fallback was deliberately removed: a finance site is better off
 * surfacing fewer real numbers than many fake ones.
 *
 * <p>The heavy lifting lives in {@link EvdsBondYieldFetcher}; this class is a
 * thin {@link BondDataProvider} adapter so the existing
 * {@code BondDataRefreshService} pipeline keeps working unchanged.
 */
@Service
@ConditionalOnProperty(name = "app.bonds.provider", havingValue = "TCMB", matchIfMissing = false)
public class TcmbBondDataProvider implements BondDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TcmbBondDataProvider.class);

    private final EvdsBondYieldFetcher evdsBondYieldFetcher;
    private final InvestingYieldCurveFetcher investingYieldCurveFetcher;

    public TcmbBondDataProvider(
            EvdsBondYieldFetcher evdsBondYieldFetcher,
            InvestingYieldCurveFetcher investingYieldCurveFetcher) {
        this.evdsBondYieldFetcher = evdsBondYieldFetcher;
        this.investingYieldCurveFetcher = investingYieldCurveFetcher;
    }

    @Override
    public String getProviderName() {
        return "TCMB";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<BondQuoteDto> fetchLatestBondQuotes() {
        // Two complementary real-data sources merged into a single response:
        //   1. EVDS3 — per-issue bonds with real price + coupon + computed YTM
        //      (TR2YT, TR3YT, TR4YT, …). Source label "TCMB_EVDS3".
        //   2. Investing.com TR yield curve — gösterge yields across the full
        //      3M…30Y tenor range. Source label "INVESTING_TR".
        // Either source returning empty (cookies expired, site down) just
        // drops out — the other set still populates the bonds page.
        List<BondQuoteDto> quotes = new ArrayList<>();
        quotes.addAll(evdsBondYieldFetcher.fetchAll());
        quotes.addAll(investingYieldCurveFetcher.fetchAll());
        log.info("[TCMB] Returned {} bond quote(s) (EVDS3 specific issues + Investing.com curve)",
                quotes.size());
        return quotes;
    }

    @Override
    public List<BondQuoteDto> fetchHistoricalYield(String symbol, LocalDate from, LocalDate to) {
        // Historical yield snapshots were derived from synthetic spreads and
        // produced misleading charts; intentionally removed.
        return Collections.emptyList();
    }

    @Override
    public Optional<BondQuoteDto> fetchBySymbol(String symbol) {
        return fetchLatestBondQuotes().stream()
                .filter(q -> symbol.equalsIgnoreCase(q.getSymbol()))
                .findFirst();
    }
}
