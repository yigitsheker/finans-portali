-- Bilingual support for the news pipeline.
--
-- The site UI runs in TR + EN; most RSS feeds publish in Turkish but a few
-- (CoinDesk, Cointelegraph, Yahoo Finance EN) publish in English. To show
-- every article in the user's chosen language we keep the original text in
-- the existing title/summary/content columns and lazily populate three
-- *_translated columns the first time a non-source-language reader requests
-- the article. LibreTranslate provides the translation; results are cached
-- here so we translate each row at most once.
--
-- source_lang stays nullable so existing rows don't need a synchronous
-- backfill — NewsService detects + sets it the first time an article is
-- read.

ALTER TABLE news_articles
    ADD COLUMN source_lang VARCHAR(2);

ALTER TABLE news_articles
    ADD COLUMN title_translated VARCHAR(300);

ALTER TABLE news_articles
    ADD COLUMN summary_translated VARCHAR(2000);

ALTER TABLE news_articles
    ADD COLUMN content_translated TEXT;
