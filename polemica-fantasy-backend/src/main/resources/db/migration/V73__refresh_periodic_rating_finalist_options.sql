-- The first finalized period accidentally gave every rank 4-5 reward the same
-- first three fantasy_player rows. Rebuild unfinished bundled rewards from the
-- current RARE player pool of active auto-generated packs. Draft selections are
-- reset because the old player/perk pair may no longer be an allowed bundle.
WITH active_rare_packs AS (
    SELECT DISTINCT cp.id, cp.tournament_id, cp.use_all_tournament_players
    FROM card_pack cp
    JOIN card_pack_rarity_config cpr ON cpr.card_pack_id = cp.id
    WHERE cp.active = TRUE
      AND cp.auto_generated = TRUE
      AND cpr.rarity = 'RARE'
      AND cpr.cards_count > 0
),
active_players AS (
    SELECT tp.fantasy_player_id
    FROM active_rare_packs arp
    JOIN tournament_player tp ON tp.tournament_id = arp.tournament_id
    WHERE arp.use_all_tournament_players = TRUE
      AND tp.excluded_from_pack_pool = FALSE

    UNION

    SELECT cpp.fantasy_player_id
    FROM active_rare_packs arp
    JOIN card_pack_player cpp ON cpp.card_pack_id = arp.id
    WHERE arp.use_all_tournament_players = FALSE
      AND NOT EXISTS (
          SELECT 1
          FROM tournament_player tp
          WHERE tp.tournament_id = arp.tournament_id
            AND tp.fantasy_player_id = cpp.fantasy_player_id
            AND tp.excluded_from_pack_pool = TRUE
      )
),
target_rewards AS (
    SELECT r.id AS reward_id,
           r.period_id,
           row_number() OVER (
               PARTITION BY r.period_id
               ORDER BY r.rank, u.telegram_id, r.id
           ) - 1 AS bundle_index
    FROM periodic_rating_reward r
    JOIN telegram_user u ON u.id = r.telegram_user_id
    WHERE r.status IN ('AVAILABLE', 'DRAFT', 'CHANGES_REQUESTED', 'OVERDUE')
      AND r.policy_snapshot->>'playerSelectionMode' = 'BUNDLED_OPTIONS'
),
ordered_players AS (
    SELECT tr.period_id,
           ap.fantasy_player_id,
           row_number() OVER (
               PARTITION BY tr.period_id
               ORDER BY md5(tr.period_id::text || ':' || ap.fantasy_player_id::text), ap.fantasy_player_id
           ) - 1 AS pool_index
    FROM (SELECT DISTINCT period_id FROM target_rewards) tr
    CROSS JOIN active_players ap
),
player_counts AS (
    SELECT period_id, count(*) AS pool_size
    FROM ordered_players
    GROUP BY period_id
    HAVING count(*) >= 3
),
ordered_perks AS (
    SELECT id AS perk_id, row_number() OVER (ORDER BY id) - 1 AS pool_index
    FROM perk
    WHERE can_appear_on_random_cards = TRUE
),
perk_count AS (
    SELECT count(*) AS pool_size
    FROM ordered_perks
    HAVING count(*) >= 3
),
bundle_rows AS (
    SELECT tr.reward_id,
           option_index,
           op.fantasy_player_id,
           operk.perk_id
    FROM target_rewards tr
    JOIN player_counts pc ON pc.period_id = tr.period_id
    CROSS JOIN perk_count pkc
    CROSS JOIN generate_series(0, 2) AS options(option_index)
    JOIN ordered_players op
      ON op.period_id = tr.period_id
     AND op.pool_index = mod(tr.bundle_index * 3 + option_index, pc.pool_size)
    JOIN ordered_perks operk
      ON operk.pool_index = mod(tr.bundle_index * 3 + option_index, pkc.pool_size)
),
rebuilt_bundles AS (
    SELECT reward_id,
           jsonb_agg(
               jsonb_build_object(
                   'playerId', fantasy_player_id,
                   'perkIds', jsonb_build_array(perk_id)
               )
               ORDER BY option_index
           ) AS bundles
    FROM bundle_rows
    GROUP BY reward_id
)
UPDATE periodic_rating_reward r
SET policy_snapshot = jsonb_set(r.policy_snapshot, '{bundles}', rb.bundles),
    selection = '{}'::jsonb,
    status = 'AVAILABLE',
    changes_requested_reason = NULL,
    version = r.version + 1,
    updated_at = now()
FROM rebuilt_bundles rb
WHERE r.id = rb.reward_id;
