ALTER TABLE user_card
    ADD COLUMN crafted_by_telegram_user_id BIGINT REFERENCES telegram_user (id);

INSERT INTO economy_config (key, value, description) VALUES
    ('legendary.upgrade.cost', '400', 'Стоимость апгрейда EPIC → LEGENDARY (фантики)'),
    ('legendary.team.max_per_series', '1', 'Макс. LEGENDARY карт в фэнтези-команде на серию');
