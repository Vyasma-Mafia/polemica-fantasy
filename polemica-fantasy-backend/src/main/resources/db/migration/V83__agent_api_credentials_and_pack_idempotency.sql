ALTER TABLE telegram_user
    ADD COLUMN is_automated_agent BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE api_credential (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    label VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_hint VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_by VARCHAR(100) NOT NULL,
    revoked_by VARCHAR(100),
    CONSTRAINT api_credential_label_check CHECK (length(btrim(label)) > 0),
    CONSTRAINT api_credential_token_hash_check CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT api_credential_expiry_check CHECK (expires_at > created_at),
    CONSTRAINT api_credential_revocation_check CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL)
    )
);

CREATE INDEX idx_api_credential_user_created
    ON api_credential (telegram_user_id, created_at DESC);

CREATE INDEX idx_api_credential_active_user
    ON api_credential (telegram_user_id)
    WHERE revoked_at IS NULL;

CREATE TABLE api_credential_auth_audit (
    id BIGSERIAL PRIMARY KEY,
    api_credential_id BIGINT NOT NULL REFERENCES api_credential(id) ON DELETE CASCADE,
    outcome VARCHAR(16) NOT NULL,
    request_method VARCHAR(16) NOT NULL,
    request_path VARCHAR(512) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT api_credential_auth_audit_outcome_check
        CHECK (outcome IN ('SUCCESS', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX idx_api_credential_auth_audit_credential_time
    ON api_credential_auth_audit (api_credential_id, occurred_at DESC, id DESC);

CREATE TABLE pack_purchase_idempotency (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    key_hash VARCHAR(64) NOT NULL,
    canonical_request_hash VARCHAR(64) NOT NULL,
    card_pack_id BIGINT NOT NULL REFERENCES card_pack(id),
    response_kind VARCHAR(24) NOT NULL,
    balance_after BIGINT NOT NULL,
    pack_choice_id BIGINT REFERENCES user_card_pack_choice(id),
    user_card_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pack_purchase_key_hash_check CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT pack_purchase_request_hash_check CHECK (canonical_request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT pack_purchase_response_kind_check CHECK (response_kind IN ('OPENED', 'PENDING_CHOICE')),
    CONSTRAINT pack_purchase_user_card_ids_check CHECK (jsonb_typeof(user_card_ids) = 'array'),
    CONSTRAINT pack_purchase_response_json_check CHECK (jsonb_typeof(response_json) = 'object'),
    CONSTRAINT pack_purchase_idempotency_user_key_unique
        UNIQUE (telegram_user_id, key_hash)
);

CREATE INDEX idx_pack_purchase_idempotency_user_created
    ON pack_purchase_idempotency (telegram_user_id, created_at DESC);
