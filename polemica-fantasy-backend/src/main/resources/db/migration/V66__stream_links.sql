CREATE TABLE tournament_stream_link (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournament(id) ON DELETE CASCADE,
    label VARCHAR(128),
    url VARCHAR(2048) NOT NULL,
    display_order INTEGER NOT NULL
);

CREATE INDEX idx_tournament_stream_link_tournament
    ON tournament_stream_link(tournament_id, display_order, id);

CREATE TABLE series_stream_link (
    id BIGSERIAL PRIMARY KEY,
    series_id BIGINT NOT NULL REFERENCES series(id) ON DELETE CASCADE,
    label VARCHAR(128),
    url VARCHAR(2048) NOT NULL,
    display_order INTEGER NOT NULL
);

CREATE INDEX idx_series_stream_link_series
    ON series_stream_link(series_id, display_order, id);
