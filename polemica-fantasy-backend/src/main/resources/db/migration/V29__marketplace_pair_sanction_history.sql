CREATE TABLE marketplace_pair_sanction_history (
    id                BIGSERIAL   PRIMARY KEY,
    created_at        TIMESTAMP   NOT NULL,
    user_id_low       BIGINT      NOT NULL REFERENCES telegram_user (id),
    user_id_high      BIGINT      NOT NULL REFERENCES telegram_user (id),
    reason            TEXT        NOT NULL,
    fantiki_taken_low  BIGINT     NOT NULL,
    fantiki_taken_high BIGINT     NOT NULL,
    cards_count_low   INT         NOT NULL,
    cards_count_high  INT         NOT NULL,
    CONSTRAINT ck_pair_sanction_history_order CHECK (user_id_low < user_id_high)
);

CREATE INDEX idx_pair_sanction_history_created ON marketplace_pair_sanction_history (created_at DESC);
