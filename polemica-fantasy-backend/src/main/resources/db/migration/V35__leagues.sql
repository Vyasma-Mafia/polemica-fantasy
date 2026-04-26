INSERT INTO economy_config (key, value, description) VALUES
    ('league.reward_scale.MAIN', '100', 'Масштаб наград основной лиги (%)'),
    ('league.reward_scale.BUDGET', '50', 'Масштаб наград бюджетной лиги (%)'),
    ('league.budget.value_cap', '175', 'Максимальная суммарная ценность команды в бюджетной лиге');

CREATE TABLE league (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    league_type VARCHAR(32) NOT NULL,
    value_cap BIGINT,
    max_legendary_count INT,
    min_team_size INT NOT NULL DEFAULT 1,
    max_team_size INT NOT NULL DEFAULT 3,
    created_by_telegram_user_id BIGINT REFERENCES telegram_user(id),
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    entry_fee BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO league (code, name, description, league_type)
VALUES
    ('MAIN', 'Основная лига', 'Без ограничений по ценности и редкости', 'SYSTEM'),
    ('BUDGET', 'Бюджетная лига', 'Суммарная ценность команды ограничена', 'SYSTEM');

CREATE TABLE series_league (
    id BIGSERIAL PRIMARY KEY,
    series_id BIGINT NOT NULL REFERENCES series(id),
    league_id BIGINT NOT NULL REFERENCES league(id),
    value_cap_override BIGINT,
    reward_scale_override INT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_series_league UNIQUE (series_id, league_id)
);

INSERT INTO series_league (series_id, league_id)
SELECT s.id, l.id
FROM series s
CROSS JOIN league l
WHERE l.league_type = 'SYSTEM';

ALTER TABLE fantasy_team
    ADD COLUMN series_league_id BIGINT REFERENCES series_league(id);

UPDATE fantasy_team ft
SET series_league_id = sl.id
FROM series_league sl
JOIN league l ON l.id = sl.league_id
WHERE sl.series_id = ft.series_id
  AND l.code = 'MAIN';

ALTER TABLE fantasy_team
    ALTER COLUMN series_league_id SET NOT NULL;

ALTER TABLE fantasy_team DROP CONSTRAINT IF EXISTS fantasy_team_telegram_user_id_series_id_key;
ALTER TABLE fantasy_team DROP CONSTRAINT IF EXISTS uk_fantasy_team_user_series;
ALTER TABLE fantasy_team
    ADD CONSTRAINT fantasy_team_user_series_league_key UNIQUE (telegram_user_id, series_id, series_league_id);

UPDATE economy_config
SET description = '[DEPRECATED - replaced by league.max_legendary_count] ' || COALESCE(description, '')
WHERE key = 'legendary.team.max_per_series'
  AND description NOT LIKE '[DEPRECATED - replaced by league.max_legendary_count]%';
