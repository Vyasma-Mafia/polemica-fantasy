-- Возврат сверхдохода с продаж на маркетплейсе по цене выше текущего потолка
-- marketplace.max_price для редкости карты (после введения лимита в конфиге / API).
-- Для каждой продажи: фактически получено продавцом = price - floor(price * pct / 100),
-- легитимный максимум = max_p - floor(max_p * pct / 100), списываем разницу.

WITH params AS (
    SELECT
        (SELECT value::bigint FROM economy_config WHERE key = 'marketplace.commission_percent') AS pct,
        (SELECT value::bigint FROM economy_config WHERE key = 'marketplace.max_price.COMMON') AS max_common,
        (SELECT value::bigint FROM economy_config WHERE key = 'marketplace.max_price.RARE') AS max_rare,
        (SELECT value::bigint FROM economy_config WHERE key = 'marketplace.max_price.EPIC') AS max_epic,
        (SELECT value::bigint FROM economy_config WHERE key = 'marketplace.max_price.LEGENDARY') AS max_legendary
),
listing_excess AS (
    SELECT
        ml.seller_id,
        GREATEST(
            0,
            (ml.price - ml.price * p.pct / 100)
            - (mp.max_p - mp.max_p * p.pct / 100)
        ) AS excess
    FROM marketplace_listing ml
    INNER JOIN user_card uc ON ml.user_card_id = uc.id
    INNER JOIN card_template ct ON uc.card_template_id = ct.id
    CROSS JOIN params p
    INNER JOIN LATERAL (
        SELECT CASE ct.rarity
            WHEN 'COMMON' THEN p.max_common
            WHEN 'RARE' THEN p.max_rare
            WHEN 'EPIC' THEN p.max_epic
            WHEN 'LEGENDARY' THEN p.max_legendary
        END AS max_p
    ) mp ON mp.max_p IS NOT NULL
    WHERE ml.status = 'SOLD'
      AND ml.price > mp.max_p
),
seller_claw AS (
    SELECT seller_id, SUM(excess)::bigint AS total_excess
    FROM listing_excess
    GROUP BY seller_id
    HAVING SUM(excess) > 0
),
to_apply AS (
    SELECT
        tu.id,
        LEAST(tu.fantiki, sc.total_excess) AS deduct
    FROM telegram_user tu
    INNER JOIN seller_claw sc ON sc.seller_id = tu.id
),
applied AS (
    UPDATE telegram_user tu
    SET fantiki = tu.fantiki - ta.deduct
    FROM to_apply ta
    WHERE tu.id = ta.id
      AND ta.deduct > 0
    RETURNING tu.id, ta.deduct
)
INSERT INTO fantiki_transaction (telegram_user_id, amount, reason)
SELECT id, -deduct, 'ADMIN_CONFISCATE'
FROM applied;
