ALTER TABLE series
    ADD COLUMN game_phase INT NULL;

ALTER TABLE series
    ADD CONSTRAINT chk_series_game_phase_range
        CHECK (game_phase IS NULL OR game_phase BETWEEN 0 AND 2);

UPDATE series s
SET game_phase = 0
FROM tournament t
WHERE s.tournament_id = t.id
  AND t.kind = 'POLEMICA_COMPETITION'
  AND s.game_phase IS NULL;
