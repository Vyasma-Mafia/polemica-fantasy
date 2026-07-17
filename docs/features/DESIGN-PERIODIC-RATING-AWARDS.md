# Периодический рейтинг пользователей и уникальные награды

> **Статус:** Expanded discovery draft  
> **Дата:** 2026-07-17  
> **Scope:** backend + TMA + admin + notifications + economy

## 1. Суть фичи

Каждая серия уже имеет лидерборд: результат пользователя — `FantasyTeam.totalScore`, то есть сумма баллов всех карт выставленной им фэнтези-команды в этой серии и лиге.

Новая фича создаёт рейтинг за календарный период:

```text
period_total_score(user) = Σ fantasy_team.total_score
```

Суммируются результаты всех подходящих серий периода. Победители определяются по итоговой сумме и получают редкие награды. Например, первое место получает право создать EPIC-карту: выбрать существующего `fantasy_player` и два допустимых перка.

Это не рейтинг отдельных `user_card`. Состав карт важен только как источник уже рассчитанного `FantasyTeam.totalScore` конкретной серии.

## 2. Связь с текущим продуктом

В TMA уже есть почти такая же агрегация для общего лидерборда турнира:

1. запрашиваются лидерборды серий выбранной лиги;
2. строки группируются по `telegram_user`;
3. `LeaderboardEntry.totalScore` суммируется;
4. пользователи сортируются по общей сумме.

Текущая реализация находится в `aggregateTournamentLeaderboards` и работает на клиенте внутри одного турнира. Периодический рейтинг должен перенести этот принцип на backend и агрегировать серии из всех турниров за заданный период, чтобы результат можно было зафиксировать, проверить и использовать для выдачи наград.

## 3. Рекомендуемый MVP

- Cadence: один период раз в две недели.
- Границы: понедельник 00:00 — второе воскресенье 24:00 МСК.
- В зачёт входят только финализированные серии.
- Одна серия целиком относится к одному периоду.
- Результат пользователя в серии — существующий `FantasyTeam.totalScore`.
- В MVP рейтинг считается по `MAIN`.
- Отсутствие команды в серии даёт не `0`, а просто отсутствие слагаемого.
- Итог ранжируется по сумме очков; рядом показывается число сыгранных серий.
- После границы периода админ проверяет состав серий и фиксирует immutable snapshot.
- Победители получают reward entitlements; выдача уникальной карты идемпотентна.

Biweekly предпочтительнее weekly на старте: больше серий в выборке, меньше случайности и вдвое меньше операционной нагрузки по уникальным наградам.

## 4. Какие серии входят в период

### 4.1 Рекомендуемая дата серии

Спортивно корректная дата серии:

```text
series_effective_at = MAX(series_game.played_at)
```

Вся серия относится к периоду, в который попала её последняя реальная игра. Даже если игры серии пересекли календарную границу, `FantasyTeam.totalScore` не делится: серия остаётся одной зачётной единицей.

Почему не `series.finalized_at`: задержка админского finalize не должна переносить спортивный результат в другую неделю. `finalized_at` используется как признак готовности результата, а не как дата зачёта.

Если у финализированной серии нет игр с `played_at`, она становится blocking anomaly для admin review и не включается молча по fallback-дате.

### 4.2 Eligibility серии

Серия входит в зачёт, если:

- `series.finalized = true`;
- есть `series.finalized_at`;
- определён `series_effective_at`;
- серия не исключена админом с обязательной причиной;
- в ней включена зачётная лига;
- у команд есть финальный `total_score`.

Серия, чья последняя игра попадает в период, но которая ещё не финализирована, блокирует закрытие периода. Админ либо ждёт finalize, либо явно исключает серию с публичной причиной.

### 4.3 Турниры

По умолчанию учитываются серии всех внутренних `tournament`, а не одного соревнования Polemica. При необходимости period config может содержать allowlist/denylist турниров, но MVP лучше запускать с правилом «все официальные серии».

## 5. Лиги

После появления MAIN + BUDGET у пользователя может быть до двух `FantasyTeam` в одной серии. Складывать их вместе в один рейтинг нельзя без явного продуктового решения: это даст двойную возможность заработать очки в одной серии.

Рекомендуемый MVP:

