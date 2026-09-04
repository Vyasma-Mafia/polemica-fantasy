CREATE TABLE fantasy_player_import_block (
    polemica_user_id BIGINT PRIMARY KEY,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fantasy_player_import_block_polemica_user_id_positive
        CHECK (polemica_user_id > 0)
);

INSERT INTO fantasy_player_import_block (polemica_user_id, reason)
VALUES (41582, 'Player opted out of Polemica Fantasy');

-- Keep the opt-out independently of the player profile, then remove the profile
-- only while it is still an unused orphan. If any history appears before rollout,
-- preserve that history and let the import policy prevent new tournament links.
DELETE FROM fantasy_player fp
WHERE fp.polemica_user_id = 41582
  AND NOT EXISTS (SELECT 1 FROM tournament_player tp WHERE tp.fantasy_player_id = fp.id)
  AND NOT EXISTS (SELECT 1 FROM card_template ct WHERE ct.fantasy_player_id = fp.id)
  AND NOT EXISTS (SELECT 1 FROM card_pack_player cpp WHERE cpp.fantasy_player_id = fp.id)
  AND NOT EXISTS (
      SELECT 1
      FROM fantasy_player_merge_audit fpma
      WHERE fpma.target_fantasy_player_id = fp.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM marketplace_watch_filter mwf
      WHERE mwf.fantasy_player_id = fp.id
  )
  AND NOT EXISTS (SELECT 1 FROM user_card_merge ucm WHERE ucm.fantasy_player_id = fp.id)
  AND NOT EXISTS (
      SELECT 1
      FROM user_profile_customization upc
      WHERE upc.favorite_badge_fantasy_player_id = fp.id
  );
