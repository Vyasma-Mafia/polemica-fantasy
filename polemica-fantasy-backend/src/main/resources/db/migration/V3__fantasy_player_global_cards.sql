-- Global player identity (one row per Polemica user). Card templates reference this, not a tournament roster row.
CREATE TABLE fantasy_player (
    id                  BIGSERIAL PRIMARY KEY,
    polemica_user_id    BIGINT NOT NULL UNIQUE,
    nickname            VARCHAR(512) NOT NULL,
    photo_url           VARCHAR(2048)
);

CREATE INDEX idx_fantasy_player_polemica ON fantasy_player (polemica_user_id);

INSERT INTO fantasy_player (polemica_user_id, nickname, photo_url)
SELECT DISTINCT ON (polemica_user_id) polemica_user_id, nickname, photo_url
FROM tournament_player
ORDER BY polemica_user_id, id;

ALTER TABLE tournament_player ADD COLUMN fantasy_player_id BIGINT;

UPDATE tournament_player tp
SET fantasy_player_id = fp.id
FROM fantasy_player fp
WHERE fp.polemica_user_id = tp.polemica_user_id;

ALTER TABLE tournament_player ALTER COLUMN fantasy_player_id SET NOT NULL;
ALTER TABLE tournament_player
    ADD CONSTRAINT fk_tournament_player_fantasy FOREIGN KEY (fantasy_player_id) REFERENCES fantasy_player (id);

CREATE UNIQUE INDEX uk_tournament_player_tournament_fantasy ON tournament_player (tournament_id, fantasy_player_id);

ALTER TABLE tournament_player DROP COLUMN polemica_user_id;
ALTER TABLE tournament_player DROP COLUMN nickname;
ALTER TABLE tournament_player DROP COLUMN photo_url;

ALTER TABLE card_template ADD COLUMN fantasy_player_id BIGINT;

UPDATE card_template ct
SET fantasy_player_id = tp.fantasy_player_id
FROM tournament_player tp
WHERE tp.id = ct.tournament_player_id;

ALTER TABLE card_template ALTER COLUMN fantasy_player_id SET NOT NULL;
ALTER TABLE card_template
    ADD CONSTRAINT fk_card_template_fantasy_player FOREIGN KEY (fantasy_player_id) REFERENCES fantasy_player (id);

ALTER TABLE card_template DROP CONSTRAINT card_template_tournament_player_id_fkey;
DROP INDEX idx_card_template_tournament_player;
ALTER TABLE card_template DROP COLUMN tournament_player_id;

CREATE INDEX idx_card_template_fantasy_player ON card_template (fantasy_player_id);