- основной периодический рейтинг использует только `MAIN`;
- `league_code = MAIN` фиксируется в rules snapshot периода;
- позднее можно добавить отдельную номинацию «Бюджетный чемпион» по `BUDGET`;
- нельзя выбирать лучший результат пользователя между MAIN/BUDGET задним числом.

Альтернатива — два независимых рейтинга и две сетки наград за один период. Это логичнее, чем суммировать лиги, но увеличивает эмиссию и операционную нагрузку.

## 6. Формула рейтинга

Для каждого пользователя:

```text
series_score = fantasy_team.total_score
period_total_score = ROUND(SUM(series_score), 2)
series_count = COUNT(DISTINCT series_id)
average_series_score = period_total_score / series_count
```

Основная сортировка — только `period_total_score DESC`, потому что пользователь просит именно сумму рейтингов серий.

В UI дополнительно показываются:

- число зачтённых серий;
- средний результат за серию;
- лучший результат одной серии;
- список серий и место пользователя внутри каждой серии.

### 6.1 Отрицательные и нулевые результаты

Официальный `FantasyTeam.totalScore` учитывается как есть, включая `0` или отрицательное значение. Исключать неудачные серии нельзя: иначе пользователь мог бы улучшать итог только выбором положительных результатов.

### 6.2 Пользователь без команды

Если пользователь не участвовал в серии, строка отсутствует. Это не штраф и не отдельный ноль. Следовательно, total-score рейтинг одновременно награждает активность и качество результата.

### 6.3 Ничьи

Рекомендуемая политика — одинаковое отображаемое значение получает одинаковое место:

```text
125.50 → 1
125.50 → 1
120.25 → 3
```

Все tied winners получают награду соответствующего места. Это честнее скрытого `id ASC`; admin preview показывает фактическое количество наград до финализации.

Если экономика не допускает shared prizes, tie-break должен быть публичным и заранее зафиксированным в правилах периода. Возможный порядок: больше серий, лучший single-series score, более раннее достижение финальной суммы.

## 7. Lifecycle периода

| Status | Значение |
|---|---|
| `DRAFT` | Настройка дат, лиги и наград. |
| `OPEN` | Период идёт; рейтинг предварительный. |
| `SETTLING` | Период закончился; ждём финализацию относящихся серий. |
| `FINALIZED` | Рейтинг и breakdown зафиксированы, созданы награды. |
| `CANCELLED` | Период отменён с причиной. |

Рекомендуемый grace window — 24–36 часов. Переход в `SETTLING` автоматический; `FINALIZED` в MVP выполняется админом.

Перед финализацией admin preview показывает:

- все включённые серии и их effective date;
- незавершённые серии-blockers;
- число пользователей и команд;
- top-N;
- ничьи в призовой зоне;
- reward liability;
- исключённые серии и причины;
- расхождения с предыдущим preview.

После `FINALIZED` изменения исходных `FantasyTeam.totalScore` не должны молча менять победителей. До выдачи наград допускается audited revision; после выдачи — только compensating reward, без автоматического отзыва уникальной карты.

## 8. Награды топ-10

### 8.1 Рекомендуемая сетка: trophy card каждому участнику топ-10

| Место | Награда |
|---|---|
| 1 | **Champion EPIC**: любой существующий `fantasy_player`, 2 выбранных перка, champion skin. |
| 2 | **Silver RARE**: любой существующий `fantasy_player`, 1 выбранный перк, silver skin. |
| 3 | **Bronze RARE**: любой игрок, 1 перк из трёх server-fixed вариантов, bronze skin. |
| 4–5 | **Finalist RARE**: выбор одной из трёх заранее сгенерированных комбинаций player + perk, без reroll. |
| 6–10 | **Finalist COMMON**: любой существующий `fantasy_player`, без перков, finalist skin + 50₣. |

Каждый победитель получает действительно коллекционный экземпляр, но сила награды убывает по tier. Сетка проста для объяснения и реализуется одним универсальным card-constructor, где entitlement задаёт rarity, число выбираемых перков и skin.

Все top-10 дополнительно получают непередаваемый профильный трофей/placement history. Он остаётся у победителя, даже если trophy card позже продана.

Почему это предпочтительнее фантиков/паков для мест 4–10:

- top-10 ощущается как достижение, а не небольшая экономическая компенсация;
- COMMON с выбранным любимым игроком и редким skin имеет коллекционную ценность без сильной эмиссии power;
- все призёры проходят один узнаваемый reward flow;
- provenance остаётся ценной даже после продажи карты.

