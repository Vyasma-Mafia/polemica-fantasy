ALTER TABLE series_game
    ADD CONSTRAINT uk_series_game_series_polemica UNIQUE (series_id, polemica_game_id);
