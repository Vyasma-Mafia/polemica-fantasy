ALTER TABLE tournament_player
    ADD COLUMN excluded_from_pack_pool BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN tournament_player.excluded_from_pack_pool IS 'When true, player is omitted from random card pack pools for this tournament.';
