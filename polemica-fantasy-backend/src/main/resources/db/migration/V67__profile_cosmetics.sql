CREATE TABLE profile_cosmetic (
    code          VARCHAR(96) PRIMARY KEY,
    kind          VARCHAR(32)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    style_token   VARCHAR(96),
    display_order INTEGER      NOT NULL DEFAULT 0,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_profile_cosmetic_kind
        CHECK (kind IN ('TITLE', 'ACCENT', 'BACKGROUND'))
);

WITH cosmetics(code, kind, name, style_token, display_order) AS (
    VALUES
        ('budget_champion_title', 'TITLE', 'Бюджетный чемпион', 'budget_champion', 100),
        ('budget_marathon_title', 'TITLE', 'Бюджетный марафонец', 'budget_marathon', 110),
        ('budget_winner_title', 'TITLE', 'Победитель бюджета', 'budget_winner', 120),
        ('collection_title', 'TITLE', 'Коллекционер', 'collection', 200),
        ('crafter_title', 'TITLE', 'Мастер крафта', 'crafter', 300),
        ('dynasty_title', 'TITLE', 'Династия', 'dynasty', 400),
        ('market_network_title', 'TITLE', 'Рыночный связной', 'market_network', 500),
        ('market_seller_title', 'TITLE', 'Продавец рынка', 'market_seller', 510),
        ('podium_title', 'TITLE', 'На пьедестале', 'podium', 600),
        ('scout_title', 'TITLE', 'Скаут', 'scout', 700),
        ('series_winner_title', 'TITLE', 'Победитель серии', 'series_winner', 800),
        ('title_elite_manager', 'TITLE', 'Элитный менеджер', 'elite_manager', 900),
        ('title_marathon_manager', 'TITLE', 'Марафонец составов', 'marathon_manager', 910),
        ('budget_top_accent', 'ACCENT', 'Бюджетный топ', 'budget_top', 1000),
        ('dual_strategy_accent', 'ACCENT', 'Две стратегии', 'dual_strategy', 1010),
        ('top10_accent', 'ACCENT', 'Верхняя группа', 'top10', 1020)
)
INSERT INTO profile_cosmetic (code, kind, name, style_token, display_order)
SELECT code, kind, name, style_token, display_order
FROM cosmetics
ON CONFLICT (code) DO NOTHING;

INSERT INTO profile_cosmetic (code, kind, name, style_token, display_order)
SELECT DISTINCT
    unlock.cosmetic_code,
    CASE
        WHEN LOWER(unlock.cosmetic_code) LIKE '%accent%' OR LOWER(unlock.cosmetic_code) LIKE '%background%' THEN 'ACCENT'
        ELSE 'TITLE'
    END,
    INITCAP(REPLACE(unlock.cosmetic_code, '_', ' ')),
    REGEXP_REPLACE(LOWER(unlock.cosmetic_code), '[^a-z0-9_]+', '_', 'g'),
    9000
FROM user_cosmetic_unlock unlock
WHERE unlock.cosmetic_type = 'COSMETIC_UNLOCK'
  AND unlock.cosmetic_code IS NOT NULL
ON CONFLICT (code) DO NOTHING;

ALTER TABLE user_profile_customization
    ADD COLUMN profile_title_code VARCHAR(96) REFERENCES profile_cosmetic(code) ON DELETE SET NULL,
    ADD COLUMN profile_accent_code VARCHAR(96) REFERENCES profile_cosmetic(code) ON DELETE SET NULL,
    ADD COLUMN profile_background_code VARCHAR(96) REFERENCES profile_cosmetic(code) ON DELETE SET NULL;

CREATE INDEX idx_profile_cosmetic_kind_enabled
    ON profile_cosmetic(kind, enabled, display_order, code);
