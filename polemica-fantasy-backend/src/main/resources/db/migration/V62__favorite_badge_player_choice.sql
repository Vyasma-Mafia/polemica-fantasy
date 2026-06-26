ALTER TABLE user_profile_customization
    ADD COLUMN favorite_badge_fantasy_player_id BIGINT REFERENCES fantasy_player(id) ON DELETE SET NULL;

CREATE INDEX idx_user_profile_customization_favorite_badge_player
    ON user_profile_customization(favorite_badge_fantasy_player_id);
