-- V16: capture the UI language at alert creation so the scheduled trigger
-- emails in the same language the user picked on the site. Snapshot at
-- creation time (matches the existing user_email pattern in V13). Two-char
-- ISO codes only: 'tr' (default) or 'en'.

ALTER TABLE price_alerts
    ADD COLUMN language VARCHAR(2) NOT NULL DEFAULT 'tr';
