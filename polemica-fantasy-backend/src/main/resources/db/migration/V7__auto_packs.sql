ALTER TABLE card_pack
    ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN price_fantiki BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN use_all_tournament_players BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE card_pack_player (
    id                  BIGSERIAL PRIMARY KEY,
    card_pack_id        BIGINT NOT NULL REFERENCES card_pack (id) ON DELETE CASCADE,
    fantasy_player_id   BIGINT NOT NULL REFERENCES fantasy_player (id),
    UNIQUE (card_pack_id, fantasy_player_id)
);

CREATE INDEX idx_card_pack_player_pack ON card_pack_player (card_pack_id);
CREATE INDEX idx_card_pack_player_player ON card_pack_player (fantasy_player_id);

ALTER TABLE card_pack_rarity_config DROP COLUMN probability;
