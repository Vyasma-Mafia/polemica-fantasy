CREATE TABLE user_card_merge_preview (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    operation VARCHAR(32) NOT NULL,
    input_user_card_ids JSONB NOT NULL,
    input_set_hash VARCHAR(96) NOT NULL,
    fixed_perk_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    offered_perk_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected_skin_source_user_card_id BIGINT REFERENCES user_card(id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at TIMESTAMPTZ,
    result_user_card_id BIGINT UNIQUE REFERENCES user_card(id),
    CONSTRAINT user_card_merge_preview_operation_check
        CHECK (operation IN ('COMMON_TO_RARE', 'RARE_TO_EPIC')),
    CONSTRAINT user_card_merge_preview_input_ids_json_check
        CHECK (jsonb_typeof(input_user_card_ids) = 'array'),
    CONSTRAINT user_card_merge_preview_fixed_json_check
        CHECK (jsonb_typeof(fixed_perk_ids) = 'array'),
    CONSTRAINT user_card_merge_preview_offered_json_check
        CHECK (jsonb_typeof(offered_perk_ids) = 'array')
);

CREATE UNIQUE INDEX user_card_merge_preview_active_input_set
    ON user_card_merge_preview (telegram_user_id, operation, input_set_hash)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_card_merge_preview_user_created
    ON user_card_merge_preview (telegram_user_id, created_at DESC);

CREATE TABLE user_card_merge (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    preview_id BIGINT UNIQUE REFERENCES user_card_merge_preview(id),
    result_user_card_id BIGINT NOT NULL UNIQUE REFERENCES user_card(id),
    operation VARCHAR(32) NOT NULL,
    source_rarity VARCHAR(32) NOT NULL,
    result_rarity VARCHAR(32) NOT NULL,
    fantasy_player_id BIGINT NOT NULL REFERENCES fantasy_player(id),
    selected_perk_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    fixed_perk_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    offered_perk_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected_skin_source_user_card_id BIGINT REFERENCES user_card(id),
    result_skin_code VARCHAR(64),
    cost_fantiki BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_card_merge_operation_check
        CHECK (operation IN ('COMMON_TO_RARE', 'RARE_TO_EPIC')),
    CONSTRAINT user_card_merge_rarity_check
        CHECK (
            (operation = 'COMMON_TO_RARE' AND source_rarity = 'COMMON' AND result_rarity = 'RARE')
            OR (operation = 'RARE_TO_EPIC' AND source_rarity = 'RARE' AND result_rarity = 'EPIC')
        ),
    CONSTRAINT user_card_merge_selected_json_check
        CHECK (jsonb_typeof(selected_perk_ids) = 'array'),
    CONSTRAINT user_card_merge_fixed_json_check
        CHECK (jsonb_typeof(fixed_perk_ids) = 'array'),
    CONSTRAINT user_card_merge_offered_json_check
        CHECK (jsonb_typeof(offered_perk_ids) = 'array')
);

CREATE INDEX idx_card_merge_user_created
    ON user_card_merge (telegram_user_id, created_at DESC);

CREATE INDEX idx_card_merge_result_card
    ON user_card_merge (result_user_card_id);

CREATE INDEX idx_card_merge_fantasy_player
    ON user_card_merge (fantasy_player_id);

CREATE TABLE user_card_merge_input (
    id BIGSERIAL PRIMARY KEY,
    merge_id BIGINT NOT NULL REFERENCES user_card_merge(id) ON DELETE CASCADE,
    input_user_card_id BIGINT NOT NULL REFERENCES user_card(id),
    input_card_template_id BIGINT NOT NULL REFERENCES card_template(id),
    input_rarity VARCHAR(32) NOT NULL,
    input_perk_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    input_uses_remaining INT NOT NULL,
    input_times_renewed INT NOT NULL,
    input_skin_code VARCHAR(64),
    CONSTRAINT user_card_merge_input_rarity_check
        CHECK (input_rarity IN ('COMMON', 'RARE')),
    CONSTRAINT user_card_merge_input_perks_json_check
        CHECK (jsonb_typeof(input_perk_ids) = 'array')
);

CREATE UNIQUE INDEX idx_card_merge_input_unique_card
    ON user_card_merge_input (merge_id, input_user_card_id);

CREATE INDEX idx_card_merge_input_card
    ON user_card_merge_input (input_user_card_id);

ALTER TABLE user_card_ownership_history
    DROP CONSTRAINT IF EXISTS ownership_acquisition_type_check;

ALTER TABLE user_card_ownership_history
    ADD CONSTRAINT ownership_acquisition_type_check
        CHECK (acquisition_type IN (
            'PACK_OPENING',
            'ADMIN_GRANT',
            'MARKETPLACE_PURCHASE',
            'ACHIEVEMENT_REWARD',
            'CARD_MERGE'
        ));

INSERT INTO release_note (
    title,
    body,
    button_text,
    button_url,
    audience,
    active,
    published_at,
    created_at
) VALUES (
    'Слияние карт',
    $$Теперь дубликаты одного игрока можно собрать в карту выше редкостью: 3 COMMON -> RARE или 3 RARE -> EPIC. Перед подтверждением показываем контракт, перки, ценность и потерю скинов.$$,
    'Открыть слияние',
    '/cards/merge',
    'ALL',
    TRUE,
    now(),
    now()
);

WITH seed(code, category, condition_type, target_value, chain_group, chain_level, title, description, rarity, display_order) AS (
    VALUES
    ('card_merge_1','COLLECTION','CARD_MERGES',1,'card_merge',1,'Первая сборка','Выполнить первое слияние карт','COMMON',820),
    ('card_merge_10','COLLECTION','CARD_MERGES',10,'card_merge',2,'Мастерская коллекции','Выполнить 10 слияний карт','RARE',830),
    ('card_merge_epic_1','COLLECTION','CARD_MERGE_EPIC_RESULTS',1,'card_merge_epic',1,'Эпик из деталей','Собрать первую EPIC-карту через слияние','RARE',840),
    ('card_merge_epic_5','COLLECTION','CARD_MERGE_EPIC_RESULTS',5,'card_merge_epic',2,'Эпический сборщик','Собрать 5 EPIC-карт через слияние','EPIC',850),
    ('card_merge_players_5','COLLECTION','CARD_MERGE_UNIQUE_PLAYERS',5,'card_merge_players',1,'Ростерная мастерская','Собрать карты через слияние для 5 разных игроков','RARE',860)
)
INSERT INTO achievement_definition (
    code, category, condition_type, history_policy, target_value, chain_group, chain_level,
    title, description, rarity, visibility, enabled, tracking_started_at, display_order, updated_at
)
SELECT
    code,
    category,
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
      'card_merge_1',
      'card_merge_10',
      'card_merge_epic_1',
      'card_merge_epic_5',
      'card_merge_players_5'
  );

