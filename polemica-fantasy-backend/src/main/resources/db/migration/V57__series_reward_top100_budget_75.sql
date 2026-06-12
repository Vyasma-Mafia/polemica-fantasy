INSERT INTO economy_config (key, value, description)
VALUES ('series.reward.top100', '40', 'Награда за 51–100 место')
ON CONFLICT (key) DO UPDATE
SET value = EXCLUDED.value,
    description = EXCLUDED.description;

UPDATE economy_config
SET value = '250'
WHERE key = 'series.reward.1';

UPDATE economy_config
SET value = '200'
WHERE key = 'series.reward.2';

UPDATE economy_config
SET value = '150'
WHERE key = 'series.reward.3';

UPDATE economy_config
SET value = '100'
WHERE key = 'series.reward.top10';

UPDATE economy_config
SET value = '75'
WHERE key = 'series.reward.top25';

UPDATE economy_config
SET value = '50'
WHERE key = 'series.reward.top50';

UPDATE economy_config
SET value = '30',
    description = 'Награда за участие (101+ место)'
WHERE key = 'series.reward.participation';

UPDATE economy_config
SET value = '75'
WHERE key = 'league.reward_scale.BUDGET';
