-- Replaces the V6 achievement catalog with detectors from polemica-achievement-service.
-- Clears junction and per-game achievement rows so FKs allow deleting old achievement ids.

DELETE FROM fantasy_team_card_game_achievement;
DELETE FROM card_template_achievement;
DELETE FROM achievement_applicable_role;
DELETE FROM achievement;

INSERT INTO achievement (id, name, description, bonus_points, occurrence_type, can_appear_on_random_cards)
VALUES
    ('sniper', 'Снайпер', 'Отстрелите шерифа в первую ночь', 1, 'ONCE_PER_GAME', TRUE),
    ('winThreeToThree', 'Баланс не нужен', 'Выиграйте за черного 3 в 3', 1, 'ONCE_PER_GAME', FALSE),
    ('findSheriff', 'Я нашел тебя!', 'Найдите шерифа за дона в первую ночь', 1, 'ONCE_PER_GAME', FALSE),
    (
        'voteForBlack',
        'Изгнать этого черныша!',
        'На красном проголосуйте за уход черного (учитывается последнее голосование на круге, при попиле рука за подъем)',
        1,
        'MULTIPLE_PER_GAME',
        TRUE
    ),
    (
        'strongCity',
        'Сильному городу шериф не нужен',
        'Выиграйте красным, когда шериф умер в первую ночь',
        1,
        'ONCE_PER_GAME',
        FALSE
    ),
    (
        'firstKickedFullGuess',
        'Цветопопадатель',
        'Будучи первым покинувшим стол красным, оставьте три верных цвета в лучший ход',
        1,
        'ONCE_PER_GAME',
        FALSE
    ),
    (
        'votingOnlyForBlack',
        'Сильная рука',
        'Будучи мирным, голосуйте только за черных (кроме попилов)',
        1,
        'ONCE_PER_GAME',
        FALSE
    ),
    (
        'winWithoutCritic',
        'Просушили',
        'Выиграйте игру за красного, не переходя в критику',
        1,
        'ONCE_PER_GAME',
        FALSE
    );

-- sniper, winThreeToThree: black
INSERT INTO achievement_applicable_role (achievement_id, role) VALUES
    ('sniper', 'DON'),
    ('sniper', 'MAFIA'),
    ('winThreeToThree', 'DON'),
    ('winThreeToThree', 'MAFIA');

-- findSheriff: don only
INSERT INTO achievement_applicable_role (achievement_id, role) VALUES
    ('findSheriff', 'DON');

-- voteForBlack, firstKickedFullGuess, winWithoutCritic: red
INSERT INTO achievement_applicable_role (achievement_id, role) VALUES
    ('voteForBlack', 'PEACE'),
    ('voteForBlack', 'SHERIFF'),
    ('firstKickedFullGuess', 'PEACE'),
    ('firstKickedFullGuess', 'SHERIFF'),
    ('winWithoutCritic', 'PEACE'),
    ('winWithoutCritic', 'SHERIFF');

-- strongCity, votingOnlyForBlack: peace
INSERT INTO achievement_applicable_role (achievement_id, role) VALUES
    ('strongCity', 'PEACE'),
    ('votingOnlyForBlack', 'PEACE');
