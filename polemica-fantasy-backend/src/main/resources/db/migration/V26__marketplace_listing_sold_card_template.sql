-- Snapshot of card_template at sale time (rarity + achievements preview in feed); survives EPIC→LEGENDARY upgrade on user_card.
ALTER TABLE marketplace_listing
    ADD COLUMN sold_card_template_id BIGINT REFERENCES card_template (id);

COMMENT ON COLUMN marketplace_listing.sold_card_template_id IS 'card_template at moment of sale; feed uses this instead of current user_card.card_template';
