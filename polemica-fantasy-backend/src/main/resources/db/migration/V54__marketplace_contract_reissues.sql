INSERT INTO economy_config (key, value, description) VALUES
    ('marketplace.contract_reissue_discount_percent', '15', 'Снижение минимальной цены маркетплейса за каждое перезаключение контракта (%)');

UPDATE user_card uc
SET times_renewed = 0
FROM marketplace_listing ml
WHERE ml.user_card_id = uc.id
  AND ml.status = 'ACTIVE';

ALTER TABLE marketplace_watch_filter
    ADD COLUMN min_times_renewed INT,
    ADD COLUMN max_times_renewed INT;

ALTER TABLE marketplace_watch_filter
    ADD CONSTRAINT marketplace_watch_min_times_renewed_non_negative
        CHECK (min_times_renewed IS NULL OR min_times_renewed >= 0),
    ADD CONSTRAINT marketplace_watch_max_times_renewed_non_negative
        CHECK (max_times_renewed IS NULL OR max_times_renewed >= 0),
    ADD CONSTRAINT marketplace_watch_times_renewed_range
        CHECK (
            min_times_renewed IS NULL
            OR max_times_renewed IS NULL
            OR min_times_renewed <= max_times_renewed
        );

DROP INDEX IF EXISTS uk_marketplace_watch_normalized;

CREATE UNIQUE INDEX uk_marketplace_watch_normalized
    ON marketplace_watch_filter (
        telegram_user_id,
        COALESCE(fantasy_player_id, -1),
        COALESCE(tournament_id, -1),
        COALESCE(rarity, ''),
        COALESCE(max_price, -1),
        perk_ids_key,
        COALESCE(min_times_renewed, -1),
        COALESCE(max_times_renewed, -1)
    );
