ALTER TABLE series
    ADD COLUMN game_started_on DATE;

COMMENT ON COLUMN series.game_started_on IS
    'STANDALONE sync filter by Polemica game.started calendar day in server timezone; NULL means no day filter';
