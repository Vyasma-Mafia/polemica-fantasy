-- One-time cleanup for series id 5: remove fantasy_team_card rows whose card's fantasy_player
-- is not in series_player, while team submission is still open (team_deadline not passed).
-- Then compact slot numbers to 1..n and remove empty fantasy_team rows.

DELETE FROM fantasy_team_card ftc
USING fantasy_team ft, user_card uc, card_template ct, series s
WHERE ftc.fantasy_team_id = ft.id
  AND ftc.user_card_id = uc.id
  AND uc.card_template_id = ct.id
  AND ft.series_id = s.id
  AND s.id = 5
  AND s.team_deadline > CURRENT_TIMESTAMP
  AND NOT EXISTS (
    SELECT 1
    FROM series_player sp
    INNER JOIN tournament_player tp ON sp.tournament_player_id = tp.id
    WHERE sp.series_id = ft.series_id
      AND tp.fantasy_player_id = ct.fantasy_player_id
  );

UPDATE fantasy_team_card f
SET slot = r.new_slot
FROM (
  SELECT ftc.id,
         ROW_NUMBER() OVER (PARTITION BY ftc.fantasy_team_id ORDER BY ftc.slot) AS new_slot
  FROM fantasy_team_card ftc
  INNER JOIN fantasy_team ft ON ftc.fantasy_team_id = ft.id
  WHERE ft.series_id = 5
) r
WHERE f.id = r.id
  AND f.slot IS DISTINCT FROM r.new_slot;

DELETE FROM fantasy_team ft
WHERE ft.series_id = 5
  AND EXISTS (
    SELECT 1 FROM series s WHERE s.id = 5 AND s.team_deadline > CURRENT_TIMESTAMP
  )
  AND NOT EXISTS (
    SELECT 1 FROM fantasy_team_card ftc WHERE ftc.fantasy_team_id = ft.id
  );