Оценка biweekly-эмиссии за период при отсутствии ties:

- 1 EPIC, 4 RARE, 5 COMMON;
- стандартный card value: примерно `100 + 4×50 + 5×25 = 425`;
- стандартные contracts, без extra uses;
- 26 периодов в год: до 260 trophy cards и около 11 050 card value без учёта ties.

Weekly cadence удваивает эмиссию. Если рейтинг станет еженедельным, разумный fallback — карты для top-3, а места 4–10 получают сезонную trophy card только один раз за месяц/сезон и небольшую текущую награду.

### 8.2 Альтернативные модели

| Модель | Плюсы | Минусы |
|---|---|---|
| Top-3 cards, 4–10 fantiki | Минимальная эмиссия | Top-10 не ощущает уникальности |
| Все top-10 получают одинаковую RARE | Очень желанная награда | Слишком плоская сетка и 260 RARE/year |
| Trophy skin для существующей карты | Почти нет card emission | Ломает текущий принцип: skin принадлежит созданному `user_card`, а не является отдельным unlock/token |

Рекомендация — tiered EPIC/RARE/COMMON edition cards.

### 8.3 Правила конструктора

Общие правила:

- только существующий `fantasy_player`;
- выбранные перки различны и входят в server-provided prize pool;
- системные `bonus_points`, без override;
- обычный rarity modifier;
- обычный contract соответствующей rarity;
- `times_renewed = 0`;
- никаких дополнительных uses или скрытого бонуса к очкам;
- выбранный player/perk/skin snapshot фиксируется при создании entitlement;
- карта создаётся/reuse template и выдаётся одной идемпотентной транзакцией.

Допустимость перков должна приходить с reward entitlement. `can_appear_on_random_cards` можно использовать как стартовый фильтр, но лучше иметь отдельный prize allowlist.

Для COMMON шаг выбора перков отсутствует; места 4–5 сразу выбирают готовую комбинацию. UI строится из reward policy, а не из отдельных hardcoded flow для каждого места:

```text
rarity
playerSelectionMode = ANY_EXISTING | FIXED_OPTIONS
perkSelectionMode = NONE | FREE_CHOICE | FIXED_PERK_OPTIONS | BUNDLED_OPTIONS
perkSelectionCount = 0 | 1 | 2
optionCount
perkPoolSnapshot
skinCode
skinVariantOptions
editionTier
fulfillmentMode = AUTO_ISSUE | REVIEW
```

### 8.4 Trophy skin system

Не нужно создавать новый CSS-skin для каждого победителя или периода. Рекомендуется четыре стабильных tier skin:

| Skin code | Места | Визуальный язык |
|---|---:|---|
| `rating_champion_*` | 1 | Золотая/иридисцентная рамка, crown mark, мягкий animated sweep |
| `rating_silver_*` | 2 | Холодное серебро/аврора, silver podium mark |
| `rating_bronze_*` | 3 | Тёплая медь, bronze podium mark |
| `rating_finalist_*` | 4–10 | Сдержанная синевато-фиолетовая рамка, finalist mark |

Существующий `card_skin` и CSS variable pattern из achievement skins подходит для базового визуала. Но статического `skinCode` недостаточно для реальной уникальности: нужен доступный для чтения dynamic edition badge, а не только CSS `content`.

На экземпляре показываются:

- `#1`, `#2` ... `#10`;
- короткий period code или диапазон дат;
- serial, например `S03-001`;
- первоначальный победитель в provenance;
- skin name, например «Корона сезона 2026».

Можно менять общую trophy theme раз в сезон или год, сохраняя четыре tier. Это даёт коллекционный повод участвовать снова без ручного арта для каждой карты.

Rank tier пользователю не выбирается, но внутри него доступны три заранее подготовленных accent variants: `Aurora`, `Crimson`, `Nocturne`. Поскольку существующие карточные поверхности получают только `skinCode`, pilot использует 12 конкретных `card_skin` code (`4 tier families × 3 accents`), а не четыре кода плюс недоступный рендерам JSON. Металл, crown/laurel/star и exact rank marker остаются неизменными.

### 8.5 Уникальность экземпляра

Одинаковые `fantasy_player + rarity + perks` могут использовать один `card_template`. Уникальным должен быть выданный `user_card`:

