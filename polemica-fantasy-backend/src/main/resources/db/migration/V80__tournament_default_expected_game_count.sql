ALTER TABLE tournament
    ADD COLUMN default_expected_game_count INTEGER;

ALTER TABLE tournament
    ADD CONSTRAINT tournament_default_expected_game_count_check
    CHECK (default_expected_game_count IS NULL OR default_expected_game_count BETWEEN 1 AND 1000);
