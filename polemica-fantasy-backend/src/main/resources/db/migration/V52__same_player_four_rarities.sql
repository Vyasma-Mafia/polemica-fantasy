UPDATE achievement_definition
SET condition_type = 'SAME_PLAYER_4_RARITIES',
    description = 'Собрать активные карты одного игрока во всех 4 редкостях',
    updated_at = now()
WHERE code = 'same_player_3_rarities';

UPDATE achievement_reward ar
SET metadata = jsonb_build_object('source', 'ACTIVE_PACKS', 'rarity', 'RARE', 'count', 2, 'options', 5)
FROM achievement_definition d
WHERE ar.achievement_id = d.id
  AND d.code = 'same_player_3_rarities'
  AND ar.reward_type = 'CARD_CHOICE_ROLL';