- edition title;
- serial;
- период и место;
- первоначальный победитель;
- trophy `card_skin`;
- immutable provenance и link на reward entitlement.

Пример:

```text
Champion Edition · Победитель рейтинга 8–21 июля 2026 · #001
```

Карта может использоваться, продлеваться, проходить in-place Legendary upgrade и продаваться по обычным правилам; provenance и skin следуют за тем же экземпляром. В pilot trophy card нельзя использовать как материал card merge, потому что merge создаёт новый `user_card` и иначе потеряет reward provenance. Победитель дополнительно сохраняет непередаваемый профильный badge/placement history, поэтому продажа trophy card не стирает спортивное достижение.

### 8.6 Ties, repeat winners и claim deadline

- Если tied rank попадает в top-10, все участники получают reward tier этого rank; следующее место пропускается.
- Один пользователь получает один entitlement за период.
- Repeat winner получает обычную награду снова: искусственный cooldown искажает спортивный результат.
- Reward не сгорает. Обычный срок выбора — 30 дней; после него entitlement становится `OVERDUE`, но остаётся доступным ещё минимум 60 дней и может быть продлён админом.
- Для pilot `OVERDUE` остаётся доступным бессрочно; extension меняет reminders/UI, но не право на награду.
- Reminders: после выдачи, через 7 дней, за 3 дня до deadline и при переходе в `OVERDUE`.
- Для мест 6–10 fallback легко материализовать как COMMON текущего top-scoring/favorite player; для EPIC/RARE и bundled RARE fallback без выбора пользователя не должен применяться автоматически.

## 9. TMA UX

Рекомендуется добавить в `/rating` две вкладки:

- `Рейтинг периода`;
- `Общий рейтинг` — существующий рейтинг капитала.

Отдельный reward hub:

```text
/rating/rewards
/rating/rewards/{rewardId}/create
```

Входы: Telegram deep link, action-card завершённого периода, Home banner и badge на разделе «Рейтинг». Trophy entitlement не смешивается с `/achievements`, потому что имеет отдельный lifecycle, deadline и историю editions. При этом спортивные milestones по финализированным местам отображаются в обычном каталоге достижений.

### 9.1 Достижения рейтинга периодов

Категория `/achievements` «Рейтинг периодов» содержит шесть milestones:

| Код | Условие | Визуальная редкость | Награда |
|---|---|---|---:|
| `periodic_rating_period_1` | первый итоговый рейтинг | COMMON | 50₣ |
| `periodic_rating_period_5` | 5 итоговых рейтингов | RARE | 250₣ |
| `periodic_rating_top10_1` | первый top-10 | RARE | 200₣ |
| `periodic_rating_top10_5` | 5 попаданий в top-10 | EPIC | 1 000₣ |
| `periodic_rating_podium_1` | первый rank `<= 3` | EPIC | 400₣ |
| `periodic_rating_champion_1` | первый rank `= 1` | LEGENDARY | 600₣ |

Редкость в этой таблице относится только к оформлению достижения. Карты и скины через achievement claim не выдаются; суммарная одноразовая эмиссия всей линейки — 2 500₣ на пользователя. Пять попаданий в top-10 дают эквивалент пяти стандартных паков по 200₣, что отражает сложность результата при 100–200 участниках. Прогресс считается только по immutable `periodic_rating_entry` периодов в `FINALIZED` со значением `finalized_at >= tracking_started_at`. Competition ties сохраняются: все пользователи с одинаковым rank получают соответствующий milestone.

Экран периода:

- даты и МСК;
- provisional/settling/final status;
- countdown или finalized time;
- награды;
- формула «сумма результатов серий»;
- top-N и pinned row пользователя;
- `totalScore`, `seriesCount`, average;
- переход в breakdown.

Breakdown пользователя:

| Серия | Турнир | Лига | Результат | Место в серии |
|---|---|---|---:|---:|

Это ключевая прозрачность фичи: итоговая сумма должна буквально раскладываться на существующие результаты серий.

### 9.2 End-to-end reward journey

