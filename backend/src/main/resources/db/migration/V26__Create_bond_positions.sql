-- Bond/bill positions & transactions — SIMULATION ONLY (no real orders).
-- Nominal-based; one position per (user_id, isin) with weighted-average dirty
-- cost. Full history (buy/sell/coupon/redemption) in bond_transactions.

CREATE TABLE bond_positions (
    id                BIGSERIAL PRIMARY KEY,
    user_id           VARCHAR(100) NOT NULL,
    isin              VARCHAR(12)  NOT NULL,
    symbol            VARCHAR(30)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    type              VARCHAR(30)  NOT NULL,
    issuer            VARCHAR(100),
    currency          VARCHAR(3)   NOT NULL DEFAULT 'TRY',
    remaining_nominal NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_cost        NUMERIC(19,2) NOT NULL DEFAULT 0,
    avg_cost_price    NUMERIC(12,4) NOT NULL DEFAULT 0,
    coupon_rate       NUMERIC(10,4),
    coupon_frequency  INTEGER,
    maturity_date     DATE,
    purchase_date     DATE,
    last_coupon_date  DATE,
    realized_pnl      NUMERIC(19,2) NOT NULL DEFAULT 0,
    coupon_income     NUMERIC(19,2) NOT NULL DEFAULT 0,
    status            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bond_user_isin UNIQUE (user_id, isin)
);

CREATE INDEX idx_bond_pos_user ON bond_positions(user_id);
CREATE INDEX idx_bond_pos_status ON bond_positions(status);
CREATE INDEX idx_bond_pos_maturity ON bond_positions(maturity_date);

CREATE TABLE bond_transactions (
    id               BIGSERIAL PRIMARY KEY,
    user_id          VARCHAR(100) NOT NULL,
    isin             VARCHAR(12)  NOT NULL,
    type             VARCHAR(24)  NOT NULL,
    nominal          NUMERIC(19,2) NOT NULL,
    clean_price      NUMERIC(12,4),
    accrued_interest NUMERIC(12,4),
    dirty_price      NUMERIC(12,4),
    gross_amount     NUMERIC(19,2) NOT NULL,
    realized_pnl     NUMERIC(19,2) NOT NULL DEFAULT 0,
    gross_coupon     NUMERIC(19,2),
    tax_amount       NUMERIC(19,2),
    net_coupon       NUMERIC(19,2),
    executed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note             VARCHAR(200)
);

CREATE INDEX idx_bond_txn_user ON bond_transactions(user_id);
CREATE INDEX idx_bond_txn_executed ON bond_transactions(executed_at);
