CREATE TABLE IF NOT EXISTS runs (
    id TEXT PRIMARY KEY,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),
    model TEXT NOT NULL,
    prompt_hash TEXT NOT NULL,
    tools_hash TEXT NOT NULL,
    config_hash TEXT NOT NULL,
    summary_hash TEXT,
    summary_path TEXT
);

CREATE TABLE IF NOT EXISTS snapshots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT NOT NULL REFERENCES runs(id),
    kind TEXT NOT NULL,
    as_of TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    source TEXT NOT NULL,
    sample_size INTEGER,
    completeness TEXT NOT NULL CHECK (completeness IN ('COMPLETE', 'PARTIAL')),
    payload_hash TEXT NOT NULL,
    payload_path TEXT NOT NULL,
    sealed_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_snapshots_run_kind ON snapshots(run_id, kind, generated_at);

CREATE TABLE IF NOT EXISTS raw_payload_refs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL,
    source_key TEXT NOT NULL,
    source_version TEXT,
    observed_at TEXT NOT NULL,
    as_of TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    payload_path TEXT NOT NULL,
    UNIQUE(source, source_key, source_version, payload_hash)
);

CREATE TABLE IF NOT EXISTS raw_polemica_games (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL,
    game_id INTEGER NOT NULL,
    source_version TEXT,
    fetched_at TEXT NOT NULL,
    finished_at TEXT,
    payload_hash TEXT NOT NULL,
    payload_path TEXT NOT NULL,
    UNIQUE(source, game_id, source_version, payload_hash)
);

CREATE TABLE IF NOT EXISTS derived_features (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    subject_type TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    feature_version TEXT NOT NULL,
    as_of TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    payload_path TEXT NOT NULL,
    UNIQUE(subject_type, subject_id, feature_version, as_of)
);

CREATE TABLE IF NOT EXISTS strategy_versions (
    version TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    prompt_hash TEXT NOT NULL,
    config_hash TEXT NOT NULL,
    config_path TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS decisions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT NOT NULL REFERENCES runs(id),
    decision_type TEXT NOT NULL,
    subject_type TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    decided_at TEXT NOT NULL,
    as_of TEXT NOT NULL,
    strategy_version TEXT REFERENCES strategy_versions(version),
    alternatives_hash TEXT NOT NULL,
    alternatives_path TEXT NOT NULL,
    choice_hash TEXT NOT NULL,
    choice_path TEXT NOT NULL,
    rationale TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_decisions_subject ON decisions(subject_type, subject_id, decided_at);

CREATE TABLE IF NOT EXISTS decision_snapshots (
    decision_id INTEGER NOT NULL REFERENCES decisions(id) ON DELETE CASCADE,
    snapshot_id INTEGER NOT NULL REFERENCES snapshots(id),
    PRIMARY KEY(decision_id, snapshot_id)
);

CREATE TABLE IF NOT EXISTS operation_intents (
    operation_id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES runs(id),
    decision_id INTEGER REFERENCES decisions(id),
    kind TEXT NOT NULL,
    target_id TEXT NOT NULL,
    is_economic INTEGER NOT NULL CHECK (is_economic IN (0, 1)),
    request_hash TEXT NOT NULL,
    request_path TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PLANNED', 'SENT', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    planned_at TEXT NOT NULL,
    sent_at TEXT,
    resolved_at TEXT,
    result_hash TEXT,
    result_path TEXT,
    verification_hash TEXT,
    verification_path TEXT
);
CREATE INDEX IF NOT EXISTS idx_intents_unresolved ON operation_intents(is_economic, state, planned_at);

CREATE TABLE IF NOT EXISTS tool_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT NOT NULL REFERENCES runs(id),
    operation_id TEXT REFERENCES operation_intents(operation_id),
    sequence_no INTEGER NOT NULL,
    server TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    request_path TEXT NOT NULL,
    response_hash TEXT,
    response_path TEXT,
    error_code TEXT,
    UNIQUE(run_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS outcomes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    decision_id INTEGER NOT NULL REFERENCES decisions(id),
    observed_at TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    payload_path TEXT NOT NULL,
    score REAL
);

CREATE TABLE IF NOT EXISTS interventions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT REFERENCES runs(id),
    occurred_at TEXT NOT NULL,
    reason TEXT NOT NULL,
    details_hash TEXT NOT NULL,
    details_path TEXT NOT NULL
);
