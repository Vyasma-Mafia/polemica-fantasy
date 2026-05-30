ALTER TABLE series_player
    ADD COLUMN replacement_polemica_user_id BIGINT;

ALTER TABLE series_player
    ADD CONSTRAINT ck_series_player_replacement_polemica_user_id_positive
        CHECK (replacement_polemica_user_id IS NULL OR replacement_polemica_user_id > 0);

CREATE UNIQUE INDEX uk_series_player_series_replacement_polemica_user_id
    ON series_player (series_id, replacement_polemica_user_id)
    WHERE replacement_polemica_user_id IS NOT NULL;

ALTER TABLE fantasy_team_card_game_score
    ADD COLUMN scored_polemica_user_id BIGINT,
    ADD COLUMN scored_player_name VARCHAR(512),
    ADD COLUMN scored_via_replacement BOOLEAN NOT NULL DEFAULT FALSE;
