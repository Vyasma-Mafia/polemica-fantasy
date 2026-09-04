CREATE TABLE IF NOT EXISTS research_collections (
    collection_id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES runs(id),
    state TEXT NOT NULL CHECK (state IN ('COLLECTING', 'SEALED')),
    created_at TEXT NOT NULL,
    as_of TEXT,
    completeness TEXT NOT NULL DEFAULT 'COMPLETE' CHECK (completeness IN ('COMPLETE', 'PARTIAL')),
    error_count INTEGER NOT NULL DEFAULT 0 CHECK (error_count >= 0),
    evidence_snapshot_id INTEGER UNIQUE REFERENCES snapshots(id),
    sealed_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_research_collections_run ON research_collections(run_id, state);

CREATE TABLE IF NOT EXISTS research_collection_records (
    collection_id TEXT NOT NULL REFERENCES research_collections(collection_id),
    source TEXT NOT NULL,
    object_id TEXT NOT NULL,
    source_version TEXT,
    payload_hash TEXT NOT NULL,
    blob_path TEXT NOT NULL,
    first_seen_at TEXT NOT NULL,
    fetched_at TEXT NOT NULL,
    parser_version TEXT NOT NULL,
    correction_index INTEGER NOT NULL CHECK (correction_index >= 1),
    PRIMARY KEY(collection_id, payload_hash)
);

CREATE TABLE IF NOT EXISTS snapshot_trust (
    snapshot_id INTEGER PRIMARY KEY REFERENCES snapshots(id),
    trust_kind TEXT NOT NULL CHECK (trust_kind IN ('TRUSTED_RESEARCH', 'NON_EVIDENCE')),
    collection_id TEXT UNIQUE REFERENCES research_collections(collection_id),
    attested_at TEXT NOT NULL,
    CHECK (
      (trust_kind = 'TRUSTED_RESEARCH' AND collection_id IS NOT NULL) OR
      (trust_kind = 'NON_EVIDENCE' AND collection_id IS NULL)
    )
);

CREATE TABLE IF NOT EXISTS act_authorizations (
    decision_id INTEGER PRIMARY KEY REFERENCES decisions(id),
    operation_id TEXT NOT NULL UNIQUE,
    run_id TEXT NOT NULL REFERENCES runs(id),
    tool_name TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    authorized_at TEXT NOT NULL
);
