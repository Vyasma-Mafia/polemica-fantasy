ALTER TABLE league_import_item
    ADD COLUMN current_evidence_hash VARCHAR(64),
    ADD COLUMN roster_status VARCHAR(32) NOT NULL DEFAULT 'NO_MEDIA',
    ADD COLUMN roster_draft_json JSONB,
    ADD COLUMN roster_checksum VARCHAR(64);

UPDATE league_import_item SET current_evidence_hash = current_content_hash;
ALTER TABLE league_import_item ALTER COLUMN current_evidence_hash SET NOT NULL;

ALTER TABLE league_import_item DROP CONSTRAINT league_import_item_state_check;
ALTER TABLE league_import_item ADD CONSTRAINT league_import_item_state_check CHECK (state IN (
    'READY_TO_PREVIEW','BLOCKED','PREVIEW_PENDING','AWAITING_CONFIRMATION',
    'CREATE_PENDING','CREATING','APPLIED','CONFLICT','FAILED','INCIDENT',
    'WAITING_FOR_GAMES','RECONCILING','READY_TO_FINALIZE','FINALIZE_PENDING',
    'FINALIZING','FINALIZED','PARTIAL_RESULT','BLOCKED_MISMATCH','NEEDS_REVIEW'
));
ALTER TABLE league_import_item ADD CONSTRAINT league_import_item_roster_status_check CHECK (roster_status IN (
    'NO_MEDIA','READY','NEEDS_REVIEW','OCR_FAILED','UNSUPPORTED'
));
ALTER TABLE league_import_item ADD CONSTRAINT league_import_item_roster_json_object_check CHECK (
    roster_draft_json IS NULL OR jsonb_typeof(roster_draft_json) = 'object'
);

ALTER TABLE league_import_revision
    ADD COLUMN evidence_hash VARCHAR(64),
    ADD COLUMN media_evidence JSONB;

UPDATE league_import_revision SET evidence_hash = content_hash;
ALTER TABLE league_import_revision ALTER COLUMN evidence_hash SET NOT NULL;
ALTER TABLE league_import_revision DROP CONSTRAINT league_import_revision_source_revision_unique;
ALTER TABLE league_import_revision ADD CONSTRAINT league_import_revision_source_evidence_unique
    UNIQUE (source_channel_id, source_message_id, revision, evidence_hash);
ALTER TABLE league_import_revision ADD CONSTRAINT league_import_revision_media_object_check CHECK (
    media_evidence IS NULL OR jsonb_typeof(media_evidence) = 'object'
);

ALTER TABLE league_import_action ADD COLUMN roster_checksum VARCHAR(64);
ALTER TABLE league_import_job ADD COLUMN roster_checksum VARCHAR(64);
