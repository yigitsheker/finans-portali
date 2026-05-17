-- Make the RSS feed list manageable from the admin panel instead of being
-- a hard-coded array inside NewsService. The service will seed this table
-- from the original list on first boot, then read from it on every cycle.

CREATE TABLE news_feeds (
    id          BIGSERIAL PRIMARY KEY,
    url         VARCHAR(500) NOT NULL UNIQUE,
    category    VARCHAR(60)  NOT NULL,
    source      VARCHAR(120) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

CREATE INDEX idx_news_feeds_enabled ON news_feeds(enabled);
CREATE INDEX idx_news_feeds_category ON news_feeds(category);
