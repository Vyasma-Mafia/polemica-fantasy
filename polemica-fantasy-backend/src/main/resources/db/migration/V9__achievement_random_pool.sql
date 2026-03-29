-- At least two achievements must allow random attachment for EPIC pack slots.
UPDATE achievement SET can_appear_on_random_cards = true
WHERE id IN ('WON_GAME', 'BEST_MOVE', 'SURVIVED_TILL_END');
