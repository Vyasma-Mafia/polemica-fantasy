CREATE TABLE user_achievement_card_choice (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    achievement_id BIGINT NOT NULL REFERENCES achievement_definition(id) ON DELETE CASCADE,
    reward_id BIGINT NOT NULL REFERENCES achievement_reward(id) ON DELETE CASCADE,
    required_count INT NOT NULL,
    options JSONB NOT NULL,
    selected_option_ids JSONB,
    selected_user_card_ids JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    PRIMARY KEY (telegram_user_id, achievement_id, reward_id)
);

CREATE INDEX idx_user_achievement_card_choice_user
    ON user_achievement_card_choice(telegram_user_id, claimed_at);

ALTER TABLE user_card_ownership_history
    DROP CONSTRAINT IF EXISTS ownership_acquisition_type_check;

ALTER TABLE user_card_ownership_history
    ADD CONSTRAINT ownership_acquisition_type_check
        CHECK (acquisition_type IN ('PACK_OPENING', 'ADMIN_GRANT', 'MARKETPLACE_PURCHASE', 'ACHIEVEMENT_REWARD'));
