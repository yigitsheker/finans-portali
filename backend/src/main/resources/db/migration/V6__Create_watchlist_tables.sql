-- Create watchlists table
CREATE TABLE watchlists (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_watchlists_user_id ON watchlists(user_id);

-- Create watchlist_items table
CREATE TABLE watchlist_items (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_watchlist FOREIGN KEY (watchlist_id) REFERENCES watchlists(id) ON DELETE CASCADE,
    CONSTRAINT uq_watchlist_symbol UNIQUE (watchlist_id, symbol)
);

CREATE INDEX idx_watchlist_items_watchlist_id ON watchlist_items(watchlist_id);
CREATE INDEX idx_watchlist_items_symbol ON watchlist_items(symbol);
