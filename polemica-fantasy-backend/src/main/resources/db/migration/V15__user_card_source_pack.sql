ALTER TABLE user_card
    ADD COLUMN source_card_pack_id BIGINT NULL REFERENCES card_pack (id) ON DELETE SET NULL;

CREATE INDEX idx_user_card_source_pack ON user_card (source_card_pack_id);
