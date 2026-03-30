-- 1. Контракт карточки
ALTER TABLE user_card ADD COLUMN uses_remaining INT NOT NULL DEFAULT 3;
ALTER TABLE user_card ADD COLUMN times_renewed INT NOT NULL DEFAULT 0;

-- 2. Финализация серии
ALTER TABLE series ADD COLUMN finalized BOOLEAN NOT NULL DEFAULT false;

-- 3. Конфигурация экономики (key-value, редактируется через админку)
CREATE TABLE economy_config (
    key VARCHAR(64) PRIMARY KEY,
    value VARCHAR(256) NOT NULL,
    description TEXT
);

-- Seed: использования по редкостям
INSERT INTO economy_config (key, value, description) VALUES
('card.uses.COMMON', '2', 'Кол-во использований для COMMON карт'),
('card.uses.RARE', '3', 'Кол-во использований для RARE карт'),
('card.uses.EPIC', '4', 'Кол-во использований для EPIC карт'),
('card.uses.LEGENDARY', '5', 'Кол-во использований для LEGENDARY карт'),
-- Переработка
('recycle.value.COMMON', '10', 'Фантики за переработку COMMON'),
('recycle.value.RARE', '25', 'Фантики за переработку RARE'),
('recycle.value.EPIC', '60', 'Фантики за переработку EPIC'),
('recycle.value.LEGENDARY', '200', 'Фантики за переработку LEGENDARY'),
-- Продление
('renewal.cost.COMMON', '30', 'Стоимость продления COMMON'),
('renewal.cost.RARE', '60', 'Стоимость продления RARE'),
('renewal.cost.EPIC', '120', 'Стоимость продления EPIC'),
('renewal.cost.LEGENDARY', '250', 'Стоимость продления LEGENDARY'),
('renewal.max_times', '2', 'Максимум продлений на карту'),
-- Награды лидерборда
('series.reward.1', '100', 'Награда за 1 место'),
('series.reward.2', '70', 'Награда за 2 место'),
('series.reward.3', '50', 'Награда за 3 место'),
('series.reward.top10', '30', 'Награда за 4-10 место'),
('series.reward.participation', '15', 'Награда за участие (11+)');

-- 4. Установить uses_remaining для существующих карт по их редкости
UPDATE user_card uc
SET uses_remaining = CASE
    WHEN ct.rarity = 'COMMON' THEN 2
    WHEN ct.rarity = 'RARE' THEN 3
    WHEN ct.rarity = 'EPIC' THEN 4
    WHEN ct.rarity = 'LEGENDARY' THEN 5
    ELSE 3
END
FROM card_template ct
WHERE uc.card_template_id = ct.id;
