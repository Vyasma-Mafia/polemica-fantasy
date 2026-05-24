ALTER TABLE marketplace_watch_filter
    ADD COLUMN achievement_ids_key VARCHAR(1024) NOT NULL DEFAULT '';

CREATE TABLE marketplace_watch_filter_achievement (
    watch_filter_id BIGINT NOT NULL REFERENCES marketplace_watch_filter(id) ON DELETE CASCADE,
    achievement_id  VARCHAR(64) NOT NULL REFERENCES achievement(id),
    PRIMARY KEY (watch_filter_id, achievement_id)
);

ALTER TABLE marketplace_watch_filter
    DROP CONSTRAINT uk_marketplace_watch;

WITH ranked_duplicates AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY
                telegram_user_id,
                COALESCE(fantasy_player_id, -1),
                COALESCE(tournament_id, -1),
                COALESCE(rarity, ''),
                COALESCE(max_price, -1),
                achievement_ids_key
            ORDER BY created_at ASC, id ASC
        ) AS rn
    FROM marketplace_watch_filter
)
DELETE FROM marketplace_watch_filter mwf
USING ranked_duplicates rd
WHERE mwf.id = rd.id
  AND rd.rn > 1;

CREATE UNIQUE INDEX uk_marketplace_watch_normalized
    ON marketplace_watch_filter (
        telegram_user_id,
        COALESCE(fantasy_player_id, -1),
        COALESCE(tournament_id, -1),
        COALESCE(rarity, ''),
        COALESCE(max_price, -1),
        achievement_ids_key
    );

CREATE INDEX idx_mwf_achievement_filter
    ON marketplace_watch_filter_achievement (achievement_id, watch_filter_id);
