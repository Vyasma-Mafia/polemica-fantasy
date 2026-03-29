CREATE TABLE fantasy_team_card_game_score (
    id                      BIGSERIAL PRIMARY KEY,
    fantasy_team_card_id    BIGINT NOT NULL REFERENCES fantasy_team_card (id) ON DELETE CASCADE,
    series_game_id          BIGINT NOT NULL REFERENCES series_game (id),
    base_points             DOUBLE PRECISION,
    achievement_bonus       DOUBLE PRECISION,
    rarity_modifier         DOUBLE PRECISION,
    total_score             DOUBLE PRECISION,
    UNIQUE (fantasy_team_card_id, series_game_id)
);

CREATE INDEX idx_ftc_game_score_card ON fantasy_team_card_game_score (fantasy_team_card_id);
CREATE INDEX idx_ftc_game_score_game ON fantasy_team_card_game_score (series_game_id);

CREATE TABLE fantasy_team_card_game_achievement (
    id                      BIGSERIAL PRIMARY KEY,
    card_game_score_id      BIGINT NOT NULL REFERENCES fantasy_team_card_game_score (id) ON DELETE CASCADE,
    achievement_id          VARCHAR(64) NOT NULL REFERENCES achievement (id),
    bonus_points            DOUBLE PRECISION NOT NULL,
    UNIQUE (card_game_score_id, achievement_id)
);

CREATE INDEX idx_ftc_game_achievement_score ON fantasy_team_card_game_achievement (card_game_score_id);
