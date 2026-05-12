ALTER TABLE user_card
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_user_card_not_deleted
    ON user_card (deleted_at);

COMMENT ON COLUMN user_card.deleted_at IS 'Soft-delete timestamp; non-null means card is recycled and hidden from active flows';
