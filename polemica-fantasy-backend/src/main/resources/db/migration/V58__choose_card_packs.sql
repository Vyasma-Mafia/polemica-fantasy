ALTER TABLE card_pack
    ADD COLUMN open_mode TEXT NOT NULL DEFAULT 'INSTANT';

ALTER TABLE card_pack
    ADD CONSTRAINT card_pack_open_mode_check
        CHECK (open_mode IN ('INSTANT', 'CHOOSE'));

CREATE TABLE user_card_pack_choice (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    card_pack_id BIGINT NOT NULL REFERENCES card_pack(id) ON DELETE CASCADE,
    options JSONB NOT NULL,
    selected_option_id TEXT,
    selected_user_card_ids JSONB,
    payment_kind TEXT NOT NULL,
    price_fantiki_reserved BIGINT NOT NULL DEFAULT 0,
    free_usage_reserved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    selected_at TIMESTAMPTZ,
    CONSTRAINT user_card_pack_choice_payment_kind_check
        CHECK (payment_kind IN ('ZERO', 'FREE', 'PAID'))
);

CREATE UNIQUE INDEX uq_user_card_pack_choice_pending
    ON user_card_pack_choice(telegram_user_id, card_pack_id)
    WHERE selected_at IS NULL;

CREATE INDEX idx_user_card_pack_choice_user_pending
    ON user_card_pack_choice(telegram_user_id, selected_at);
