CREATE TABLE marketplace_listing (
    id              BIGSERIAL PRIMARY KEY,
    seller_id       BIGINT NOT NULL REFERENCES telegram_user (id),
    user_card_id    BIGINT NOT NULL REFERENCES user_card (id),
    price           BIGINT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    sold_at         TIMESTAMP,
    buyer_id        BIGINT REFERENCES telegram_user (id),

    CONSTRAINT marketplace_listing_price_positive CHECK (price > 0),
    CONSTRAINT marketplace_listing_status_check
        CHECK (status IN ('ACTIVE', 'SOLD', 'CANCELLED'))
);

CREATE UNIQUE INDEX marketplace_listing_active_card
    ON marketplace_listing (user_card_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_marketplace_listing_sold_feed
    ON marketplace_listing (sold_at DESC NULLS LAST)
    WHERE status = 'SOLD';

CREATE TABLE user_card_ownership_history (
    id                  BIGSERIAL PRIMARY KEY,
    user_card_id        BIGINT NOT NULL REFERENCES user_card (id),
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user (id),
    acquired_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    acquisition_type    VARCHAR(32) NOT NULL,

    CONSTRAINT ownership_acquisition_type_check
        CHECK (acquisition_type IN ('PACK_OPENING', 'ADMIN_GRANT', 'MARKETPLACE_PURCHASE'))
);

CREATE INDEX idx_ownership_history_card_user
    ON user_card_ownership_history (user_card_id, telegram_user_id);

INSERT INTO economy_config (key, value, description) VALUES
    ('marketplace.commission_percent', '10', 'Комиссия маркетплейса (%)');

INSERT INTO user_card_ownership_history (user_card_id, telegram_user_id, acquired_at, acquisition_type)
SELECT id, telegram_user_id, acquired_at, 'PACK_OPENING'
FROM user_card;
