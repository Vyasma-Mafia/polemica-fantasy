DO $$
DECLARE
    existing_period_id BIGINT;
BEGIN
    SELECT id
    INTO existing_period_id
    FROM periodic_rating_period
    WHERE code = 'rating_2026_07_17'
       OR (
           status IN ('DRAFT', 'OPEN', 'SETTLING')
           AND starts_at = TIMESTAMPTZ '2026-07-17 00:00:00+03'
           AND starts_at < TIMESTAMPTZ '2026-07-20 00:00:00+03'
           AND ends_at > TIMESTAMPTZ '2026-07-17 00:00:00+03'
       )
    ORDER BY CASE WHEN code = 'rating_2026_07_17' THEN 0 ELSE 1 END, id
    LIMIT 1;

    IF existing_period_id IS NULL THEN
        INSERT INTO periodic_rating_period (
            code,
            title,
            starts_at,
            ends_at,
            timezone,
            league_code,
            status,
            rules_snapshot
        ) VALUES (
            'rating_2026_07_17',
            'Первый рейтинг · 17–19 июля',
            TIMESTAMPTZ '2026-07-17 00:00:00+03',
            TIMESTAMPTZ '2026-07-20 00:00:00+03',
            'Europe/Moscow',
            'MAIN',
            'OPEN',
            jsonb_build_object(
                'version', 1,
                'timezone', 'Europe/Moscow',
                'league', 'MAIN',
                'launchPeriod', true
            )
        );
    ELSE
        UPDATE periodic_rating_period
        SET code = 'rating_2026_07_17',
            title = 'Первый рейтинг · 17–19 июля',
            starts_at = TIMESTAMPTZ '2026-07-17 00:00:00+03',
            ends_at = TIMESTAMPTZ '2026-07-20 00:00:00+03',
            timezone = 'Europe/Moscow',
            league_code = 'MAIN',
            status = 'OPEN',
            rules_snapshot = jsonb_build_object(
                'version', 1,
                'timezone', 'Europe/Moscow',
                'league', 'MAIN',
                'launchPeriod', true
            ),
            updated_at = now()
        WHERE id = existing_period_id;
    END IF;
END $$;

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
    'Первый рейтинг периодов уже начался',
    $$Теперь результаты складываются не только внутри одной серии. Мы суммируем очки ваших MAIN-колод во всех финализированных сериях периода и формируем общий рейтинг.

Первый короткий период идёт с 17 июля до 20 июля, 00:00 МСК. Топ-10 получит призовые карты с уникальными скинами: победитель создаст EPIC-карту с игроком и перками на свой выбор, места 2–5 получат RARE-карты, а места 6–10 — COMMON-карту и 50₣.

За регулярные попадания в рейтинг также появились достижения. За всю линейку можно получить до 2 500₣, включая 1 000₣ за пять попаданий в топ-10.$$,
    'Открыть рейтинг',
    '/rating',
    'ALL',
    TRUE,
    now(),
    now()
);
