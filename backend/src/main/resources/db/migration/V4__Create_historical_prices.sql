-- Create historical_prices table for caching historical price data
CREATE TABLE historical_prices (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    price_date DATE NOT NULL,
    close_price DECIMAL(19,6) NOT NULL,
    adjusted_close_price DECIMAL(19,6),
    open_price DECIMAL(19,6),
    high_price DECIMAL(19,6),
    low_price DECIMAL(19,6),
    volume BIGINT,
    CONSTRAINT uk_historical_symbol_date UNIQUE (symbol, price_date)
);

-- Create indexes for efficient querying
CREATE INDEX idx_historical_symbol_date ON historical_prices(symbol, price_date);
CREATE INDEX idx_historical_date ON historical_prices(price_date);