1. **Результаты зафиксированы.** Telegram notification, Home CTA и баннер на экране периода: «Вы заняли N место — создайте призовую карту».
2. **Reward overview.** Пользователь видит точные права своего tier: rarity, число перков, skin, contract, deadline и правила продажи.
3. **Выбор игрока.** Searchable каталог `fantasy_player`; recent/favorite players показываются до ввода запроса, но источник истины — server eligibility. Рядом предупреждение: карту можно поставить только в серию, где игрок входит в roster.
4. **Выбор перков.** Только для EPIC/RARE. Показываются bonus, occurrence type и applicable roles; sticky counter «1 из 2». COMMON пропускает шаг.
5. **Оформление.** Tier skin заработан местом; пользователь выбирает один из разрешённых accent variants. Большой live preview показывает место, период и зарезервированный serial. Оформление косметическое и не влияет на силу карты.
6. **Подтверждение.** Player, rarity, perks, uses, skin, marketplace/upgrade rules и необратимость выбора после issue.
7. **Issue.** Целевой UX — мгновенная server-validated выдача. Для первых pilot periods можно включить `REVIEW` и показать «Выбор отправлен на проверку».
8. **Reveal.** Полноэкранный trophy reveal с CTA «Открыть в коллекции» и optional share.

Черновик хранится на backend после каждого шага. Telegram close/device switch не сбрасывает выбор.

Каталог игроков не должен загружаться целиком в TMA. Поиск выполняется на backend по nickname и aliases с debounce около 250 мс и пагинацией; пустой запрос возвращает небольшой блок recent/favorite players, а уже выбранный игрок всегда остаётся видимым в черновике. Empty/error states не сбрасывают сделанный выбор.

Предпросмотр использует композицию реальной collection card: portrait с aspect ratio `3:4`, rarity frame, value badge слева, contract uses справа, нижний gradient cap с nickname, rarity modifier и perk chips. Trophy edition добавляет поверх этой структуры dynamic badge `tier + exact rank`, serial и период, а не заменяет существующий язык карты отдельным абстрактным дизайном.

### 9.2 Reward statuses

| Status | UI |
|---|---|
| `AVAILABLE` | «Создать призовую карту» |
| `DRAFT` | «Продолжить создание» |
| `REVIEW_REQUIRED` | Legacy/exception flow: «Выбор на проверке» |
| `CHANGES_REQUESTED` | Причина и CTA «Исправить» |
| `ISSUING` | Только клиентский disabled progress во время синхронного approve; в БД отдельный промежуточный status не хранится. |
| `FULFILLED` | Карта в коллекции, CTA на `?cardId=` |
| `OVERDUE` | Награда сохранена; связаться/продолжить после extension |
| `CANCELLED` | Видимая admin reason |

### 9.3 UX safeguards

- Если player/perk перестал быть eligible, черновик сохраняется, проблемный выбор подсвечивается.
- Submit с устаревшей reward version возвращает актуальные options без потери остальных шагов.
- Нельзя получить вторую карту повторным tap/retry.
- Финальное подтверждение пользователя атомарно создаёт карту и переводит entitlement в `FULFILLED`; повторный tap/retry возвращает уже выданную карту.
- `REVIEW_REQUIRED` сохраняется только для ранее отправленных заявок и исключительных случаев; после ручного approve редактирование недоступно.
- Skin preview должен использовать тот же reusable card component/class mapping, что collection, team, pack reveal и marketplace.
- Dynamic edition badge должен быть реальным DOM-текстом для accessibility, а не только CSS pseudo-element.
- На thumbnails анимация выключена; glow/sweep включается только в reveal/detail и учитывает `prefers-reduced-motion`.

## 10. Admin UX

Отдельная страница `Periodic ratings`:

### Periods

- создание следующего периода из предыдущего;
- даты, timezone, league code;
- reward tiers;
- included/excluded tournaments and series;
- recalculation preview;
- blockers;
- top-N и series breakdown;
- finalize/cancel/revision с обязательной причиной.

### Rewards

- период, место, пользователь, итог и число серий;
- выбранный игрок/перки;
- status/deadline;
- `Request changes`;
- `Approve & issue`;
- `Extend deadline`;
- resulting `card_template_id` / `user_card_id`;
- audit trail.

Выдача через общие Card Templates/User Tools не подходит: она неатомарна и не связывает карту с победой.

## 11. Модель данных

### 11.1 `periodic_rating_period`

- `id`, `code`, `title`;
- `starts_at`, `ends_at`, `timezone`;
- `league_code`;
- `status`, `rules_version`, `config_snapshot`;
- `settling_started_at`, `finalized_at`, `finalized_by`, `finalize_reason`;
- `cancelled_at`, `cancel_reason`;
- `source_checksum`, optimistic `version`;
- unique `(starts_at, ends_at, league_code)`.

