UPDATE perk
SET bonus_points = CASE id
    WHEN 'sniper' THEN 4.0
    WHEN 'firstKickedFullGuess' THEN 3.0
    WHEN 'winThreeToThree' THEN 1.3
    WHEN 'ninja' THEN 2.0
    ELSE bonus_points
END
WHERE id IN (
    'sniper',
    'firstKickedFullGuess',
    'winThreeToThree',
    'ninja'
);

INSERT INTO release_note (
    title,
    body,
    button_text,
    button_url,
    audience,
    active,
    published_at,
    created_at
) VALUES (
    'Баланс перков обновлен',
    $$Пересмотрели бонусы нескольких перков по статистике серий и вашим отзывам.

Снайпер теперь дает 4 очка вместо 5, Верные цвета ПУ — 3 вместо 3.25, Победа 3 в 3 — 1.3 вместо 1.5. Ниндзя, наоборот, усилили до 2 очков, чтобы редкое срабатывание ощущалось заметнее.

Изменения применяются к новым расчетам очков. Уже завершенные результаты не пересчитываются автоматически.$$,
    'Открыть справку',
    '/help',
    'ALL',
    TRUE,
    now(),
    now()
);
