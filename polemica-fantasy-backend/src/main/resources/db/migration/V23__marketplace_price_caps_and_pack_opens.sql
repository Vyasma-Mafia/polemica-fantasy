-- Потолок цены листинга по редкости (маркетплейс)
INSERT INTO economy_config (key, value, description) VALUES
    ('marketplace.max_price.COMMON', '150', 'Максимальная цена листинга COMMON'),
    ('marketplace.max_price.RARE', '300', 'Максимальная цена листинга RARE'),
    ('marketplace.max_price.EPIC', '600', 'Максимальная цена листинга EPIC'),
    ('marketplace.max_price.LEGENDARY', '1500', 'Максимальная цена листинга LEGENDARY'),
    ('marketplace.min_pack_opens_before_purchase', '3', 'Минимум открытых паков до покупки на маркетплейсе');

-- Учёт открытий паков (магазин / тот же счётчик, что инкрементируется при успешном openPack)
ALTER TABLE telegram_user ADD COLUMN pack_opens_count INT NOT NULL DEFAULT 0;

-- Все существующие пользователи считаются выполнившими требование по пакам
UPDATE telegram_user SET pack_opens_count = 3;
