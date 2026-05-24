ALTER TABLE achievement RENAME TO perk;
ALTER TABLE achievement_applicable_role RENAME TO perk_applicable_role;
ALTER TABLE perk_applicable_role RENAME COLUMN achievement_id TO perk_id;

ALTER TABLE card_template_achievement RENAME TO card_template_perk;
ALTER TABLE card_template_perk RENAME COLUMN achievement_id TO perk_id;

ALTER TABLE fantasy_team_card_game_score RENAME COLUMN achievement_bonus TO perk_bonus;
ALTER TABLE fantasy_team_card_game_achievement RENAME TO fantasy_team_card_game_perk;
ALTER TABLE fantasy_team_card_game_perk RENAME COLUMN achievement_id TO perk_id;

ALTER TABLE card_pack_achievement RENAME TO card_pack_perk;
ALTER TABLE card_pack_perk RENAME COLUMN achievement_id TO perk_id;

ALTER TABLE marketplace_watch_filter RENAME COLUMN achievement_ids_key TO perk_ids_key;
ALTER TABLE marketplace_watch_filter_achievement RENAME TO marketplace_watch_filter_perk;
ALTER TABLE marketplace_watch_filter_perk RENAME COLUMN achievement_id TO perk_id;

UPDATE economy_config
SET key = 'card.value.perk_bonus',
    description = REPLACE(description, 'достижение', 'перк')
WHERE key = 'card.value.achievement_bonus';

ALTER INDEX IF EXISTS achievement_pkey RENAME TO perk_pkey;
ALTER INDEX IF EXISTS achievement_applicable_role_pkey RENAME TO perk_applicable_role_pkey;
ALTER INDEX IF EXISTS card_template_achievement_pkey RENAME TO card_template_perk_pkey;
ALTER INDEX IF EXISTS idx_card_template_achievement_template RENAME TO idx_card_template_perk_template;
ALTER INDEX IF EXISTS uq_card_template_achievement_template_achievement RENAME TO uq_card_template_perk_template_perk;
ALTER INDEX IF EXISTS fantasy_team_card_game_achievement_pkey RENAME TO fantasy_team_card_game_perk_pkey;
ALTER INDEX IF EXISTS idx_ftc_game_achievement_score RENAME TO idx_ftc_game_perk_score;
ALTER INDEX IF EXISTS card_pack_achievement_pkey RENAME TO card_pack_perk_pkey;
ALTER INDEX IF EXISTS marketplace_watch_filter_achievement_pkey RENAME TO marketplace_watch_filter_perk_pkey;
ALTER INDEX IF EXISTS idx_mwf_achievement_filter RENAME TO idx_mwf_perk_filter;

ALTER SEQUENCE IF EXISTS card_template_achievement_id_seq RENAME TO card_template_perk_id_seq;
ALTER SEQUENCE IF EXISTS fantasy_team_card_game_achievement_id_seq RENAME TO fantasy_team_card_game_perk_id_seq;

DO $$
DECLARE
    old_constraint_name TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'achievement_applicable_role_achievement_id_fkey') THEN
        ALTER TABLE perk_applicable_role
            RENAME CONSTRAINT achievement_applicable_role_achievement_id_fkey TO perk_applicable_role_perk_id_fkey;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'card_template_achievement_card_template_id_fkey') THEN
        ALTER TABLE card_template_perk
            RENAME CONSTRAINT card_template_achievement_card_template_id_fkey TO card_template_perk_card_template_id_fkey;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_card_template_achievement_achievement') THEN
        ALTER TABLE card_template_perk
            RENAME CONSTRAINT fk_card_template_achievement_achievement TO fk_card_template_perk_perk;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fantasy_team_card_game_achievement_card_game_score_id_fkey') THEN
        ALTER TABLE fantasy_team_card_game_perk
            RENAME CONSTRAINT fantasy_team_card_game_achievement_card_game_score_id_fkey
            TO fantasy_team_card_game_perk_card_game_score_id_fkey;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fantasy_team_card_game_achievement_achievement_id_fkey') THEN
        ALTER TABLE fantasy_team_card_game_perk
            RENAME CONSTRAINT fantasy_team_card_game_achievement_achievement_id_fkey
            TO fantasy_team_card_game_perk_perk_id_fkey;
    END IF;

    SELECT conname
    INTO old_constraint_name
    FROM pg_constraint
    WHERE conrelid = 'fantasy_team_card_game_perk'::regclass
      AND contype = 'u'
      AND conname LIKE 'fantasy_team_card_game_achievement_card_game_score_id%'
    LIMIT 1;

    IF old_constraint_name IS NOT NULL THEN
        EXECUTE format(
            'ALTER TABLE fantasy_team_card_game_perk RENAME CONSTRAINT %I TO %I',
            old_constraint_name,
            'fantasy_team_card_game_perk_card_game_score_id_perk_id_key'
        );
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'card_pack_achievement_card_pack_id_fkey') THEN
        ALTER TABLE card_pack_perk
            RENAME CONSTRAINT card_pack_achievement_card_pack_id_fkey TO card_pack_perk_card_pack_id_fkey;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'card_pack_achievement_achievement_id_fkey') THEN
        ALTER TABLE card_pack_perk
            RENAME CONSTRAINT card_pack_achievement_achievement_id_fkey TO card_pack_perk_perk_id_fkey;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'marketplace_watch_filter_achievement_watch_filter_id_fkey') THEN
        ALTER TABLE marketplace_watch_filter_perk
            RENAME CONSTRAINT marketplace_watch_filter_achievement_watch_filter_id_fkey
            TO marketplace_watch_filter_perk_watch_filter_id_fkey;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'marketplace_watch_filter_achievement_achievement_id_fkey') THEN
        ALTER TABLE marketplace_watch_filter_perk
            RENAME CONSTRAINT marketplace_watch_filter_achievement_achievement_id_fkey
            TO marketplace_watch_filter_perk_perk_id_fkey;
    END IF;
END $$;
