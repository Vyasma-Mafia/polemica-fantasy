-- Telegram users
CREATE TABLE telegram_user (
    id              BIGSERIAL PRIMARY KEY,
    telegram_id     BIGINT NOT NULL UNIQUE,
    username        VARCHAR(255),
    first_name      VARCHAR(255),
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_telegram_user_telegram_id ON telegram_user (telegram_id);

-- Tournaments
CREATE TABLE tournament (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(512) NOT NULL,
    description     TEXT,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL
);

-- Players registered in a tournament
CREATE TABLE tournament_player (
    id                  BIGSERIAL PRIMARY KEY,
    tournament_id       BIGINT NOT NULL REFERENCES tournament (id),
    polemica_user_id    BIGINT NOT NULL,
    nickname            VARCHAR(512) NOT NULL,
    photo_url           VARCHAR(2048)
);

CREATE INDEX idx_tournament_player_tournament ON tournament_player (tournament_id);
CREATE INDEX idx_tournament_player_polemica ON tournament_player (polemica_user_id);

-- Series within a tournament
CREATE TABLE series (
    id              BIGSERIAL PRIMARY KEY,
    tournament_id   BIGINT NOT NULL REFERENCES tournament (id),
    name            VARCHAR(512) NOT NULL,
    name_prefix     VARCHAR(512) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    starts_at       TIMESTAMP NOT NULL,
    team_deadline   TIMESTAMP NOT NULL
);

CREATE INDEX idx_series_tournament ON series (tournament_id);

-- Players participating in a series
CREATE TABLE series_player (
    id                      BIGSERIAL PRIMARY KEY,
    series_id               BIGINT NOT NULL REFERENCES series (id),
    tournament_player_id    BIGINT NOT NULL REFERENCES tournament_player (id),
    UNIQUE (series_id, tournament_player_id)
);

CREATE INDEX idx_series_player_series ON series_player (series_id);
CREATE INDEX idx_series_player_tournament_player ON series_player (tournament_player_id);

-- Games linked to a series (Polemica game cache)
CREATE TABLE series_game (
    id                  BIGSERIAL PRIMARY KEY,
    series_id           BIGINT NOT NULL REFERENCES series (id),
    polemica_game_id    BIGINT NOT NULL,
    game_name           VARCHAR(1024) NOT NULL,
    game_data_cache     JSONB,
    scored              BOOLEAN NOT NULL DEFAULT FALSE,
    played_at           TIMESTAMP
);

CREATE INDEX idx_series_game_series ON series_game (series_id);
CREATE INDEX idx_series_game_polemica ON series_game (polemica_game_id);

-- Card template (definition)
CREATE TABLE card_template (
    id                      BIGSERIAL PRIMARY KEY,
    tournament_player_id    BIGINT NOT NULL REFERENCES tournament_player (id),
    rarity                  VARCHAR(32) NOT NULL,
    image_url               VARCHAR(2048),
    description             TEXT
);

CREATE INDEX idx_card_template_tournament_player ON card_template (tournament_player_id);

CREATE TABLE card_template_achievement (
    id                  BIGSERIAL PRIMARY KEY,
    card_template_id    BIGINT NOT NULL REFERENCES card_template (id) ON DELETE CASCADE,
    achievement_type    VARCHAR(64) NOT NULL,
    bonus_points        DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_card_template_achievement_template ON card_template_achievement (card_template_id);

-- User-owned card instances
CREATE TABLE user_card (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user (id),
    card_template_id    BIGINT NOT NULL REFERENCES card_template (id),
    acquired_at         TIMESTAMP NOT NULL
);

CREATE INDEX idx_user_card_user ON user_card (telegram_user_id);
CREATE INDEX idx_user_card_template ON user_card (card_template_id);

-- Card packs
CREATE TABLE card_pack (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(512) NOT NULL,
    tournament_id   BIGINT NOT NULL REFERENCES tournament (id),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_card_pack_tournament ON card_pack (tournament_id);

CREATE TABLE card_pack_rarity_config (
    id              BIGSERIAL PRIMARY KEY,
    card_pack_id    BIGINT NOT NULL REFERENCES card_pack (id) ON DELETE CASCADE,
    rarity          VARCHAR(32) NOT NULL,
    probability     DOUBLE PRECISION NOT NULL,
    cards_count     INTEGER NOT NULL
);

CREATE INDEX idx_card_pack_rarity_config_pack ON card_pack_rarity_config (card_pack_id);

-- Fantasy team (3 cards per series)
CREATE TABLE fantasy_team (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user (id),
    series_id           BIGINT NOT NULL REFERENCES series (id),
    total_score         DOUBLE PRECISION,
    submitted_at        TIMESTAMP NOT NULL,
    UNIQUE (telegram_user_id, series_id)
);

CREATE INDEX idx_fantasy_team_user ON fantasy_team (telegram_user_id);
CREATE INDEX idx_fantasy_team_series ON fantasy_team (series_id);

CREATE TABLE fantasy_team_card (
    id                  BIGSERIAL PRIMARY KEY,
    fantasy_team_id     BIGINT NOT NULL REFERENCES fantasy_team (id) ON DELETE CASCADE,
    user_card_id        BIGINT NOT NULL REFERENCES user_card (id),
    slot                INTEGER NOT NULL,
    score               DOUBLE PRECISION,
    UNIQUE (fantasy_team_id, slot),
    UNIQUE (fantasy_team_id, user_card_id),
    CONSTRAINT fantasy_team_card_slot_range CHECK (slot IN (1, 2, 3))
);

CREATE INDEX idx_fantasy_team_card_team ON fantasy_team_card (fantasy_team_id);
CREATE INDEX idx_fantasy_team_card_user_card ON fantasy_team_card (user_card_id);