### 11.2 `periodic_rating_series`

Фиксирует series membership периода:

- `period_id`, `series_id`;
- `series_effective_at`;
- `included`, `reason`;
- tournament/series snapshots;
- unique `(period_id, series_id)`.

### 11.3 `periodic_rating_entry`

Immutable итог пользователя:

- `period_id`, `telegram_user_id`;
- `rank`, `tie_group`;
- `total_score NUMERIC`, `series_count`;
- `average_score`, `best_series_score`;
- public user snapshot;
- unique `(period_id, telegram_user_id)`.

### 11.4 `periodic_rating_contribution`

Одно слагаемое суммы:

- `entry_id`, `period_id`;
- `telegram_user_id`, `series_id`, `fantasy_team_id`;
- `league_code`;
- `score NUMERIC`;
- series/tournament snapshot;
- place and participants count inside series;
- unique `(period_id, telegram_user_id, series_id, league_code)`.

### 11.5 `periodic_rating_reward`

- `period_id`, `entry_id`, `telegram_user_id`, `place`, `reward_type`;
- reward policy snapshot: rarity, player/perk selection modes, option count, perk count/pool, skin variants, edition tier, fulfillment mode;
- status and deadline;
- draft/selection snapshot, reserved serial and version;
- admin/user comments;
- `issued_card_template_id`, `issued_user_card_id`;
- unique idempotency key and optimistic `version`.

### 11.6 Audit/provenance

- append-only `periodic_rating_audit_event`;
- `CardAcquisitionType.PERIODIC_RATING_REWARD`;
- unique nullable reward/provenance link на выданном `user_card` или в общей provenance table;
- edition metadata: tier, rank, period code, serial, original winner, issued_at;
- `user_card.card_skin_id` с одним из trophy skins.

Implementation migration — **V69**. Production read-only проверка 2026-07-17 подтвердила: DB timezone `Etc/UTC`, `series_game.played_at` — `timestamp without time zone`, а последние значения совпадают с `game_data_cache.started`. V69 безопасно переводит колонку в `TIMESTAMPTZ` через `played_at AT TIME ZONE 'UTC'`, чтобы сравнение с границами периода не зависело от session timezone.

## 12. Расчёт на backend

Концептуальный запрос для MAIN:

```sql
SELECT
  ft.telegram_user_id,
  SUM(ft.total_score) AS period_total_score,
  COUNT(DISTINCT ft.series_id) AS series_count,
  MAX(ft.total_score) AS best_series_score
FROM fantasy_team ft
JOIN series s ON s.id = ft.series_id
JOIN series_league sl ON sl.id = ft.series_league_id
JOIN league l ON l.id = sl.league_id
JOIN periodic_rating_series prs
  ON prs.series_id = s.id
 AND prs.period_id = :periodId
 AND prs.included = TRUE
WHERE s.finalized = TRUE
  AND l.code = :leagueCode
  AND ft.total_score IS NOT NULL
GROUP BY ft.telegram_user_id
ORDER BY period_total_score DESC;
```

Важно:

- не суммировать `fantasy_team_card_game_score` — серия уже имеет готовый итог команды;
- не дедуплицировать отдельные карты;
- не складывать MAIN и BUDGET в одну строку;
- contribution snapshot хранит каждый использованный `FantasyTeam.totalScore`;
- ranking/display используют одинаковое округление.

## 13. API impact

### User API

```text
GET  /api/v1/periodic-ratings/current
GET  /api/v1/periodic-ratings/periods
GET  /api/v1/periodic-ratings/periods/{id}/leaderboard
GET  /api/v1/periodic-ratings/periods/{id}/users/{telegramId}
GET  /api/v1/periodic-ratings/rewards/pending
GET  /api/v1/periodic-ratings/rewards/{id}
PUT  /api/v1/periodic-ratings/rewards/{id}/draft
POST /api/v1/periodic-ratings/rewards/{id}/submit
```

### Admin API

