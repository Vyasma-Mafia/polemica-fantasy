CREATE TABLE notification_preference (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    category         VARCHAR(32) NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_notif_pref_user_category UNIQUE (telegram_user_id, category)
);

CREATE TABLE tournament_subscription (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    tournament_id    BIGINT NOT NULL REFERENCES tournament(id),
    CONSTRAINT uk_tournament_sub UNIQUE (telegram_user_id, tournament_id)
);

ALTER TABLE telegram_user
    ADD COLUMN bot_blocked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE deadline_reminder (
    id              BIGSERIAL PRIMARY KEY,
    series_id       BIGINT NOT NULL REFERENCES series(id),
    remind_at       TIMESTAMPTZ NOT NULL,
    sent            BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at         TIMESTAMPTZ,
    recipient_count INT,
    CONSTRAINT uk_deadline_reminder_series UNIQUE (series_id)
);

CREATE INDEX idx_deadline_reminder_pending ON deadline_reminder (remind_at)
    WHERE sent = FALSE;

CREATE TABLE marketplace_watch_filter (
    id                BIGSERIAL PRIMARY KEY,
    telegram_user_id  BIGINT NOT NULL REFERENCES telegram_user(id),
    fantasy_player_id BIGINT REFERENCES fantasy_player(id),
    tournament_id     BIGINT REFERENCES tournament(id),
    rarity            VARCHAR(32),
    max_price         BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_marketplace_watch UNIQUE (
        telegram_user_id, fantasy_player_id, tournament_id, rarity, max_price
    )
);

CREATE INDEX idx_marketplace_watch_player ON marketplace_watch_filter (fantasy_player_id)
    WHERE fantasy_player_id IS NOT NULL;

CREATE TABLE marketplace_watch_pending (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    listing_id       BIGINT NOT NULL REFERENCES marketplace_listing(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mw_pending_user ON marketplace_watch_pending (telegram_user_id);
