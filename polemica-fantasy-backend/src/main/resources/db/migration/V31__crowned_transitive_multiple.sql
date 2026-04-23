-- Транзитивные «рули»: несколько независимых цепочек от разных красных к одному игроку.
UPDATE achievement
SET occurrence_type = 'MULTIPLE_PER_GAME'
WHERE id = 'crowned';
