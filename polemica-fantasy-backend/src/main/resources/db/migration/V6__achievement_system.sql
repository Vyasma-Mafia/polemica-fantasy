CREATE TABLE achievement (
    id                          VARCHAR(64) PRIMARY KEY,
    name                        VARCHAR(512) NOT NULL,
    description                 TEXT,
    bonus_points                DOUBLE PRECISION NOT NULL DEFAULT 1,
    occurrence_type             VARCHAR(32) NOT NULL,
    can_appear_on_random_cards  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE achievement_applicable_role (
    achievement_id  VARCHAR(64) NOT NULL REFERENCES achievement (id) ON DELETE CASCADE,
    role            VARCHAR(32) NOT NULL,
    PRIMARY KEY (achievement_id, role)
);

INSERT INTO achievement (id, name, description, bonus_points, occurrence_type, can_appear_on_random_cards)
VALUES
    ('SHERIFF_FOUND_BLACK', 'Sheriff found black', 'Sheriff checked a black player', 1, 'ONCE_PER_GAME', FALSE),
    ('DON_FOUND_SHERIFF', 'Don found sheriff', 'Don found the sheriff', 1, 'ONCE_PER_GAME', FALSE),
    ('FIRST_NIGHT_SURVIVED', 'First night survived', 'Survived the first night', 1, 'ONCE_PER_GAME', FALSE),
    ('WON_GAME', 'Won game', 'Team won the game', 1, 'ONCE_PER_GAME', FALSE),
    ('BEST_MOVE', 'Best move', 'Best move of the game', 1, 'ONCE_PER_GAME', FALSE),
    ('SURVIVED_TILL_END', 'Survived till end', 'Was in play at game end', 1, 'ONCE_PER_GAME', FALSE),
    ('VOTED_OUT_BLACK', 'Voted out black', 'Voted a black player out', 1, 'ONCE_PER_GAME', FALSE),
    ('CORRECT_GUESS', 'Correct guess', 'Sheriff guess was correct', 1, 'ONCE_PER_GAME', FALSE),
    ('NO_FOULS', 'No fouls', 'No fouls in the game', 1, 'ONCE_PER_GAME', FALSE);

INSERT INTO achievement_applicable_role (achievement_id, role)
SELECT a.id, r.role
FROM achievement a
CROSS JOIN (
    VALUES ('DON'), ('MAFIA'), ('PEACE'), ('SHERIFF')
) AS r(role);

ALTER TABLE card_template_achievement RENAME COLUMN achievement_type TO achievement_id;

ALTER TABLE card_template_achievement
    ADD CONSTRAINT fk_card_template_achievement_achievement
        FOREIGN KEY (achievement_id) REFERENCES achievement (id);

ALTER TABLE card_template_achievement ALTER COLUMN bonus_points DROP NOT NULL;
