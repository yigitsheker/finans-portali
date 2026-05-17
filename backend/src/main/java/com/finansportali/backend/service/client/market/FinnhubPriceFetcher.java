package com.finansportali.backend.service.client.market;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches real-time quotes from Finnhub (https://finnhub.io).
 * Free tier: 60 req/min. We only call once per day per symbol.
 *
 * Finnhub symbol mapping:
 *  - Stocks  : "AAPL", "TSLA", "MSFT" ...
 *  - Crypto  : "BINANCE:BTCUSDT", "BINANCE:ETHUSDT" ...
 *  - Forex   : "OANDA:USD_TRY", "OANDA:EUR_TRY" ...
 */
@Component
public class FinnhubPriceFetcher {

    private static final Logger log = LoggerFactory.getLogger(FinnhubPriceFetcher.class);
    private static final String BASE = "https://finnhub.io/api/v1";

    private final WebClient client;
    private final String apiKey;

    public FinnhubPriceFetcher(@Value("${finnhub.api-key:disabled}") String apiKey) {
        this.apiKey = apiKey;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10))
                        .addHandlerLast(new WriteTimeoutHandler(10)));
        this.client = WebClient.builder()
                .baseUrl(BASE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Returns the current price for the given Finnhub symbol, or empty if unavailable.
     */
    @SuppressWarnings("unchecked")
    public Optional<BigDecimal> fetchPrice(String finnhubSymbol) {
        try {
            Map<String, Object> resp = client.get()
                    .uri(u -> u.path("/quote")
                            .queryParam("symbol", finnhubSymbol)
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return Optional.empty();

            // Finnhub quote response: { c: current, d: change, dp: changePct, h, l, o, pc }
            Object c = resp.get("c");
            if (c == null) return Optional.empty();

            double price = ((Number) c).doubleValue();
            if (price == 0.0) return Optional.empty(); // symbol not found / market closed

            return Optional.of(BigDecimal.valueOf(price));
        } catch (Exception e) {
            log.warn("Finnhub fetch failed for {}: {}", finnhubSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    /** Returns change (d) and changePct (dp) alongside price. */
    @SuppressWarnings("unchecked")
    public Optional<FinnhubQuote> fetchQuote(String finnhubSymbol) {
        try {
            Map<String, Object> resp = client.get()
                    .uri(u -> u.path("/quote")
                            .queryParam("symbol", finnhubSymbol)
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return Optional.empty();

            double c  = toDouble(resp.get("c"));
            double d  = toDouble(resp.get("d"));
            double dp = toDouble(resp.get("dp"));

            if (c == 0.0) return Optional.empty();

            return Optional.of(new FinnhubQuote(
                    BigDecimal.valueOf(c),
                    BigDecimal.valueOf(d),
                    BigDecimal.valueOf(dp)
            ));
        } catch (Exception e) {
            log.warn("Finnhub quote failed for {}: {}", finnhubSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        return ((Number) o).doubleValue();
    }

    public record FinnhubQuote(BigDecimal last, BigDecimal changeAbs, BigDecimal changePct) {}
}
