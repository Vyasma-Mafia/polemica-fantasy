CREATE TABLE IF NOT EXISTS computations (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES runs(id),
    source_snapshot_id INTEGER NOT NULL REFERENCES snapshots(id),
    tool_name TEXT NOT NULL,
    engine_version TEXT NOT NULL,
    dataset_schema_version TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    request_path TEXT NOT NULL,
    manifest_hash TEXT NOT NULL,
    manifest_path TEXT NOT NULL,
    input_hash TEXT NOT NULL,
    input_path TEXT NOT NULL,
    result_hash TEXT,
    result_path TEXT,
    verification_hash TEXT,
    verification_path TEXT,
    state TEXT NOT NULL CHECK (
        state IN ('PLANNED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'INTERRUPTED')
    ),
    error_code TEXT,
    input_count INTEGER NOT NULL CHECK (input_count >= 0),
    result_count INTEGER CHECK (result_count IS NULL OR result_count >= 0),
    output_bytes INTEGER CHECK (output_bytes IS NULL OR output_bytes >= 0),
    claim_count INTEGER NOT NULL DEFAULT 0 CHECK (claim_count >= 0),
    planned_at TEXT NOT NULL,
    started_at TEXT,
    finished_at TEXT,
    duration_ms INTEGER CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CHECK ((result_hash IS NULL) = (result_path IS NULL)),
    CHECK ((verification_hash IS NULL) = (verification_path IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_computations_run_state
    ON computations(run_id, state, planned_at);
CREATE INDEX IF NOT EXISTS idx_computations_source_snapshot
    ON computations(source_snapshot_id, state);

CREATE TABLE IF NOT EXISTS computation_inputs (
    computation_id TEXT NOT NULL REFERENCES computations(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    source TEXT NOT NULL,
    object_id TEXT NOT NULL,
    source_version TEXT,
    payload_hash TEXT NOT NULL,
    PRIMARY KEY(computation_id, ordinal),
    UNIQUE(computation_id, source, object_id, source_version, payload_hash)
);

CREATE TABLE IF NOT EXISTS decision_computations (
    decision_id INTEGER NOT NULL REFERENCES decisions(id) ON DELETE CASCADE,
    computation_id TEXT NOT NULL REFERENCES computations(id),
    PRIMARY KEY(decision_id, computation_id)
);
