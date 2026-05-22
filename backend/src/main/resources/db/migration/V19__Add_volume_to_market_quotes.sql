-- V19: surface real trading volume on the market summary feed.
--
-- Yahoo's chart endpoint already returns volume per bar
-- (indicators.quote[0].volume[]); we just weren't parsing it. The
-- HISSE / KRIPTO / EMTIA list pages will read the last bar's volume
-- straight off MarketQuote so the column doesn't need a join with
-- MarketCandle on every summary fetch.
--
-- Nullable so legacy rows from before V19 don't violate constraints.
-- 0 would be misleading (looks like "no trading") whereas NULL is
-- clearly "we don't have this data".

ALTER TABLE market_quotes
    ADD COLUMN volume BIGINT;
