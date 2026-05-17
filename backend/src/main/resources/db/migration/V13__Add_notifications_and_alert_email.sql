-- V13: persist user email on price_alerts so the scheduled checker can email
-- the user without an Authentication context. Plus a notifications table so
-- triggered alerts also surface as an in-app inbox item.

ALTER TABLE price_alerts
    ADD COLUMN user_email VARCHAR(200);

CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      VARCHAR(100) NOT NULL,
    type         VARCHAR(40)  NOT NULL,    -- 'PRICE_ALERT', 'SYSTEM', ...
    title        VARCHAR(200) NOT NULL,
    message      TEXT         NOT NULL,
    reference_id VARCHAR(100),             -- e.g. PriceAlert.id as string
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    read_at      TIMESTAMP
);

CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id, is_read);

CREATE INDEX idx_notifications_user_created
    ON notifications(user_id, created_at DESC);
