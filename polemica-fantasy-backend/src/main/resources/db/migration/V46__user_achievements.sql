ALTER TABLE fantasy_team
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE fantasy_team SET created_at = submitted_at WHERE submitted_at IS NOT NULL;

ALTER TABLE series
    ADD COLUMN finalized_at TIMESTAMPTZ NULL;

CREATE TABLE achievement_definition (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(96) NOT NULL UNIQUE,
    category VARCHAR(48) NOT NULL,
    condition_type VARCHAR(96) NOT NULL,
    history_policy VARCHAR(48) NOT NULL,
    target_value BIGINT NOT NULL,
    chain_group VARCHAR(96),
    chain_level INT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    icon_url VARCHAR(2048),
    accent_color VARCHAR(32),
    rarity VARCHAR(32) NOT NULL DEFAULT 'COMMON',
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    tracking_started_at TIMESTAMPTZ NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE achievement_reward (
    id BIGSERIAL PRIMARY KEY,
    achievement_id BIGINT NOT NULL REFERENCES achievement_definition(id) ON DELETE CASCADE,
    reward_type VARCHAR(48) NOT NULL,
    amount BIGINT,
    reward_code VARCHAR(96),
    metadata JSONB,
    display_order INT NOT NULL DEFAULT 0
);

CREATE TABLE user_achievement (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    achievement_id BIGINT NOT NULL REFERENCES achievement_definition(id) ON DELETE CASCADE,
    progress_value BIGINT NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    reward_snapshot JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (telegram_user_id, achievement_id)
);

CREATE TABLE user_cosmetic_unlock (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    cosmetic_type VARCHAR(48) NOT NULL,
    cosmetic_code VARCHAR(96) NOT NULL,
    source_type VARCHAR(48) NOT NULL DEFAULT 'ACHIEVEMENT',
    source_code VARCHAR(96),
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (telegram_user_id, cosmetic_type, cosmetic_code)
);

CREATE TABLE user_card_pack_open_event (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    card_pack_id BIGINT NOT NULL REFERENCES card_pack(id) ON DELETE CASCADE,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_achievement_definition_enabled_order ON achievement_definition(enabled, display_order, id);
CREATE INDEX idx_achievement_reward_definition ON achievement_reward(achievement_id, display_order, id);
CREATE INDEX idx_user_achievement_user ON user_achievement(telegram_user_id);
CREATE INDEX idx_pack_open_event_user_time ON user_card_pack_open_event(telegram_user_id, opened_at);
CREATE INDEX idx_fantasy_team_user_created ON fantasy_team(telegram_user_id, created_at);
CREATE INDEX idx_series_finalized_at ON series(finalized_at);

WITH seed(code, category, condition_type, target_value, chain_group, chain_level, title, description, rarity, enabled, display_order) AS (
    VALUES
    ('team_submit_1','PARTICIPATION','TEAMS_SUBMITTED',1,'team_submit',1,'Первый состав','Создать первую команду в любой лиге','COMMON',TRUE,10),
    ('team_submit_5','PARTICIPATION','TEAMS_SUBMITTED',5,'team_submit',2,'Регулярный участник','Создать команды в 5 лигах серий','COMMON',TRUE,20),
    ('team_submit_15','PARTICIPATION','TEAMS_SUBMITTED',15,'team_submit',3,'В расписании','Создать команды в 15 лигах серий','RARE',TRUE,30),
    ('team_submit_30','PARTICIPATION','TEAMS_SUBMITTED',30,'team_submit',4,'Стабильный менеджер','Создать команды в 30 лигах серий','EPIC',TRUE,40),
    ('dual_league_1','PARTICIPATION','DUAL_LEAGUE_SERIES',1,'dual_league',1,'Двойная заявка','В одной серии создать команды в MAIN и BUDGET','COMMON',TRUE,50),
    ('dual_league_10','PARTICIPATION','DUAL_LEAGUE_SERIES',10,'dual_league',2,'Две стратегии','В 10 разных сериях создать команды в MAIN и BUDGET','RARE',TRUE,60),
    ('budget_team_1','BUDGET','BUDGET_TEAMS_SUBMITTED',1,'budget_team',1,'Первый бюджет','Создать первую команду в BUDGET','COMMON',TRUE,110),
    ('budget_team_5','BUDGET','BUDGET_TEAMS_SUBMITTED',5,'budget_team',2,'Бюджетный старт','Создать команду в BUDGET 5 раз','COMMON',TRUE,120),
    ('budget_team_15','BUDGET','BUDGET_TEAMS_SUBMITTED',15,'budget_team',3,'Экономный стратег','Создать команду в BUDGET 15 раз','RARE',TRUE,130),
    ('budget_team_30','BUDGET','BUDGET_TEAMS_SUBMITTED',30,'budget_team',4,'Мастер бюджета','Создать команду в BUDGET 30 раз','EPIC',TRUE,140),
    ('budget_win_1','BUDGET','BUDGET_WINS',1,'budget_win',1,'Бюджетная победа','Победить в BUDGET-лиге завершенной серии','RARE',TRUE,150),
    ('budget_top10_5','BUDGET','BUDGET_TOP10',5,'budget_top10',1,'Бюджетный топ','5 раз попасть в топ-10 BUDGET','RARE',TRUE,160),
    ('series_win_1','RESULTS','SERIES_WINS',1,'series_win',1,'Первая победа','Победить в любой лиге завершенной серии','RARE',TRUE,210),
    ('series_win_3','RESULTS','SERIES_WINS',3,'series_win',2,'Серийный победитель','Победить в 3 лигах серий','RARE',TRUE,220),
    ('series_win_10','RESULTS','SERIES_WINS',10,'series_win',3,'Династия','Победить в 10 лигах серий','EPIC',TRUE,230),
    ('top3_5','RESULTS','TOP3_FINISHES',5,'top3',1,'На пьедестале','5 раз попасть в топ-3','RARE',TRUE,240),
    ('top10_10','RESULTS','TOP10_OR_HALF_FINISHES',10,'top10',1,'В верхней группе','10 раз попасть в топ-10 или верхнюю половину','RARE',TRUE,250),
    ('top_quarter_10','RESULTS','TOP_QUARTER_FINISHES',10,'top_quarter',1,'Стабильный результат','10 раз попасть в верхние 25% leaderboard','EPIC',TRUE,260),
    ('cards_total_10','COLLECTION','CARDS_OWNED_TOTAL',10,'cards_total',1,'Первая полка','Владеть 10 активными картами после запуска','COMMON',TRUE,310),
    ('cards_total_30','COLLECTION','CARDS_OWNED_TOTAL',30,'cards_total',2,'Коллекционер','Владеть 30 активными картами после запуска','RARE',TRUE,320),
    ('cards_total_100','COLLECTION','CARDS_OWNED_TOTAL',100,'cards_total',3,'Большая коллекция','Владеть 100 активными картами после запуска','EPIC',TRUE,330),
    ('first_epic','COLLECTION','FIRST_EPIC_CARD',1,'collection_rarity',1,'Эпический дроп','Получить и владеть активной EPIC-картой после запуска','RARE',TRUE,340),
    ('first_legendary','COLLECTION','FIRST_LEGENDARY_CARD',1,'collection_rarity',2,'Легенда в коллекции','Получить и владеть активной LEGENDARY-картой после запуска','EPIC',TRUE,350),
    ('first_skin_card','COLLECTION','FIRST_SKIN_CARD',1,'collection_skin',1,'Особый выпуск','Получить или применить активную карту со скином после запуска','RARE',TRUE,360),
    ('same_player_3_rarities','COLLECTION','SAME_PLAYER_3_RARITIES',1,'same_player_rarities',1,'Любимый игрок','Собрать активные карты одного игрока в 3 редкостях','EPIC',TRUE,370),
    ('pack_open_1','PACKS','PACKS_OPENED',1,'pack_open',1,'Первый пак','Открыть 1 пак после запуска','COMMON',TRUE,410),
    ('pack_open_5','PACKS','PACKS_OPENED',5,'pack_open',2,'Пять попыток','Открыть 5 паков после запуска','COMMON',TRUE,420),
    ('pack_open_15','PACKS','PACKS_OPENED',15,'pack_open',3,'Охота за редкостью','Открыть 15 паков после запуска','RARE',TRUE,430),
    ('pack_open_30','PACKS','PACKS_OPENED',30,'pack_open',4,'Большое вскрытие','Открыть 30 паков после запуска','EPIC',TRUE,440),
    ('pack_epic_drop_1','PACKS','PACK_EPIC_DROP',1,'pack_epic',1,'Эпик из пака','Получить EPIC из пака и владеть этой картой','RARE',TRUE,450),
    ('market_buy_1','MARKETPLACE','MARKETPLACE_PURCHASES',1,'market_buy',1,'Первая покупка','Купить первую карту на marketplace','COMMON',TRUE,510),
    ('market_buy_5','MARKETPLACE','MARKETPLACE_PURCHASES',5,'market_buy',2,'Охотник за картами','Купить 5 карт на marketplace','RARE',TRUE,520),
    ('market_sell_1','MARKETPLACE','MARKETPLACE_SALES',1,'market_sell',1,'Первая продажа','Продать первую карту на marketplace','COMMON',TRUE,530),
    ('market_sell_5','MARKETPLACE','MARKETPLACE_SALES',5,'market_sell',2,'Продавец','Продать 5 карт на marketplace','RARE',TRUE,540),
    ('market_watch_1','MARKETPLACE','MARKETPLACE_WATCHES',1,'market_watch',1,'На наблюдении','Создать первый marketplace watch-фильтр','COMMON',TRUE,550),
    ('market_unique_counterparties_5','MARKETPLACE','MARKETPLACE_UNIQUE_COUNTERPARTIES',5,'market_counterparties',1,'Широкий рынок','Провести сделки с 5 разными контрагентами','RARE',TRUE,560),
    ('share_profile_1','SOCIAL','SHARE_PROFILE',1,'share_profile',1,'Витрина открыта','Нажать share профиля','COMMON',TRUE,610),
    ('share_team_1','SOCIAL','SHARE_TEAM',1,'share_team',1,'Команда наружу','Нажать share команды','COMMON',TRUE,620),
    ('compare_open_1','SOCIAL','COMPARE_OPEN',1,'compare_open',1,'Сравнили составы','Открыть compare-view','COMMON',TRUE,630),
    ('view_public_profile_5','SOCIAL','PUBLIC_PROFILE_VIEWS',5,'public_profile_views',1,'Скаут','Открыть 5 чужих профилей','COMMON',TRUE,640),
    ('legendary_upgrade_1','PACKS','LEGENDARY_UPGRADES',1,'legendary_upgrade',1,'Своими руками','Сделать первый legendary upgrade','EPIC',TRUE,710),
    ('crafted_legendary_3','PACKS','LEGENDARY_UPGRADES',3,'legendary_upgrade',2,'Мастер апгрейда','Сделать 3 legendary upgrade','LEGENDARY',TRUE,720)
)
INSERT INTO achievement_definition (
    code, category, condition_type, history_policy, target_value, chain_group, chain_level,
    title, description, rarity, visibility, enabled, tracking_started_at, display_order
)
SELECT
    code, category, condition_type, 'FROM_ACHIEVEMENTS_LAUNCH', target_value, chain_group, chain_level,
    title, description, rarity, 'PUBLIC', enabled, CASE WHEN enabled THEN now() ELSE NULL END, display_order
FROM seed;

WITH rewards(code, reward_type, amount, reward_code, display_order) AS (
    VALUES
    ('team_submit_1','FANTIKI',10,NULL,10),
    ('team_submit_5','FANTIKI',25,NULL,10),
    ('team_submit_15','FANTIKI',50,NULL,10),
    ('team_submit_30','FANTIKI',100,NULL,10), ('team_submit_30','BADGE_STYLE',NULL,'stable_manager',20),
    ('dual_league_1','FANTIKI',25,NULL,10),
    ('dual_league_10','FANTIKI',75,NULL,10), ('dual_league_10','BADGE_STYLE',NULL,'dual_strategy',20),
    ('budget_team_1','FANTIKI',10,NULL,10),
    ('budget_team_5','FANTIKI',25,NULL,10),
    ('budget_team_15','FANTIKI',75,NULL,10),
    ('budget_team_30','FANTIKI',150,NULL,10), ('budget_team_30','PROFILE_FRAME',NULL,'budget_master',20),
    ('budget_win_1','FANTIKI',75,NULL,10), ('budget_win_1','BADGE_STYLE',NULL,'budget_winner',20),
    ('budget_top10_5','FANTIKI',50,NULL,10),
    ('series_win_1','FANTIKI',75,NULL,10), ('series_win_1','BADGE_STYLE',NULL,'series_winner',20),
    ('series_win_3','FANTIKI',100,NULL,10),
    ('series_win_10','FANTIKI',200,NULL,10), ('series_win_10','PROFILE_FRAME',NULL,'dynasty',20),
    ('top3_5','FANTIKI',75,NULL,10),
    ('top10_10','FANTIKI',75,NULL,10),
    ('top_quarter_10','FANTIKI',100,NULL,10), ('top_quarter_10','BADGE_STYLE',NULL,'steady_result',20),
    ('cards_total_10','FANTIKI',25,NULL,10),
    ('cards_total_30','FANTIKI',75,NULL,10),
    ('cards_total_100','FANTIKI',150,NULL,10), ('cards_total_100','BADGE_STYLE',NULL,'big_collection',20),
    ('first_epic','FANTIKI',25,NULL,10),
    ('first_legendary','FANTIKI',75,NULL,10), ('first_legendary','BADGE_STYLE',NULL,'legendary_collection',20),
    ('first_skin_card','FANTIKI',50,NULL,10),
    ('same_player_3_rarities','FANTIKI',100,NULL,10), ('same_player_3_rarities','BADGE_STYLE',NULL,'favorite_player',20),
    ('pack_open_1','FANTIKI',10,NULL,10),
    ('pack_open_5','FANTIKI',25,NULL,10),
    ('pack_open_15','FANTIKI',75,NULL,10),
    ('pack_open_30','FANTIKI',150,NULL,10), ('pack_open_30','BADGE_STYLE',NULL,'pack_opener',20),
    ('pack_epic_drop_1','FANTIKI',25,NULL,10),
    ('market_buy_1','FANTIKI',10,NULL,10),
    ('market_buy_5','FANTIKI',50,NULL,10),
    ('market_sell_1','FANTIKI',10,NULL,10),
    ('market_sell_5','FANTIKI',50,NULL,10),
    ('market_watch_1','FANTIKI',10,NULL,10),
    ('market_unique_counterparties_5','FANTIKI',75,NULL,10), ('market_unique_counterparties_5','BADGE_STYLE',NULL,'wide_market',20),
    ('share_profile_1','FANTIKI',10,NULL,10),
    ('share_team_1','FANTIKI',10,NULL,10),
    ('compare_open_1','FANTIKI',10,NULL,10),
    ('view_public_profile_5','BADGE_STYLE',NULL,'scout',10),
    ('legendary_upgrade_1','FANTIKI',75,NULL,10), ('legendary_upgrade_1','BADGE_STYLE',NULL,'crafted_legendary',20),
    ('crafted_legendary_3','COSMETIC_UNLOCK',NULL,'legendary_crafter',10)
)
INSERT INTO achievement_reward (achievement_id, reward_type, amount, reward_code, display_order)
SELECT d.id, r.reward_type, r.amount, r.reward_code, r.display_order
FROM rewards r
JOIN achievement_definition d ON d.code = r.code;
