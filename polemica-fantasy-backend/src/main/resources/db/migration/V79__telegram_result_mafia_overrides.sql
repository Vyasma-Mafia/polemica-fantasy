CREATE TABLE league_import_result_mafia_override (
    id BIGSERIAL PRIMARY KEY,
    series_id BIGINT NOT NULL REFERENCES series(id),
    import_item_id BIGINT NOT NULL REFERENCES league_import_item(id),
    game_number INT NOT NULL,
    original_mafia_line VARCHAR(512) NOT NULL,
    corrected_mafia_line VARCHAR(512) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    admin_actor VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT league_import_result_mafia_override_unique UNIQUE (import_item_id, game_number),
    CONSTRAINT league_import_result_mafia_override_game_check CHECK (game_number > 0)
);

CREATE INDEX league_import_result_mafia_override_series_idx
    ON league_import_result_mafia_override(series_id, game_number);
