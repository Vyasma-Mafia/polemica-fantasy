ALTER TABLE telegram_user
    ADD COLUMN fantiki BIGINT NOT NULL DEFAULT 1000;

CREATE TABLE fantiki_transaction (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user (id),
    amount              BIGINT NOT NULL,
    reason              VARCHAR(64) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fantiki_transaction_user ON fantiki_transaction (telegram_user_id);
CREATE INDEX idx_fantiki_transaction_created ON fantiki_transaction (telegram_user_id, created_at);
