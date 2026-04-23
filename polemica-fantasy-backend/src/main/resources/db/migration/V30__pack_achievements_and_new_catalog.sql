-- Per-pack cap on total opens per user (0 = unlimited) and per-pack random achievement pool.

ALTER TABLE card_pack
    ADD COLUMN max_opens_per_user INT NOT NULL DEFAULT 0;

CREATE TABLE card_pack_achievement (
    card_pack_id  BIGINT        NOT NULL REFERENCES card_pack (id) ON DELETE CASCADE,
    achievement_id VARCHAR(64)  NOT NULL REFERENCES achievement (id) ON DELETE CASCADE,
    PRIMARY KEY (card_pack_id, achievement_id)
);

INSERT INTO achievement (id, name, description, bonus_points, occurrence_type, can_appear_on_random_cards)
VALUES
    ('ninja', 'Ниндзя', 'Получить ровно 0 баллов', 0.3, 'ONCE_PER_GAME', FALSE),
    ('crowned', 'Коронован', 'Получить руль', 0.2, 'ONCE_PER_GAME', FALSE),
    ('lastHeroGuess', 'Последний герой', 'Выиграть в угадайке', 0.25, 'ONCE_PER_GAME', FALSE);

INSERT INTO achievement_applicable_role (achievement_id, role)
VALUES
    ('ninja', 'PEACE'), ('ninja', 'SHERIFF'), ('ninja', 'DON'), ('ninja', 'MAFIA'),
    ('crowned', 'PEACE'), ('crowned', 'SHERIFF'), ('crowned', 'DON'), ('crowned', 'MAFIA'),
    ('lastHeroGuess', 'PEACE'), ('lastHeroGuess', 'SHERIFF'), ('lastHeroGuess', 'DON'), ('lastHeroGuess', 'MAFIA');
