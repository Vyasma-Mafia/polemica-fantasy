CREATE TABLE user_legendary_upgrade_event (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    user_card_id BIGINT REFERENCES user_card(id) ON DELETE SET NULL,
    upgraded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_legendary_upgrade_event_user_time
    ON user_legendary_upgrade_event(telegram_user_id, upgraded_at);

CREATE UNIQUE INDEX idx_legendary_upgrade_event_user_card_unique
    ON user_legendary_upgrade_event(user_card_id)
    WHERE user_card_id IS NOT NULL;

CREATE INDEX idx_marketplace_listing_sold_buyer_time
    ON marketplace_listing(buyer_id, sold_at)
    WHERE status = 'SOLD' AND buyer_id IS NOT NULL;

CREATE INDEX idx_marketplace_listing_sold_seller_time
    ON marketplace_listing(seller_id, sold_at)
    WHERE status = 'SOLD' AND buyer_id IS NOT NULL;

CREATE INDEX idx_product_event_achievement_user_type_subject_time
    ON product_event(telegram_user_id, event_type, subject_type, subject_id, created_at);

UPDATE achievement_definition
SET enabled = TRUE,
    tracking_started_at = COALESCE(tracking_started_at, now()),
    updated_at = now()
WHERE code IN (
    'market_buy_1',
    'market_buy_5',
    'market_sell_1',
    'market_sell_5',
    'market_watch_1',
    'market_unique_counterparties_5',
    'share_profile_1',
    'share_team_1',
    'compare_open_1',
    'view_public_profile_5',
    'legendary_upgrade_1',
    'crafted_legendary_3'
);
