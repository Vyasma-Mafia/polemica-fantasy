ALTER TABLE series ADD COLUMN expected_game_count INT;
ALTER TABLE series ADD COLUMN last_synced_selector_checksum VARCHAR(64);
ALTER TABLE series ADD COLUMN last_scored_selector_checksum VARCHAR(64);
ALTER TABLE series ADD CONSTRAINT series_expected_game_count_check
    CHECK (expected_game_count IS NULL OR expected_game_count BETWEEN 1 AND 32);

ALTER TABLE series_game ADD COLUMN points_status VARCHAR(24) NOT NULL DEFAULT 'NOT_SCORED';
ALTER TABLE series_game ADD COLUMN scoring_input_checksum VARCHAR(64);
ALTER TABLE series_game ADD COLUMN scoring_context_checksum VARCHAR(64);
ALTER TABLE series_game ADD COLUMN scored_at TIMESTAMPTZ;
ALTER TABLE series_game ADD COLUMN scoring_error VARCHAR(512);
ALTER TABLE series_game ADD CONSTRAINT series_game_points_status_check CHECK (
    points_status IN ('NOT_SCORED','COMPLETE','PARTIAL','LOAD_FAILED','CACHE_INVALID')
);

CREATE TABLE league_import_item (
    id BIGSERIAL PRIMARY KEY,
    source_channel_id BIGINT NOT NULL,
    source_message_id BIGINT NOT NULL,
    item_index INT NOT NULL DEFAULT 0,
    current_revision INT NOT NULL,
    current_source_version VARCHAR(64) NOT NULL,
    current_content_hash VARCHAR(64) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    league_code VARCHAR(8) NOT NULL,
    state VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    draft_json JSONB,
    draft_checksum VARCHAR(64),
    blocked_reason VARCHAR(512),
    target_series_id BIGINT REFERENCES series(id),
    policy_generation VARCHAR(64),
    readiness_status VARCHAR(32),
    readiness_checksum VARCHAR(64),
    stable_poll_count INT NOT NULL DEFAULT 0,
    ready_since TIMESTAMPTZ,
    last_stable_observation_at TIMESTAMPTZ,
    last_reconciled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT league_import_item_source_unique UNIQUE (source_channel_id, source_message_id, item_index),
    CONSTRAINT league_import_item_index_check CHECK (item_index >= 0),
    CONSTRAINT league_import_item_state_check CHECK (state IN (
        'READY_TO_PREVIEW','BLOCKED','PREVIEW_PENDING','AWAITING_CONFIRMATION',
        'CREATE_PENDING','CREATING','APPLIED','CONFLICT','FAILED','INCIDENT',
        'WAITING_FOR_GAMES','RECONCILING','READY_TO_FINALIZE','FINALIZE_PENDING',
        'FINALIZING','FINALIZED','PARTIAL_RESULT','BLOCKED_MISMATCH'
    ))
);

CREATE TABLE league_import_revision (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES league_import_item(id) ON DELETE CASCADE,
    source_channel_id BIGINT NOT NULL,
    source_message_id BIGINT NOT NULL,
    revision INT NOT NULL,
    source_version VARCHAR(64) NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL,
    edited_at TIMESTAMPTZ,
    content_hash VARCHAR(64) NOT NULL,
    raw_text TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT league_import_revision_source_revision_unique
        UNIQUE (source_channel_id, source_message_id, revision, content_hash),
    CONSTRAINT league_import_revision_number_check CHECK (revision > 0),
    CONSTRAINT league_import_revision_text_size_check CHECK (octet_length(raw_text) <= 16384)
);

CREATE UNIQUE INDEX league_import_revision_current_unique
    ON league_import_revision (item_id) WHERE is_current;