```text
GET  /api/v1/admin/periodic-ratings/periods
POST /api/v1/admin/periodic-ratings/periods
PUT  /api/v1/admin/periodic-ratings/periods/{id}
POST /api/v1/admin/periodic-ratings/periods/{id}/preview
POST /api/v1/admin/periodic-ratings/periods/{id}/finalize
POST /api/v1/admin/periodic-ratings/periods/{id}/cancel
PUT  /api/v1/admin/periodic-ratings/periods/{id}/series/{seriesId}
GET  /api/v1/admin/periodic-ratings/rewards
POST /api/v1/admin/periodic-ratings/rewards/{id}/request-changes
POST /api/v1/admin/periodic-ratings/rewards/{id}/approve-and-issue
```

## 14. Notifications и analytics

Новая категория `PERIODIC_RATING`:

- итоги периода и место;
- CTA выбора уникальной награды;
- reminder;
- changes requested;
- карта выдана.

Награда всегда остаётся доступна в TMA, даже если бот заблокирован или пользователь отключил маркетинговые уведомления.

Метрики:

- participants and series per period;
- доля пользователей с 1/2/3+ сериями;
- distribution `totalScore` и `averageScore`;
- repeat winners;
- MAIN/BUDGET participation overlap;
- leaderboard views;
- reward selection/fulfillment time;
- выбранные players/perks;
- дальнейшее использование наградных карт.

## 15. Риски

1. **Преимущество активности.** Пользователь с большим числом серий получает больше возможностей набрать сумму. Это соответствует заданной формуле; `seriesCount` показывается явно.
2. **Разное число доступных серий.** Не все турниры одинаково доступны аудитории. При необходимости V2 вводит minimum/average nominations, но не меняет основной total-score зачёт.
3. **MAIN + BUDGET.** Их нельзя неявно складывать; MVP фиксирует MAIN.
4. **Late finalize.** Период ждёт спортивно относящуюся к нему серию, а не переносит её по `finalized_at`.
5. **Manual rescoring finalized series.** После snapshot это создаёт data drift; нужен audited correction flow или запрет manual rescoring finalized series.
6. **Ничьи.** Shared prizes увеличивают эмиссию; liability видна до finalize.
7. **Сильная кастомная EPIC.** Curated perk pool и обычный contract не дают награде обходить экономику.
8. **Повторные победители.** В MVP не вводить искусственный cooldown; измерять концентрацию.

## 16. Acceptance criteria

- [ ] Итог пользователя равен сумме его `FantasyTeam.totalScore` по зачётным сериям.
- [ ] Каждая серия даёт пользователю не более одного слагаемого в рамках выбранной лиги.
- [ ] MAIN и BUDGET не складываются неявно.
- [ ] Серия целиком относится к одному периоду по заранее определённой effective date.
- [ ] Учитываются только финализированные серии и non-null totals.
- [ ] Нулевая/отрицательная серия не исключается.
- [ ] Breakdown пользователя воспроизводит итоговую сумму.
- [ ] Provisional результат отличается визуально от final snapshot.
- [ ] Незавершённая относящаяся серия блокирует finalize либо исключается с причиной.
- [ ] Ничьи обрабатываются публично и воспроизводимо.
- [ ] Reward entitlement создаётся ровно один раз.
- [ ] Каждый участник top-10 получает entitlement своего reward tier, включая shared ties на границе top-10.
- [ ] Все trophy cards имеют обычные rarity/contract/perk rules и не получают скрытого score/uses bonus.
- [ ] Trophy skin, rank, period, serial и original winner сохраняются в постоянной provenance экземпляра.
- [ ] Черновик выбора восстанавливается после закрытия TMA или смены устройства.
- [ ] Повторный submit/issue не создаёт вторую карту.
- [ ] Late rescoring не меняет final leaderboard молча.
- [ ] Admin finalize, exclusion, correction и issue имеют audit reason.

## 17. Решения для подтверждения

1. Период — 7 или 14 дней? Рекомендация: 14 для pilot.
2. Дата серии — последняя `played_at` или `finalized_at`? Рекомендация: последняя игра.
3. MVP считает только MAIN или нужны два независимых рейтинга MAIN/BUDGET? Рекомендация: MAIN.
4. Ничья делит место и награду? Рекомендация: да.
5. Подтверждаем ли ladder `1 EPIC + 4 RARE + 5 COMMON`, или для weekly cadence оставляем карты только top-3? Рекомендация: полный top-10 ladder при biweekly.
6. Можно выбрать любого существующего `fantasy_player` или только участвовавшего в сериях периода? Рекомендация: любой существующий eligible player.
7. Нужен ли выбор из 2–3 accent variants внутри заработанного skin tier уже в pilot? Рекомендация: да, если варианты подготовлены без отдельного арта на каждого победителя.
8. Требуется admin approval выбора перков перед выдачей? Текущий pilot использует review как страховку свободного выбора. Продуктовая рекомендация после UX-проверки: валидную комбинацию выдавать сразу после финального подтверждения игроком, а admin review оставить только для исключений и спорных кейсов.

