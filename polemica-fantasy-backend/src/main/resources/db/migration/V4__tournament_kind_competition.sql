-- Tournament kind: STANDALONE (default) vs POLEMICA_COMPETITION
ALTER TABLE tournament
    ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'STANDALONE';

ALTER TABLE tournament
    ADD COLUMN polemica_competition_id BIGINT NULL;

CREATE UNIQUE INDEX uq_tournament_polemica_competition_id
    ON tournament (polemica_competition_id)
    WHERE polemica_competition_id IS NOT NULL;

-- Series: nullable prefix; optional game number range for competition tournaments
ALTER TABLE series
    ALTER COLUMN name_prefix DROP NOT NULL;

ALTER TABLE series
    ADD COLUMN game_num_from BIGINT NULL;

ALTER TABLE series
    ADD COLUMN game_num_to BIGINT NULL;
