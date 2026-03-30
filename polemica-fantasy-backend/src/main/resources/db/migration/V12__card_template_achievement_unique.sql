-- Deduplicate junction rows before adding a uniqueness constraint (auto-generated packs could
-- insert a second link if the in-memory template.achievements bag was stale within a session).
DELETE FROM card_template_achievement cta
WHERE EXISTS (
    SELECT 1
    FROM card_template_achievement other
    WHERE other.card_template_id = cta.card_template_id
      AND other.achievement_id = cta.achievement_id
      AND other.id < cta.id
);

CREATE UNIQUE INDEX uq_card_template_achievement_template_achievement
    ON card_template_achievement (card_template_id, achievement_id);
