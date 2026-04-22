CREATE TABLE marketplace_pair_clearance (
    user_id_low  BIGINT    NOT NULL REFERENCES telegram_user (id),
    user_id_high BIGINT    NOT NULL REFERENCES telegram_user (id),
    created_at   TIMESTAMP NOT NULL,
    note         TEXT,
    CONSTRAINT uq_pair_clearance_pair UNIQUE (user_id_low, user_id_high),
    CONSTRAINT ck_pair_clearance_order CHECK (user_id_low < user_id_high)
);

CREATE INDEX idx_pair_clearance_low ON marketplace_pair_clearance (user_id_low);
