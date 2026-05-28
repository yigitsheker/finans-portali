-- Backfill source_lang for legacy news_articles rows.
--
-- V21 added the source_lang column but only the fetch path (parseItem) sets
-- it going forward. Existing rows came in NULL and the translation-on-read
-- code path early-returns when source_lang is null, leaving English-source
-- articles (Investing.com, CoinDesk, Cointelegraph, Yahoo Finance) rendered
-- as English even for Turkish readers. This migration classifies legacy
-- rows by their source_url host and stamps the right ISO code so the
-- background prewarmer can pick them up on its next pass.
--
-- The set of English hosts mirrors NewsService.EN_FEED_HOST_HINTS — keep in
-- sync if a new English feed is added there.

UPDATE news_articles
SET source_lang = CASE
    WHEN source_url ILIKE '%cointelegraph.com%'        THEN 'en'
    WHEN source_url ILIKE '%coindesk.com%'             THEN 'en'
    WHEN source_url ILIKE '%feeds.finance.yahoo.com%'  THEN 'en'
    WHEN source_url ILIKE '%www.investing.com%'        THEN 'en'
    ELSE 'tr'
END
WHERE source_lang IS NULL;
