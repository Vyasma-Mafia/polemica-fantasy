CREATE INDEX idx_periodic_rating_entry_user_period
    ON periodic_rating_entry (telegram_user_id, period_id);

-- Rarity below is the visual rarity of an achievement. These milestones grant Fantiki only;
-- they do not create cards or cosmetics and stay separate from periodic trophy rewards.
WITH seed(code, condition_type, target_value, chain_group, chain_level, title, description, rarity, display_order) AS (
    VALUES
    ('periodic_rating_period_1','PERIODIC_RATING_PERIODS',1,'periodic_rating_period',1,'Первый итог','Попасть в итоговый рейтинг первого завершённого периода','COMMON',580),
    ('periodic_rating_period_5','PERIODIC_RATING_PERIODS',5,'periodic_rating_period',2,'В ритме рейтинга','Попасть в итоговый рейтинг 5 завершённых периодов','RARE',584),
    ('periodic_rating_top10_1','PERIODIC_RATING_TOP10',1,'periodic_rating_top10',1,'Финалист периода','Впервые занять место в топ-10 рейтинга периода','RARE',588),
    ('periodic_rating_top10_5','PERIODIC_RATING_TOP10',5,'periodic_rating_top10',2,'Стабильный финалист','Занять место в топ-10 в 5 периодах','EPIC',592),
    ('periodic_rating_podium_1','PERIODIC_RATING_PODIUMS',1,'periodic_rating_podium',1,'На пьедестале периода','Впервые занять место в топ-3 рейтинга периода','EPIC',596),
    ('periodic_rating_champion_1','PERIODIC_RATING_WINS',1,'periodic_rating_champion',1,'Чемпион периода','Впервые занять первое место в рейтинге периода','LEGENDARY',600)
)
INSERT INTO achievement_definition (
    code, category, condition_type, history_policy, target_value, chain_group, chain_level,
    title, description, rarity, visibility, enabled, tracking_started_at, display_order, updated_at
)
SELECT
    code,
    'PERIODIC_RATING',
    condition_type,
    'FROM_ACHIEVEMENTS_LAUNCH',
    target_value,
    chain_group,
    chain_level,
    title,
    description,
    rarity,
    'PUBLIC',
    TRUE,
    now(),
    display_order,
    now()
FROM seed
ON CONFLICT (code) DO UPDATE
SET category = EXCLUDED.category,
    condition_type = EXCLUDED.condition_type,
    history_policy = EXCLUDED.history_policy,
    target_value = EXCLUDED.target_value,
    chain_group = EXCLUDED.chain_group,
    chain_level = EXCLUDED.chain_level,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    rarity = EXCLUDED.rarity,
    visibility = EXCLUDED.visibility,
    enabled = TRUE,
    tracking_started_at = COALESCE(achievement_definition.tracking_started_at, EXCLUDED.tracking_started_at),
    display_order = EXCLUDED.display_order,
    updated_at = now();

DELETE FROM achievement_reward ar
USING achievement_definition d
WHERE ar.achievement_id = d.id
  AND d.code IN (
      'periodic_rating_period_1',
      'periodic_rating_period_5',
      'periodic_rating_top10_1',
      'periodic_rating_top10_5',
      'periodic_rating_podium_1',
      'periodic_rating_champion_1'
  );

WITH rewards(code, amount) AS (
    VALUES
    ('periodic_rating_period_1',10),
    ('periodic_rating_period_5',25),
    ('periodic_rating_top10_1',10),
    ('periodic_rating_top10_5',50),
    ('periodic_rating_podium_1',25),
    ('periodic_rating_champion_1',50)
)
INSERT INTO achievement_reward (achievement_id, reward_type, amount, reward_code, metadata, display_order)
SELECT d.id, 'FANTIKI', r.amount, NULL, NULL, 10
FROM rewards r
JOIN achievement_definition d ON d.code = r.code;