CREATE TABLE league_import_delivery (
    id BIGSERIAL PRIMARY KEY,
    key_id VARCHAR(64) NOT NULL,
    delivery_id UUID NOT NULL UNIQUE,
    request_timestamp TIMESTAMPTZ NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    item_id BIGINT REFERENCES league_import_item(id),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE league_import_action (
    id UUID PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES league_import_item(id) ON DELETE CASCADE,
    item_version BIGINT NOT NULL,
    source_revision INT NOT NULL,
    draft_checksum VARCHAR(64) NOT NULL,
    policy_generation VARCHAR(64) NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    bound_actor_telegram_id BIGINT,
    actor_telegram_id BIGINT,
    actor_username VARCHAR(128),
    callback_update_id BIGINT UNIQUE,
    callback_query_id VARCHAR(128) UNIQUE,
    operator_chat_id BIGINT NOT NULL,
    operator_message_id BIGINT,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    processing_started_at TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT league_import_action_type_check CHECK (action_type IN (
        'CREATE_PREVIEW','CREATE_CONFIRM','FINALIZE_PREVIEW','FINALIZE_CONFIRM'
    )),
    CONSTRAINT league_import_action_status_check CHECK (status IN (
        'NOTIFY_PENDING','OFFERED','CALLBACK_RECEIVED','CREATE_PENDING','CREATING',
        'FINALIZE_PENDING','FINALIZING',
        'APPLIED','REJECTED','STALE','EXPIRED','FAILED','CANCELLED'
    ))
);

CREATE INDEX league_import_action_work_idx
    ON league_import_action (status, created_at);

CREATE TABLE league_import_callback_inbox (
    id BIGSERIAL PRIMARY KEY,
    callback_update_id BIGINT NOT NULL UNIQUE,
    callback_query_id VARCHAR(128) NOT NULL UNIQUE,
    action_id UUID,
    token_hash VARCHAR(64) NOT NULL,
    operator_chat_id BIGINT,
    operator_message_id BIGINT,
    actor_telegram_id BIGINT,
    actor_username VARCHAR(128),
    actor_is_bot BOOLEAN NOT NULL,
    ordinary_message BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_until TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    answer_text VARCHAR(256),
    last_error VARCHAR(512),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT league_import_callback_inbox_status_check
        CHECK (status IN ('PENDING','PROCESSED','FAILED'))
);

CREATE INDEX league_import_callback_inbox_work_idx
    ON league_import_callback_inbox (status, available_at, id);

CREATE TABLE league_import_job (
    id UUID PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES league_import_item(id) ON DELETE CASCADE,
    item_version BIGINT NOT NULL,
    source_revision INT NOT NULL,
    evidence_checksum VARCHAR(64) NOT NULL,
    policy_generation VARCHAR(64) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    target_series_id BIGINT REFERENCES series(id),
    readiness_checksum VARCHAR(64),
    actor_telegram_id BIGINT,
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ,
    pending_notification_outbox_id BIGINT,
    notification_delivered_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    lease_token UUID,
    completed_at TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT league_import_job_unique
        UNIQUE (item_id,item_version,source_revision,evidence_checksum,policy_generation,operation),
    CONSTRAINT league_import_job_operation_check CHECK (operation IN ('CREATE','RECONCILE','FINALIZE')),
    CONSTRAINT league_import_job_status_check CHECK (status IN (
        'PENDING','RUNNING','WAITING','APPLIED','BLOCKED','FAILED','CANCELLED'
    ))
);

CREATE INDEX league_import_job_work_idx ON league_import_job(status,available_at,id);

CREATE TABLE series_external_post_link (
    id BIGSERIAL PRIMARY KEY,
    series_id BIGINT NOT NULL REFERENCES series(id),
    import_item_id BIGINT NOT NULL REFERENCES league_import_item(id),
    source_channel_id BIGINT NOT NULL,
    source_message_id BIGINT NOT NULL,
    link_role VARCHAR(24) NOT NULL,
    source_revision INT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT series_external_post_link_source_unique
        UNIQUE (source_channel_id, source_message_id, link_role),
    CONSTRAINT series_external_post_link_item_unique UNIQUE (import_item_id, link_role),
    CONSTRAINT series_external_post_link_role_check CHECK (link_role IN ('ANNOUNCEMENT','RESULT'))
);

CREATE TABLE league_import_audit_event (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES league_import_item(id),
    action_id UUID REFERENCES league_import_action(id),
    actor_telegram_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT league_import_audit_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE TABLE league_import_operator_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(160) NOT NULL UNIQUE,
    item_id BIGINT NOT NULL REFERENCES league_import_item(id),
    action_id UUID REFERENCES league_import_action(id),
    event_type VARCHAR(48) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_until TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    telegram_message_id BIGINT,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT league_import_operator_outbox_status_check
        CHECK (status IN ('PENDING','DELIVERED','FAILED','SUPERSEDED')),
    CONSTRAINT league_import_operator_outbox_payload_object_check CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX league_import_operator_outbox_work_idx
    ON league_import_operator_outbox (status, available_at, id);

ALTER TABLE league_import_job ADD CONSTRAINT league_import_job_pending_notification_fk
    FOREIGN KEY (pending_notification_outbox_id) REFERENCES league_import_operator_outbox(id);
