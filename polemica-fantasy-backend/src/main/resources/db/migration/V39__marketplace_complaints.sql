CREATE TABLE marketplace_complaint (
    id               BIGSERIAL PRIMARY KEY,
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_complaint_listing_user UNIQUE (listing_id, telegram_user_id)
);

CREATE INDEX idx_complaint_listing ON marketplace_complaint (listing_id);
CREATE INDEX idx_complaint_user ON marketplace_complaint (telegram_user_id);

CREATE TABLE marketplace_listing_sanction (
    id                 BIGSERIAL PRIMARY KEY,
    listing_id         BIGINT NOT NULL REFERENCES marketplace_listing(id),
    reason             TEXT NOT NULL,
    seller_fine        BIGINT NOT NULL DEFAULT 0,
    buyer_fine         BIGINT NOT NULL DEFAULT 0,
    complainant_reward BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    admin_username     VARCHAR(64) NOT NULL,
    CONSTRAINT uk_sanction_listing UNIQUE (listing_id)
);

ALTER TABLE telegram_user
    ADD COLUMN marketplace_banned_until TIMESTAMPTZ;

INSERT INTO economy_config (key, value, description)
VALUES ('marketplace.daily_complaint_limit', '5', 'Максимум жалоб на маркетплейсе в сутки');
