-- V18: per-user preferences storage. Currently holds the Settings →
-- Bildirimler toggle map; designed as a single TEXT (JSON-encoded) column
-- so adding a new preference category never needs a schema migration.
-- Switch to JSONB later if we ever need to query into the blob.
--
-- One row per Keycloak subject. Inserts come from the Settings page; the
-- frontend keeps a localStorage mirror so the first-render UI never blocks
-- on this fetch.

CREATE TABLE user_preferences (
    user_id            VARCHAR(100) PRIMARY KEY,
    notification_prefs TEXT,
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);
