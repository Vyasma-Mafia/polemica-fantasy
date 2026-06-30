INSERT INTO perk (id, name, description, bonus_points, occurrence_type, can_appear_on_random_cards)
VALUES
    (
        'sheriffCheckBlack',
        'Проверка черного',
        'Шериф получает бонус за каждого уникального черного игрока, которого проверил за игру',
        0.75,
        'MULTIPLE_PER_GAME',
        TRUE
    ),
    (
        'voteOutSheriffDay1Or2',
        'Снять шерифа',
        'Шериф покидает стол голосованием на 1 или 2 день; бонус получают все черные игроки',
        2.75,
        'ONCE_PER_GAME',
        TRUE
    )
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    bonus_points = EXCLUDED.bonus_points,
    occurrence_type = EXCLUDED.occurrence_type,
    can_appear_on_random_cards = EXCLUDED.can_appear_on_random_cards;

INSERT INTO perk_applicable_role (perk_id, role)
VALUES
    ('sheriffCheckBlack', 'SHERIFF'),
    ('voteOutSheriffDay1Or2', 'DON'),
    ('voteOutSheriffDay1Or2', 'MAFIA')
ON CONFLICT DO NOTHING;

UPDATE perk
SET can_appear_on_random_cards = FALSE
WHERE id = 'ninja';
