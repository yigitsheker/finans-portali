-- Append-only ledger of main-portfolio movements (buy/sell) so the user can
-- review their history and so realized P&L on closed positions survives after
-- the live position row is removed.
CREATE TABLE IF NOT EXISTS portfolio_transactions (
    id            BIGSERIAL PRIMARY KEY,
    user_id       VARCHAR(100)  NOT NULL,
    symbol        VARCHAR(30)   NOT NULL,
    name          VARCHAR(200),
    type          VARCHAR(10)   NOT NULL,           -- BUY | SELL
    quantity      NUMERIC(19, 6) NOT NULL,
    price         NUMERIC(19, 6),
    amount        NUMERIC(19, 2),
    realized_pnl  NUMERIC(19, 2),                   -- SELL only
    executed_at   TIMESTAMP     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_portfolio_tx_user        ON portfolio_transactions (user_id, executed_at);
CREATE INDEX IF NOT EXISTS idx_portfolio_tx_user_symbol ON portfolio_transactions (user_id, symbol);
