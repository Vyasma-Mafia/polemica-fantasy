ALTER TABLE series DROP CONSTRAINT series_expected_game_count_check;

ALTER TABLE series ADD CONSTRAINT series_expected_game_count_check
    CHECK (expected_game_count IS NULL OR expected_game_count BETWEEN 1 AND 1000);
