-- Explicit per-user / per-pack open count (not derived from user_card rows) so
-- bann/dismantle/delete of cards cannot change the limit.
CREATE TABLE user_card_pack_opens (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user (id) ON DELETE CASCADE,
    card_pack_id        BIGINT NOT NULL REFERENCES card_pack (id) ON DELETE CASCADE,
    open_count          INT     NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_card_pack_opens UNIQUE (telegram_user_id, card_pack_id),
    CONSTRAINT chk_user_card_pack_opens_open_count_nonnegative CHECK (open_count >= 0)
);

CREATE INDEX idx_user_card_pack_opens_user ON user_card_pack_opens (telegram_user_id);
CREATE INDEX idx_user_card_pack_opens_pack ON user_card_pack_opens (card_pack_id);

-- Best-effort backfill: CEIL(n_cards / slots_per_open) where slots = SUM(cards_count) in rarity config.
-- If some cards were removed, the count can be a lower bound; going forward only open_count is authoritative.
INSERT INTO user_card_pack_opens (telegram_user_id, card_pack_id, open_count)
WITH pack_slots AS (
    SELECT
        cpr.card_pack_id,
        GREATEST(1, COALESCE(SUM(cpr.cards_count), 0))::numeric AS slots
    FROM card_pack_rarity_config cpr
    GROUP BY cpr.card_pack_id
),
per_user AS (
    SELECT
        uc.telegram_user_id,
        uc.source_card_pack_id AS pack_id,
        COUNT(*)::numeric AS n
    FROM user_card uc
    WHERE uc.source_card_pack_id IS NOT NULL
    GROUP BY uc.telegram_user_id, uc.source_card_pack_id
)
SELECT
    p.telegram_user_id,
    p.pack_id,
    GREATEST(0, CEIL(p.n / s.slots))::int
FROM per_user p
INNER JOIN pack_slots s ON s.card_pack_id = p.pack_id;
