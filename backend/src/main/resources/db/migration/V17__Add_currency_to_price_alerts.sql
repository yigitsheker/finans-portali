-- V17: respect the site-wide currency toggle on price alerts. When the user is
-- viewing the site in USD and creates an alarm on a TRY-quoted instrument
-- (THYAO at $8.20), the alarm and its email should both be in USD. We store
-- the chosen currency on the alert so the scheduled checker — which has no
-- UI context — can convert and format consistently.
--
-- creationPrice / triggeredPrice are interpreted in this currency too; legacy
-- rows default to TRY since that was the implicit assumption before this
-- migration.

ALTER TABLE price_alerts
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'TRY';
