-- VİOP (futures) positions & transactions — SIMULATION ONLY (no real orders).
-- One net position per (user_id, contract_symbol): net-position logic in the
-- service prevents simultaneous long+short on the same contract. Full leg-by-leg
-- history (open/close/partial/expire) lives in viop_transactions.

CREATE TABLE viop_positions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            VARCHAR(100) NOT NULL,
    contract_symbol    VARCHAR(40)  NOT NULL,
    underlying         VARCHAR(20)  NOT NULL,
    contract_type      VARCHAR(40)  NOT NULL,
    maturity_date      DATE,
    direction          VARCHAR(10)  NOT NULL,
    quantity           NUMERIC(19,6) NOT NULL DEFAULT 0,
    entry_price        NUMERIC(19,6) NOT NULL DEFAULT 0,
    contract_size      NUMERIC(19,6) NOT NULL DEFAULT 1,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'TRY',
    margin_rate        NUMERIC(9,6),
    required_margin    NUMERIC(19,2),
    initial_margin     NUMERIC(19,2),
    maintenance_margin NUMERIC(19,2),
    realized_pnl       NUMERIC(19,2) NOT NULL DEFAULT 0,
    status             VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    opened_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at          TIMESTAMP,
    CONSTRAINT uk_viop_user_contract UNIQUE (user_id, contract_symbol)
);

CREATE INDEX idx_viop_pos_user ON viop_positions(user_id);
CREATE INDEX idx_viop_pos_status ON viop_positions(status);
CREATE INDEX idx_viop_pos_maturity ON viop_positions(maturity_date);

CREATE TABLE viop_transactions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(100) NOT NULL,
    contract_symbol VARCHAR(40)  NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    quantity        NUMERIC(19,6) NOT NULL,
    price           NUMERIC(19,6) NOT NULL,
    contract_size   NUMERIC(19,6) NOT NULL,
    position_size   NUMERIC(19,2) NOT NULL,
    realized_pnl    NUMERIC(19,2) NOT NULL DEFAULT 0,
    executed_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note            VARCHAR(200)
);

CREATE INDEX idx_viop_txn_user ON viop_transactions(user_id);
CREATE INDEX idx_viop_txn_executed ON viop_transactions(executed_at);