WITH rewards(code, reward_type, amount, reward_code, metadata, display_order) AS (
    VALUES
    ('card_merge_1','FANTIKI',25,NULL,NULL::jsonb,10),
    ('card_merge_10','BADGE_STYLE',NULL,'card_merge',NULL::jsonb,10),
    ('card_merge_10','CARD_CHOICE_ROLL',NULL,NULL,jsonb_build_object('source','ACTIVE_PACKS','rarity','COMMON','count',2,'options',5),20),
    ('card_merge_epic_1','CARD_CHOICE_ROLL',NULL,NULL,jsonb_build_object('source','ACTIVE_PACKS','rarity','RARE','count',2,'options',5),10),
    ('card_merge_epic_5','BADGE_STYLE',NULL,'epic_crafter',NULL::jsonb,10),
    ('card_merge_epic_5','CARD_CHOICE_ROLL',NULL,NULL,jsonb_build_object('source','ACTIVE_PACKS','rarity','EPIC','count',1,'options',3),20),
    ('card_merge_players_5','FANTIKI',75,NULL,NULL::jsonb,10)
)
INSERT INTO achievement_reward (achievement_id, reward_type, amount, reward_code, metadata, display_order)
SELECT d.id, r.reward_type, r.amount, r.reward_code, r.metadata, r.display_order
FROM rewards r
JOIN achievement_definition d ON d.code = r.code;
