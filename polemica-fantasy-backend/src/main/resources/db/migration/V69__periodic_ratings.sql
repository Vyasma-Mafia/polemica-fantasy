ALTER TABLE series_game
    ALTER COLUMN played_at TYPE TIMESTAMPTZ
    USING played_at AT TIME ZONE 'UTC';

CREATE TABLE periodic_rating_period (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Moscow',
    league_code VARCHAR(32) NOT NULL DEFAULT 'MAIN',
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    finalized_at TIMESTAMPTZ,
    source_checksum VARCHAR(64),
    rules_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT periodic_rating_period_dates_check CHECK (starts_at < ends_at),
    CONSTRAINT periodic_rating_period_timezone_check CHECK (timezone = 'Europe/Moscow'),
    CONSTRAINT periodic_rating_period_league_check CHECK (league_code = 'MAIN'),
    CONSTRAINT periodic_rating_period_status_check
        CHECK (status IN ('DRAFT', 'OPEN', 'SETTLING', 'FINALIZED', 'CANCELLED'))
);

CREATE INDEX idx_periodic_rating_period_visible
    ON periodic_rating_period (status, starts_at DESC);

CREATE TABLE periodic_rating_series (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES periodic_rating_period(id) ON DELETE CASCADE,
    series_id BIGINT NOT NULL REFERENCES series(id),
    included BOOLEAN NOT NULL,
    public_reason TEXT,
    effective_at TIMESTAMPTZ,
    score_snapshot_checksum VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT periodic_rating_series_unique UNIQUE (period_id, series_id),
    CONSTRAINT periodic_rating_series_exclusion_reason_check
        CHECK (included OR length(btrim(public_reason)) > 0)
);

