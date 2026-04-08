-- Дополнительные тиры наград за лидерборд серии (между top10 и участием)
INSERT INTO economy_config (key, value, description) VALUES
('series.reward.top25', '25', 'Награда за 11–25 место'),
('series.reward.top50', '20', 'Награда за 26–50 место');

UPDATE economy_config
SET description = 'Награда за участие (51+ место)'
WHERE key = 'series.reward.participation';
