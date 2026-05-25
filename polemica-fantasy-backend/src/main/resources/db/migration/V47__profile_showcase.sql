CREATE TABLE user_profile_customization (
    telegram_user_id BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    profile_frame_code VARCHAR(96),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_profile_featured_achievement (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    achievement_id BIGINT NOT NULL REFERENCES achievement_definition(id) ON DELETE CASCADE,
    display_order INT NOT NULL,
    PRIMARY KEY (telegram_user_id, achievement_id)
);

CREATE UNIQUE INDEX ux_user_profile_featured_order
    ON user_profile_featured_achievement(telegram_user_id, display_order);

CREATE INDEX idx_user_profile_featured_user_order
    ON user_profile_featured_achievement(telegram_user_id, display_order, achievement_id);