## 18. Verification plan

Backend tests:

- сумма двух/трёх `FantasyTeam.totalScore` одного пользователя;
- пользователи, участвовавшие в разном числе серий;
- MAIN учитывается, BUDGET не попадает в MAIN period;
- zero/negative/null totals;
- серия на границе `[starts_at, ends_at)`;
- серия, игры которой пересекли период, учитывается целиком по последней игре;
- unfinished series blocker;
- explicit exclusion with reason;
- ties and shared rewards;
- immutable contribution snapshot;
- concurrent finalize/issue idempotency;
- late rescoring drift;
- reward selection validation and atomic card issue.

После реализации:

```bash
cd polemica-fantasy-backend && ./gradlew compileKotlin compileTestKotlin
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.*PeriodicRating*"
cd polemica-fantasy-webapp && npm run build
cd polemica-fantasy-admin && npm run build
./scripts/codex-check.sh quick
```

## 19. Implementation gate decisions

Перед coding зафиксированы решения plan/QA review:

- `OPEN` / `SETTLING` показывают live provisional aggregation; `FINALIZED` читает только immutable entry/contribution snapshot.
- Пользовательский `GET /periodic-ratings/periods` отдаёт все видимые периоды от новых к старым; TMA позволяет переключиться на прошлый `FINALIZED` период и увидеть его snapshot leaderboard и личные contributions.
- Итог каждого пользователя: `BigDecimal.valueOf(totalScore)`, сумма без промежуточного округления, затем `setScale(2, HALF_UP)`; rank/ties считаются по этому же значению.
- Границы периода — `[startsAt, endsAt)`, `Europe/Moscow`; пересекающиеся активные MAIN periods запрещены.
- Finalize принимает checksum preview, лочит period row и одной транзакцией создаёт series/entry/contribution/reward snapshots, 50₣ для мест 6–10, audit и status `FINALIZED`.
- Начисление 50₣ происходит при finalize независимо от выбора карты, имеет `FantikiTransactionReason.PERIODIC_RATING_REWARD` и idempotent marker на entitlement.
- Конкретные три perk options для места 3 и три player+perk bundles для мест 4–5 замораживаются при создании entitlement и не reroll-ятся.
- Reward happy path: `AVAILABLE → DRAFT → FULFILLED`; финальное подтверждение пользователя является необратимым действием выдачи. Legacy/exception path остаётся `REVIEW_REQUIRED → CHANGES_REQUESTED → REVIEW_REQUIRED → FULFILLED`; пользователь редактирует только `AVAILABLE` / `DRAFT` / `CHANGES_REQUESTED` / `OVERDUE`.
- Submit лочит reward row и атомарно создаёт/reuse template, `user_card`, ownership history, provenance link и `FULFILLED`; повторный submit возвращает существующий результат. Ручной approve использует тот же issuing path только для legacy `REVIEW_REQUIRED`.
- Stale reward version возвращает `409` с актуальным DTO.
- Player search reward-scoped, paginated, по canonical nickname и numeric Polemica IDs. Исторические текстовые aliases и recent/favorites отложены.
- Trophy cards запрещены как card merge inputs в pilot; in-place Legendary upgrade разрешён.
- Flyway **V70** добавляет шесть достижений «Рейтинг периодов» и индекс `periodic_rating_entry(telegram_user_id, period_id)`. После finalize best-effort async-событие пересчитывает только четыре condition types этой линейки для полного набора участников и изолирует ошибку одного пользователя; catalog/claim сохраняют lazy self-healing при пропущенном событии.
- Flyway **V72** открывает первый короткий MAIN-период `[2026-07-17 00:00, 2026-07-20 00:00)` в `Europe/Moscow` и публикует release note с CTA `/rating`. Если окружение уже содержит открытый период с тем же стартом, миграция нормализует его вместо создания пересекающегося дубля.
- Rollout по feature flag: сначала schema + admin preview + provisional TMA, затем atomic finalize, затем reward builder/auto-issue/notifications.
