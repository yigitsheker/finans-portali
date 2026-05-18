-- V15: VIOP (Vadeli İşlem ve Opsiyon Piyasası) futures contracts.
-- Data is scraped from İş Yatırım's public VIOP page on a periodic schedule
-- (15-30 min) and upserted by symbol.

CREATE TABLE viop_contracts (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(40)  NOT NULL UNIQUE,   -- e.g. F_AKBNK0526
    name            VARCHAR(120) NOT NULL,          -- e.g. "AKBNK Mayis 2026 Vadeli"
    underlying      VARCHAR(20)  NOT NULL,          -- e.g. AKBNK, XU030, USDTRY
    maturity_month  INTEGER      NOT NULL,          -- 1..12
    maturity_year   INTEGER      NOT NULL,          -- 4-digit
    category        VARCHAR(40)  NOT NULL,          -- STOCK, INDEX, FX_TRY, FX_USD, METAL_TRY, METAL_USD, METAL
    last_price      NUMERIC(19,6),
    change_pct      NUMERIC(8,3),                   -- Daily change percent (negative allowed)
    change_abs      NUMERIC(19,6),
    volume_tl       NUMERIC(19,2),                  -- Daily volume in TL
    volume_lots     BIGINT,                         -- Daily volume in number of contracts
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_viop_category ON viop_contracts(category);
CREATE INDEX idx_viop_underlying ON viop_contracts(underlying);
CREATE INDEX idx_viop_maturity ON viop_contracts(maturity_year, maturity_month);
