CREATE TABLE fantasy_player_alias (
    id BIGSERIAL PRIMARY KEY,
    fantasy_player_id BIGINT NOT NULL REFERENCES fantasy_player(id) ON DELETE CASCADE,
    polemica_user_id BIGINT NOT NULL,
    primary_alias BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fantasy_player_alias_polemica_positive CHECK (polemica_user_id > 0)
);

INSERT INTO fantasy_player_alias (fantasy_player_id, polemica_user_id, primary_alias)
SELECT id, polemica_user_id, TRUE
FROM fantasy_player;

CREATE UNIQUE INDEX uk_fantasy_player_alias_polemica_user
    ON fantasy_player_alias (polemica_user_id);

CREATE UNIQUE INDEX uk_fantasy_player_alias_primary
    ON fantasy_player_alias (fantasy_player_id)
    WHERE primary_alias = TRUE;

CREATE INDEX idx_fantasy_player_alias_player
    ON fantasy_player_alias (fantasy_player_id);

CREATE TABLE fantasy_player_merge_audit (
    id BIGSERIAL PRIMARY KEY,
    source_fantasy_player_id BIGINT NOT NULL,
    target_fantasy_player_id BIGINT NOT NULL REFERENCES fantasy_player(id),
    reason TEXT NOT NULL,
    source_polemica_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    target_polemica_user_ids_before JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fantasy_player_merge_audit_distinct_players
        CHECK (source_fantasy_player_id <> target_fantasy_player_id),
    CONSTRAINT fantasy_player_merge_audit_reason_not_blank
        CHECK (length(trim(reason)) > 0),
    CONSTRAINT fantasy_player_merge_audit_source_aliases_json
        CHECK (jsonb_typeof(source_polemica_user_ids) = 'array'),
    CONSTRAINT fantasy_player_merge_audit_target_aliases_json
        CHECK (jsonb_typeof(target_polemica_user_ids_before) = 'array')
);

CREATE INDEX idx_fantasy_player_merge_audit_target
    ON fantasy_player_merge_audit (target_fantasy_player_id, created_at DESC);
