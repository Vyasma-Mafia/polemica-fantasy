CREATE TABLE card_skin (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE user_card
    ADD COLUMN card_skin_id BIGINT REFERENCES card_skin (id);

ALTER TABLE card_pack
    ADD COLUMN card_skin_id BIGINT REFERENCES card_skin (id);

ALTER TABLE marketplace_listing
    ADD COLUMN sold_skin_code VARCHAR(64);

COMMENT ON COLUMN user_card.card_skin_id IS 'Cosmetic skin applied to card instance; NULL means default visual';
COMMENT ON COLUMN card_pack.card_skin_id IS 'Skin assigned to cards opened from this pack';
COMMENT ON COLUMN marketplace_listing.sold_skin_code IS 'Card skin code at moment of sale; feed uses this snapshot';

INSERT INTO card_skin (code, name)
VALUES ('tournament_gold', 'Турнирная золотая');
