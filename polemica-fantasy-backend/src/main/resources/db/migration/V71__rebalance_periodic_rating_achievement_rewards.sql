WITH rewards(code, amount) AS (
    VALUES
    ('periodic_rating_period_1',50),
    ('periodic_rating_period_5',250),
    ('periodic_rating_top10_1',200),
    ('periodic_rating_top10_5',1000),
    ('periodic_rating_podium_1',400),
    ('periodic_rating_champion_1',600)
)
UPDATE achievement_reward ar
SET amount = rewards.amount
FROM rewards
JOIN achievement_definition d ON d.code = rewards.code
WHERE ar.achievement_id = d.id
  AND ar.reward_type = 'FANTIKI';
