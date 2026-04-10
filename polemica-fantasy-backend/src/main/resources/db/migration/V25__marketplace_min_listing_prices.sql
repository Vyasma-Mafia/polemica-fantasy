-- Минимальная цена листинга по редкости (маркетплейс), независимо от renewal.cost.*
INSERT INTO economy_config (key, value, description) VALUES
    ('marketplace.min_price.COMMON', '30', 'Минимальная цена листинга COMMON'),
    ('marketplace.min_price.RARE', '60', 'Минимальная цена листинга RARE'),
    ('marketplace.min_price.EPIC', '120', 'Минимальная цена листинга EPIC'),
    ('marketplace.min_price.LEGENDARY', '250', 'Минимальная цена листинга LEGENDARY');
