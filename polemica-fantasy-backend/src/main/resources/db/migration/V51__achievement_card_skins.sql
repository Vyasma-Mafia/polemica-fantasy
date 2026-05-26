INSERT INTO card_skin (code, name)
VALUES
    ('budget_edition', 'Бюджетный выпуск'),
    ('common_challenge_edition', 'Common challenge'),
    ('winner_edition', 'Выпуск победителя'),
    ('crafter_edition', 'Выпуск крафтера'),
    ('pack_hunter_edition', 'Охотник за паками')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name;

WITH skin_rewards(achievement_code, skin_code) AS (
    VALUES
    ('budget_team_30', 'budget_edition'),
    -- Temporary V1 preview attachment until a real common-only challenge condition exists.
    ('top_quarter_10', 'common_challenge_edition'),
    ('series_win_10', 'winner_edition'),
    ('legendary_upgrade_10', 'crafter_edition'),
    ('pack_open_150', 'pack_hunter_edition')
)
UPDATE achievement_reward ar
SET metadata = COALESCE(ar.metadata, '{}'::jsonb) || jsonb_build_object('skinCode', sr.skin_code)
FROM achievement_definition d
JOIN skin_rewards sr ON sr.achievement_code = d.code
WHERE ar.achievement_id = d.id
  AND ar.reward_type = 'CARD_CHOICE_ROLL';