CREATE TABLE periodic_rating_entry (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES periodic_rating_period(id) ON DELETE CASCADE,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    rank INT NOT NULL,
    total_score NUMERIC(18,2) NOT NULL,
    series_count INT NOT NULL,
    average_score NUMERIC(18,2) NOT NULL,
    best_series_score NUMERIC(18,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT periodic_rating_entry_unique UNIQUE (period_id, telegram_user_id),
    CONSTRAINT periodic_rating_entry_rank_check CHECK (rank > 0),
    CONSTRAINT periodic_rating_entry_series_count_check CHECK (series_count > 0)
);

CREATE INDEX idx_periodic_rating_entry_rank
    ON periodic_rating_entry (period_id, rank, telegram_user_id);

CREATE TABLE periodic_rating_contribution (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES periodic_rating_entry(id) ON DELETE CASCADE,
    series_id BIGINT NOT NULL REFERENCES series(id),
    score NUMERIC(18,2) NOT NULL,
    series_rank INT NOT NULL,
    participants_count INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT periodic_rating_contribution_unique UNIQUE (entry_id, series_id),
    CONSTRAINT periodic_rating_contribution_rank_check CHECK (series_rank > 0),
    CONSTRAINT periodic_rating_contribution_participants_check CHECK (participants_count > 0)
);

CREATE TABLE periodic_rating_reward (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES periodic_rating_period(id) ON DELETE CASCADE,
    entry_id BIGINT NOT NULL UNIQUE REFERENCES periodic_rating_entry(id) ON DELETE CASCADE,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    rank INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    policy_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    selection JSONB NOT NULL DEFAULT '{}'::jsonb,
    serial VARCHAR(96) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    claim_deadline TIMESTAMPTZ,
    changes_requested_reason TEXT,
    fantiki_amount BIGINT NOT NULL DEFAULT 0,
    fantiki_granted_at TIMESTAMPTZ,
    issued_card_template_id BIGINT REFERENCES card_template(id),
    issued_user_card_id BIGINT UNIQUE REFERENCES user_card(id),
    issued_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT periodic_rating_reward_unique UNIQUE (period_id, telegram_user_id),
    CONSTRAINT periodic_rating_reward_serial_unique UNIQUE (serial),
    CONSTRAINT periodic_rating_reward_fantiki_check CHECK (fantiki_amount >= 0),
    CONSTRAINT periodic_rating_reward_status_check CHECK (status IN (
        'AVAILABLE','DRAFT','REVIEW_REQUIRED','CHANGES_REQUESTED','FULFILLED','OVERDUE','CANCELLED'
    )),
    CONSTRAINT periodic_rating_reward_policy_json_check CHECK (jsonb_typeof(policy_snapshot) = 'object'),
    CONSTRAINT periodic_rating_reward_selection_json_check CHECK (jsonb_typeof(selection) = 'object')
);

CREATE INDEX idx_periodic_rating_reward_owner_status
    ON periodic_rating_reward (telegram_user_id, status, created_at DESC);

CREATE UNIQUE INDEX idx_periodic_rating_reward_period_rank_serial
    ON periodic_rating_reward (period_id, serial);

ALTER TABLE user_card
    ADD COLUMN trophy_reward_id BIGINT UNIQUE REFERENCES periodic_rating_reward(id),
    ADD COLUMN trophy_period_id BIGINT REFERENCES periodic_rating_period(id),
    ADD COLUMN trophy_period_title VARCHAR(160),
    ADD COLUMN trophy_rank INT,
    ADD COLUMN trophy_serial VARCHAR(96),
    ADD COLUMN trophy_original_owner_telegram_id BIGINT;

ALTER TABLE user_card
    ADD CONSTRAINT user_card_trophy_fields_check CHECK (
        (trophy_reward_id IS NULL AND trophy_period_id IS NULL AND trophy_period_title IS NULL
            AND trophy_rank IS NULL AND trophy_serial IS NULL AND trophy_original_owner_telegram_id IS NULL)
        OR
        (trophy_reward_id IS NOT NULL AND trophy_period_id IS NOT NULL AND trophy_period_title IS NOT NULL
            AND trophy_rank > 0 AND trophy_serial IS NOT NULL AND trophy_original_owner_telegram_id IS NOT NULL)
    );

CREATE TABLE periodic_rating_audit_event (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES periodic_rating_period(id) ON DELETE CASCADE,
    reward_id BIGINT REFERENCES periodic_rating_reward(id) ON DELETE SET NULL,
    actor_type VARCHAR(24) NOT NULL,
    actor_id VARCHAR(128),
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT periodic_rating_audit_payload_json_check CHECK (jsonb_typeof(payload) = 'object')
);

INSERT INTO card_skin (code, name)
SELECT family || '_' || variant, title || ' · ' || initcap(variant)
FROM (VALUES
    ('rating_champion_edition', 'Champion'),
    ('rating_silver_edition', 'Silver'),
    ('rating_bronze_edition', 'Bronze'),
    ('rating_finalist_edition', 'Finalist')
) AS families(family, title)
CROSS JOIN (VALUES ('aurora'), ('crimson'), ('nocturne')) AS variants(variant)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

ALTER TABLE user_card_ownership_history
    DROP CONSTRAINT IF EXISTS ownership_acquisition_type_check;

ALTER TABLE user_card_ownership_history
    ADD CONSTRAINT ownership_acquisition_type_check
        CHECK (acquisition_type IN (
            'PACK_OPENING', 'ADMIN_GRANT', 'MARKETPLACE_PURCHASE',
            'ACHIEVEMENT_REWARD', 'CARD_MERGE', 'PERIODIC_RATING_REWARD'
        ));

ALTER TABLE fantiki_transaction
    DROP CONSTRAINT IF EXISTS fantiki_transaction_reason_check;

ALTER TABLE fantiki_transaction
    ADD CONSTRAINT fantiki_transaction_reason_check CHECK (reason IN (
        'INITIAL','ADMIN_GRANT','ADMIN_CONFISCATE','PACK_PURCHASE','SERIES_REWARD',
        'CARD_RECYCLE','CARD_RENEWAL','LEGENDARY_UPGRADE','EASTER_EGG_BONUS',
        'MARKETPLACE_PURCHASE','MARKETPLACE_SALE','MARKETPLACE_SANCTION_FINE',
        'MARKETPLACE_COMPLAINT_REWARD','ACHIEVEMENT_REWARD','ADMIN_PAIR_BAN',
        'ADMIN_CARD_CONFISCATE','PERIODIC_RATING_REWARD'
    ));
