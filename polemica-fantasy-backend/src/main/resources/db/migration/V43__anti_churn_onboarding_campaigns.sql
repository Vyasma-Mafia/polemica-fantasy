CREATE TABLE onboarding_progress (
    telegram_user_id BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    store_opened_at TIMESTAMPTZ,
    collection_viewed_at TIMESTAMPTZ,
    notifications_viewed_at TIMESTAMPTZ,
    results_viewed_at TIMESTAMPTZ,
    first_pack_opened_at TIMESTAMPTZ,
    first_team_submitted_at TIMESTAMPTZ,
    no_action_nudge_sent_at TIMESTAMPTZ,
    action_no_team_nudge_sent_at TIMESTAMPTZ,
    open_deadline_nudge_sent_at TIMESTAMPTZ,
    after_first_team_nudge_sent_at TIMESTAMPTZ,
    last_nudge_sent_at TIMESTAMPTZ,
    nudge_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE release_note (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    audience VARCHAR(64) NOT NULL DEFAULT 'ALL',
    min_app_version VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_release_note_published ON release_note(active, published_at DESC);

CREATE TABLE release_note_view (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    release_note_id BIGINT NOT NULL REFERENCES release_note(id) ON DELETE CASCADE,
    seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (telegram_user_id, release_note_id)
);

CREATE TABLE product_campaign (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    audience VARCHAR(64) NOT NULL,
    button_text VARCHAR(64),
    button_url VARCHAR(2048),
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    raw_recipient_count INT NOT NULL DEFAULT 0,
    eligible_recipient_count INT NOT NULL DEFAULT 0,
    sent_count INT NOT NULL DEFAULT 0,
    skipped_blocked_count INT NOT NULL DEFAULT 0,
    skipped_preference_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_product_campaign_created ON product_campaign(created_at DESC);

CREATE TABLE product_event (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT REFERENCES telegram_user(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    campaign_id BIGINT REFERENCES product_campaign(id) ON DELETE SET NULL,
    release_note_id BIGINT REFERENCES release_note(id) ON DELETE SET NULL,
    subject_type VARCHAR(64),
    subject_id BIGINT,
    source VARCHAR(64) NOT NULL DEFAULT 'TMA',
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_event_user_created ON product_event(telegram_user_id, created_at DESC);
CREATE INDEX idx_product_event_campaign_type ON product_event(campaign_id, event_type);
CREATE INDEX idx_product_event_release_note_type ON product_event(release_note_id, event_type);
CREATE INDEX idx_product_event_type_created ON product_event(event_type, created_at DESC);
