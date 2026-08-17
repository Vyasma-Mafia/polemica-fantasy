ALTER TABLE league_import_action
    DROP CONSTRAINT league_import_action_type_check;

ALTER TABLE league_import_action
    ADD CONSTRAINT league_import_action_type_check CHECK (action_type IN (
        'CREATE_PREVIEW','CREATE_CONFIRM','ACTIVATE','FINALIZE_PREVIEW','FINALIZE_CONFIRM'
    ));

ALTER TABLE league_import_job
    ADD COLUMN operator_message_id BIGINT;
