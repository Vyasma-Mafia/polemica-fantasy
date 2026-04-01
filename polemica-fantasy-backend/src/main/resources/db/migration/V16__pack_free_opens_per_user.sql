ALTER TABLE card_pack
    ADD COLUMN free_opens_per_user INT NOT NULL DEFAULT 0;

CREATE TABLE user_card_pack_free_usage (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user (id) ON DELETE CASCADE,
    card_pack_id        BIGINT NOT NULL REFERENCES card_pack (id) ON DELETE CASCADE,
    free_opens_used     INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_card_pack_free_usage UNIQUE (telegram_user_id, card_pack_id)
);

CREATE INDEX idx_user_card_pack_free_usage_user ON user_card_pack_free_usage (telegram_user_id);
CREATE INDEX idx_user_card_pack_free_usage_pack ON user_card_pack_free_usage (card_pack_id);
