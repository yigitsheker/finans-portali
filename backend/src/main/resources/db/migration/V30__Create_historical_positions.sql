-- Create historical_positions table for tracking past investments
CREATE TABLE historical_positions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    buy_date DATE NOT NULL,
    buy_price DECIMAL(20, 6) NOT NULL,
    lots INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_currency CHECK (currency IN ('TRY', 'USD')),
    CONSTRAINT chk_lots_positive CHECK (lots > 0),
    CONSTRAINT chk_buy_price_positive CHECK (buy_price > 0),
    CONSTRAINT uk_user_symbol_buydate_created UNIQUE (user_id, symbol, buy_date, created_at)
);

-- Index for efficient user-based queries
CREATE INDEX idx_historical_positions_user_id ON historical_positions(user_id);
CREATE INDEX idx_historical_positions_buy_date ON historical_positions(buy_date DESC);

-- Comment
COMMENT ON TABLE historical_positions IS 'User-specific historical investment positions for tracking past performance';
COMMENT ON COLUMN historical_positions.user_id IS 'Keycloak user ID (JWT subject)';
COMMENT ON COLUMN historical_positions.currency IS 'Native currency of the instrument (TRY or USD)';
