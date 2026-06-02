UPDATE economy_config
SET value = '20',
    description = 'Минимальная цена листинга COMMON'
WHERE key = 'marketplace.min_price.COMMON';

UPDATE economy_config
SET value = '40',
    description = 'Минимальная цена листинга RARE'
WHERE key = 'marketplace.min_price.RARE';

UPDATE economy_config
SET value = '120',
    description = 'Минимальная цена листинга EPIC'
WHERE key = 'marketplace.min_price.EPIC';

UPDATE economy_config
SET value = '15',
    description = 'Комиссия маркетплейса (%)'
WHERE key = 'marketplace.commission_percent';
