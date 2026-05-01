-- Initial schema for Finans Portali
-- Created: 2026-04-24

-- Market Instruments Table
CREATE TABLE market_instruments (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    finnhub_symbol VARCHAR(50),
    delayed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Market Quotes Table
CREATE TABLE market_quotes (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES market_instruments(id) ON DELETE CASCADE,
    last DECIMAL(19,6) NOT NULL,
    change_abs DECIMAL(19,6) NOT NULL,
    change_pct DECIMAL(19,6) NOT NULL,
    as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Market Candles Table (Historical Data)
CREATE TABLE market_candles (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES market_instruments(id) ON DELETE CASCADE,
    day DATE NOT NULL,
    open_price DECIMAL(19,6) NOT NULL,
    high_price DECIMAL(19,6) NOT NULL,
    low_price DECIMAL(19,6) NOT NULL,
    close_price DECIMAL(19,6) NOT NULL,
    volume BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(instrument_id, day)
);

-- Portfolio Positions Table
CREATE TABLE portfolio_positions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    quantity DECIMAL(19,6) NOT NULL,
    avg_cost DECIMAL(19,6),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, symbol)
);

-- Price Alerts Table
CREATE TABLE price_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    instrument_id BIGINT NOT NULL REFERENCES market_instruments(id) ON DELETE CASCADE,
    symbol VARCHAR(30) NOT NULL,
    alert_type VARCHAR(20) NOT NULL,
    target_price DECIMAL(19,6) NOT NULL,
    creation_price DECIMAL(19,6),
    active BOOLEAN NOT NULL DEFAULT true,
    triggered_at TIMESTAMP WITH TIME ZONE,
    triggered_price DECIMAL(19,6),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(200)
);

-- News Articles Table
CREATE TABLE news_articles (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    url VARCHAR(1000) NOT NULL UNIQUE,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    category VARCHAR(50),
    source VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_market_quotes_instrument_asof ON market_quotes(instrument_id, as_of DESC);
CREATE INDEX idx_market_candles_instrument_day ON market_candles(instrument_id, day DESC);
CREATE INDEX idx_portfolio_positions_user ON portfolio_positions(user_id);
CREATE INDEX idx_price_alerts_user_symbol ON price_alerts(user_id, symbol);
CREATE INDEX idx_price_alerts_active ON price_alerts(active);
CREATE INDEX idx_price_alerts_instrument ON price_alerts(instrument_id);
CREATE INDEX idx_news_articles_published ON news_articles(published_at DESC);
CREATE INDEX idx_news_articles_category ON news_articles(category);

-- Comments
COMMENT ON TABLE market_instruments IS 'Financial instruments (stocks, crypto, forex, etc.)';
COMMENT ON TABLE market_quotes IS 'Real-time price quotes for instruments';
COMMENT ON TABLE market_candles IS 'Historical OHLCV data for charting';
COMMENT ON TABLE portfolio_positions IS 'User portfolio positions';
COMMENT ON TABLE price_alerts IS 'User price alerts and notifications';
COMMENT ON TABLE news_articles IS 'Financial news articles from RSS feeds';