# Идеи: «Скаутская лаборатория» и «Импорт анонса»

> Статус: discovery draft. Документ описывает продуктовую концепцию и возможную
> архитектуру, но не является контрактом реализации.

## Зачем объединять эти идеи

Функции решают две разные части одного сценария:

1. Организатор получает анонс серии в Telegram или в виде изображения и быстро,
   но безопасно создаёт серию в Fantasy.
2. Пользователь видит готовую серию и использует данные, чтобы осознаннее собрать
   команду до дедлайна.

Для конкурса это можно показать как единую историю: **неструктурированный анонс
реального события превращается в проверенный объект Fantasy, а накопленные данные
помогают пользователю принять объяснимое решение**.

При этом функции стоит реализовывать независимо. Импорт — административный
инструмент, а лаборатория — пользовательская функция и главный демонстрационный
сценарий.

| Идея | Для кого | Основная ценность | Технический акцент |
|------|----------|-------------------|--------------------|
| Скаутская лаборатория | Игрок Fantasy | Сравнить игроков и карты до выбора состава | Агрегации, объяснимая аналитика, кеширование |
| Импорт анонса | Администратор | Сократить ручное создание серии без потери контроля | OCR/LLM, нормализация, human review, идемпотентность |

---

# 1. Скаутская лаборатория

## Проблема

Перед дедлайном пользователь видит карты и игроков, но решение часто принимает
по памяти или симпатии. В приложении уже накоплены результаты игр, детализация
скоринга, свойства карт и перков, но эти данные не превращены в инструмент выбора.

Лаборатория отвечает не на вопрос «кто точно наберёт больше», а на более полезные
вопросы:

- как игрок выступал в последних релевантных играх;
- насколько его результат стабилен;
- какие перки конкретной карты действительно срабатывали;
- что меняется при замене одной карты в составе;
- укладывается ли состав в правила MAIN или BUDGET;
- на каких данных основан вывод и насколько мала выборка.

## Важная граница: это не scoring replay

Существующий пересчёт скоринга отвечает на операционный вопрос: **сколько очков
получили уже созданные команды по известным играм и текущим правилам**.

Скаутская лаборатория отвечает на предматчевый вопрос: **какие доступные
пользователю варианты состава выглядят сильнее или надёжнее до дедлайна**.

Она использует историю как входные данные, но не повторяет пересчёт завершённой
серии и не изменяет сохранённые очки.

## Продуктовый принцип

Лаборатория — помощник для принятия решения, а не предсказатель результата.

- Каждый вывод должен быть объяснимым: период, число игр, использованные факторы.
- При маленькой выборке интерфейс показывает «недостаточно данных», а не уверенный
  рейтинг.
- Персональные рекомендации строятся только по картам пользователя и правилам
  выбранной лиги.
- На первом этапе не нужен ML: детерминированные агрегаты проще проверить,
  объяснить и продемонстрировать.
- Интерфейс проектируется для телефона и короткой сессии перед дедлайном.

## Пользовательский сценарий

### 1. Вход

На странице предстоящей серии появляется CTA **«Открыть лабораторию»**. Он ведёт
сразу в контекст серии, выбранной лиги и карт пользователя.

### 2. Быстрый обзор

Пользователь видит игроков текущего ростера:

- форму за выбранное окно;
- медиану и разброс базовых очков;
- количество учтённых завершённых игр;
- тренд относительно предыдущего окна;
- индикатор свежести данных;
- предупреждение о маленькой выборке.

Это не глобальный рейтинг игроков: набор кандидатов ограничен текущей серией.

### 3. Сравнение

Пользователь выбирает до трёх игроков или принадлежащих ему карт. Сравнение
показывает не один магический score, а несколько осей:

- результативность;
- стабильность;
- частота полезных для карты событий;
- вклад перков;
- стоимость для BUDGET;
- доступный остаток контракта;
- число игр в выборке.

### 4. Песочница состава

Пользователь собирает черновой состав из 1–3 карт. Для каждой замены лаборатория
показывает:

- ожидаемый исторический диапазон, а не одно точное число;
- вклад base points, rarity modifier и наблюдаемой частоты перков;
- ограничение value cap для BUDGET;
- предупреждения о недоступной карте, контракте или прошедшем дедлайне;
- короткое объяснение: «выше потенциал, но сильнее разброс» или «ниже среднее,
  зато больше стабильных игр».

После сравнения CTA **«Собрать эту команду»** открывает существующий team builder
с предзаполненными картами. Фактическая отправка состава остаётся в действующем
flow и проходит все серверные проверки.

### 5. Быстрые стратегии

Чтобы экран был полезен в короткой mobile-сессии, лаборатория может сразу
предложить три допустимых состава:

- **Надёжный** — предпочитает устойчивый результат и штрафует разброс;
- **Баланс** — предпочитает историческую медиану;
- **Потенциал** — предпочитает верхнюю границу исторического диапазона.

Пользователь может закрепить уже выбранную карту, а лаборатория достроит только
оставшиеся слоты. Кнопка **«Примерить состав»** переносит рекомендацию в обычный
team builder, но ничего не отправляет автоматически.

## Метрики игрока в MVP

Для каждого показателя нужно фиксировать версию формулы и окно данных.

| Показатель | Возможная формула | Зачем пользователю |
|------------|-------------------|---------------------|
| Средняя форма | Среднее base points за последние N релевантных игр | Быстрая оценка результата |
| Медианная форма | Медиана base points | Снижает влияние выбросов |
| Стабильность | IQR или стандартное отклонение с понятной шкалой | Показывает риск выбора |
| Тренд | Последние K игр против предыдущих K | Видно изменение формы |
| Опыт выборки | Число завершённых игр и диапазон дат | Позволяет оценить доверие |
| Совместимость перков | Историческая частота событий, распознаваемых perk detectors | Объясняет ценность конкретной карты |
| Value efficiency | Исторический показатель относительно card value | Помогает в BUDGET |

Роль за столом можно использовать только там, где она надёжно присутствует в
кеше игры. Если часть истории неполна, сервис должен исключать её из конкретной
метрики и показывать фактический размер выборки.

## Что считать «прогнозом»

В MVP лучше не называть результат прогнозом. Без калибровки и backtesting это
**исторический ориентир состава**:

```text
ориентир карты = исторические base points игрока
               × rarity modifier карты
               + наблюдаемый вклад её перков
```

Для состава агрегируется диапазон карт. Значение не записывается в сущности
скоринга, не влияет на лидерборд и не обещает фактический результат серии.

## Данные и вычисления

Для первого варианта достаточно существующих источников:

- `series_game.game_data_cache` — сохранённые данные завершённых игр;
- `fantasy_team_card_game_score` и perk breakdown — фактическая детализация
  проведённого скоринга;
- `fantasy_player` и его aliases — единая идентичность игрока;
- `series_player` — кандидаты конкретной серии;
- `card_template`, `user_card`, perks, rarity и value — доступные варианты выбора;
- правила MAIN/BUDGET и текущий `value_cap`.

Не следует выполнять запросы к Polemica API при открытии экрана. История должна
агрегироваться из локальной БД после sync/scoring и переиспользоваться между
пользователями.

### Обязательный data-quality preflight

Существующие строки `fantasy_team_card_game_score` содержат selection bias:
детализация есть прежде всего для игроков, карты которых пользователи реально
ставили в команды. До выбора формулы нужно read-only измерить:

- долю игроков актуальных ростеров с 5/10/20 уникальными завершёнными играми;
- дубли одной игры из-за участия карт разных пользователей;
- долю результатов, записанных через replacement;
- полноту `game_data_cache` и доступность данных для perk detectors;
- влияние фильтра по tournament/формату на размер выборки.

Исторические наблюдения должны дедуплицироваться как минимум по
`fantasy_player + polemica_game_id`, а игры после дедлайна анализируемой серии не
могут попадать в расчёт. Если coverage окажется недостаточным, следующим слоем
становится нормализованная таблица player-game facts для всех участников игры, а
не только для выставленных карт.

### Возможная модель snapshot

Если расчёт на лету окажется тяжёлым, добавить версионированный snapshot:

```text
scouting_player_snapshot
  fantasy_player_id
  window_type / window_size
  calculated_at
  data_version
  finished_game_count
  first_game_at / last_game_at
  base_points_mean / median / dispersion
  recent_trend
  perk_event_rates_json
```

Snapshot не обязан появляться в первой миграции. MVP можно начать с отдельного
query/service слоя и короткого application cache, замерив реальную стоимость
запросов.

## API-контур

Возможный контракт:

```http
GET /api/v1/scouting/series/{seriesId}?league=MAIN&window=20
POST /api/v1/scouting/lineup-preview
```

Первый endpoint отдаёт кандидатов, агрегаты, качество выборки и доступные карты
авторизованного пользователя. Второй получает только идентификаторы серии, лиги
и карт и возвращает preview после обычной проверки владения, доступности и
лимитов.

Альтернативный более быстрый контракт для готовых стратегий:

```http
POST /api/v1/series/{seriesId}/scouting/recommendations

{
  "leagueCode": "BUDGET",
  "lockedUserCardIds": [123]
}
```

POST здесь остаётся read-only: вход зависит от закреплённых карт, но endpoint не
сохраняет и не отправляет команду.

Ключ кеша для общих агрегатов: `seriesId + dataVersion + window`. Персональная
часть с картами пользователя достраивается отдельно, чтобы не создавать кеш на
каждого пользователя.

## Производительность

- Агрегаты пересчитываются после появления новых завершённых игр или изменения
  идентичности игрока, а не на каждый HTTP-запрос.
- Экран читает только данные игроков текущего `series_player`.
- Карты, шаблоны и перки загружаются batch-запросами без N+1.
- Тяжёлые JSONB-разборы не выполняются в длинной транзакции.
- Ответ поддерживает ETag/data version; TanStack Query может переиспользовать его
  в течение пользовательской сессии.
- Для MVP задаётся бюджет, например p95 backend latency до 300 мс на прогретом
  кеше; фактический порог утверждается после замеров.
- При подборе BUDGET сначала можно оставить Pareto-frontier карт одного игрока
  по `(value, выбранный показатель)`, а затем перебирать составы максимум из трёх
  игроков. Это ограничивает комбинаторный взрыв.

## Mobile UX и состояния

На телефоне не нужна большая аналитическая таблица. Базовый экран состоит из:

1. короткой ленты игроков серии;
2. bottom sheet сравнения;
3. компактного состава из трёх слотов;
4. одного главного вывода и раскрываемого блока «Почему?»;
5. CTA в существующий team builder.

Обязательные состояния:

- история ещё не накоплена;
- часть игр без завершённого результата;
- данные устарели;
- у пользователя нет подходящей карты игрока;
- карта выставлена на marketplace или иначе недоступна;
- превышен BUDGET cap;
- дедлайн уже прошёл;
- серия или ростер изменились во время сессии.

## Продуктовые метрики

События должны оставаться низкокардинальными и не содержать ID пользователя или
игрока в labels инфраструктурных метрик.

- `scouting_lab_opened`;
- `scouting_player_compared`;
- `scouting_lineup_previewed`;
- `scouting_team_builder_opened`;
- `scouting_team_submitted_after_lab`;
- доля экранов с недостаточной выборкой;
- p50/p95 latency и error rate лаборатории.

Главная продуктовая проверка: пользователи после лаборатории чаще доходят до
отправки валидной команды, не увеличивая число отмен и ошибок состава.

## MVP

1. Лаборатория только для UPCOMING-серий до `teamDeadline`.
2. Сравнение игроков текущего ростера по детерминированным агрегатам.
3. Сравнение принадлежащих пользователю карт.
4. Песочница из 1–3 карт с проверками MAIN/BUDGET.
5. Объяснение периода, выборки и факторов.
6. Переход в существующий team builder с предзаполнением.
7. Никакого ML и онлайн-вызовов Polemica при открытии экрана.

## После MVP

- калиброванные диапазоны после backtesting на исторических сериях;
- сравнение похожих игроков и турниров;
- персонализация по риск-профилю пользователя;
- объясняющий AI-текст поверх уже рассчитанных факторов;
- сохранённые варианты состава и сравнение с фактическим результатом после серии.

AI здесь не должен рассчитывать очки. Его допустимая роль — перевести готовые
структурированные факторы в короткое человеческое объяснение.

## Риски и защиты

| Риск | Защита |
|------|--------|
| Пользователь воспринимает ориентир как обещание | Диапазон, размер выборки, объяснение и отказ от слова «точный прогноз» |
| История разных турниров несопоставима | Явная политика релевантной выборки и фильтры по турниру/формату |
| Новый игрок всегда оказывается внизу | Состояние «недостаточно данных», а не нулевая оценка |
| JSONB-агрегации перегружают БД | Snapshot/cache и пересчёт по событию |
| Лаборатория расходится с team builder | Preview не создаёт команду; окончательная серверная валидация остаётся единственным source of truth |
| Метрика становится скрытым рейтингом игроков | Показывать компоненты, период и uncertainty, не публиковать глобальный leaderboard |

## Почему это хорошо выглядит на конкурсе

- Использует настоящую предметную область и накопленные данные, а не декоративный
  AI-чат.
- Даёт сильный мобильный demo flow: сравнение, «что если», объяснение, готовый
  состав.
- Показывает backend-глубину: versioned analytics, cache invalidation, data
  quality, интеграцию с существующим скорингом и observability.
- Можно честно измерить пользу через конверсию в отправленный состав.

---

# 2. Автоматический импорт серий из Telegram

## Проблема

Серия часто создаётся в день игры по сообщению или картинке из Telegram. Сейчас
администратор вручную переносит название, дату, время, технические поля и ростер,
а затем отдельно проверяет дубли и назначает игроков. Ошибка особенно неприятна
при коротком промежутке до `teamDeadline`.

Для ЗЛ и ЛП есть конкретный доверенный источник — публичный канал
[`@polemica_closed_league`](https://t.me/s/polemica_closed_league). В нём
публикуются два полезных класса событий:

- `ANNOUNCEMENT` — анонс серии с датой и временем в тексте/caption; ростер часто
  находится на изображении;
- `RESULT` — результаты серии или игрового дня с блоками игр, ролями и
  победившей стороной.

В техническом смысле импортируются **серии ЗЛ/ЛП во внутренние `tournament`**, а
не сущности `league`: `MAIN` и `BUDGET` остаются фэнтези-лигами внутри уже
созданной серии.

Форматы анонсов нестабильны: свободный текст, афиши, сокращения лиг, никнеймы с
OCR-ошибками, комментаторы в списке, замены и несколько серий в одном сообщении.
Поэтому чисто алгоритмический parser действительно не решает задачу целиком.

## Продуктовый принцип

Автоматизация должна быть асимметричной:

- получение постов, OCR, classification, extraction, duplicate lookup, sync и
  reconciliation можно выполнять автоматически;
- изменение production-серии и особенно `finalize` проходят отдельные доменные
  gates;
- Telegram не становится scoring source of truth. Очки по-прежнему считаются по
  данным игр Polemica и публичным match points.

Пост результата является **сигналом завершения и независимым evidence**, а не
командой «немедленно финализировать». Он запускает `sync -> reconciliation ->
scoring` и при успешной проверке переводит candidate в `READY_TO_FINALIZE`.
Финализация остаётся явным подтверждением администратора, пока не появятся
защита от concurrent finalize, строгий completion gate, correction workflow и
достаточная статистика shadow-режима.

Безопасный конвейер:

```text
allowlisted channel event / текст / изображение / forward
  -> durable ingest + dedupe
  -> classification: ANNOUNCEMENT | RESULT | IGNORE
  -> OCR / structured extraction
  -> детерминированное сопоставление с production-данными
  -> ANNOUNCEMENT: review + dry-run -> UPCOMING + roster -> verification
  -> RESULT: link series -> sync -> reconcile -> score -> readiness preview
  -> explicit finalize
```

LLM не получает credentials, не вызывает admin API и не выбирает сам
неоднозначный production ID.

## Наблюдаемые форматы канала

По публичной истории канала можно заложить сильные детерминированные признаки:

- `#анонс_ЗЛ` / `#анонс_ЛП` плюс заголовок, дата и время;
- `#результаты_ЗЛ` / `#результаты_ЛП` плюс заголовок серии/финала и блоки
  `Игра N`;
- обычно 4 игры в серии, но финальные дни могут содержать 5 и более игр;
- отдельный пост может содержать уточнения состава, которые не обязаны влиять на
  reconciliation результата;
- рядом встречаются эфиры, опросы, статистика, результаты розыгрышей, итоги
  этапов и сезона — они не должны становиться actionable candidates.

Hashtag — сильный сигнал, но недостаточный. Например, `#результаты_ЗЛ` может
стоять и у итоговой таблицы сезона. `RESULT` требует одновременно поддерживаемый
tag, однозначный заголовок конкретной серии/дня и структурированные игровые
блоки. Необычный или неполный пост уходит в review, а не исправляется догадкой.

## Уровни автоматизации

| Уровень | Поведение | Решение |
|---------|-----------|---------|
| L0 | Paste/upload вручную | Fallback и backfill |
| L1 | Автополучение, dedupe, classification, Inbox | Первый production слой |
| L2 | OCR/extraction, matching и готовый draft | MVP для анонсов |
| L3 | Auto-sync/reconcile/score по результату, one-click finalize | MVP для результатов |
| L4 | Guarded auto-create и auto-finalize | Только opt-in после shadow evidence |

Целевой MVP — L1+L2 для анонсов и L3 без unattended finalize. Это снимает почти
весь ручной поиск и перенос данных, но не превращает внешний пост в необратимую
экономическую команду.

## Источники и доступ к Telegram

### Выбранный вариант: read-only user session через MTProto

Доступа к администрированию `@polemica_closed_league` нет, поэтому добавить
import-bot нельзя. Публичный канал можно читать как обычный Telegram-пользователь
через MTProto: Telegram документирует получение сообщений через
[`channels.getMessages`](https://core.telegram.org/method/channels.getMessages)
и channel difference updates для user sessions в
[`Updates`](https://core.telegram.org/api/updates). Отдельный Python worker на
Telethon авторизуется по
`api_id`/`api_hash`, телефону, одноразовому коду и при необходимости 2FA, затем
читает только allowlisted username/numeric channel ID. Канал не требуется
администрировать; достаточно, чтобы аккаунт мог открыть публичный канал. Для
стабильных updates лучше подписать этот аккаунт на канал.

В репозитории уже есть близкий read-only пример
`scripts/telegram-support-export/export_support_messages.py`. Production worker
использует тот же способ авторизации, но работает постоянно: получает новые и
отредактированные сообщения, периодически делает reconciliation по
`message_id/edit_date`, скачивает максимальный доступный размер изображения и
сохраняет durable envelope до OCR. Realtime updates не заменяют polling/backfill:
после рестарта worker дочитывает сообщения начиная с последнего сохранённого ID.

User session даёт более широкий доступ, чем Bot API. Поэтому предпочтителен
отдельный Telegram-аккаунт только для импорта; session-файл хранится вне git и
DB с правами `0600`, процесс запускается отдельным Unix user, а код не содержит
методов отправки/редактирования сообщений. Компрометация закрывается отзывом
сессии в Telegram. `api_hash`, телефон и 2FA также не попадают в логи или
browser storage.

Идентичность поста — `(source_channel_id, message_id)`, revision —
`edit_date + content_hash`. Для альбомов сообщения с одинаковым grouped ID
агрегируются перед extraction. Download media, OCR, LLM и Polemica calls идут
асинхронно после durable ingest.

Не следует смешивать ingestion с текущим support webhook: у нового worker свои
source allowlist, state machine и kill switch.

### Более узкий вариант в будущем: отдельный import-bot

Если владельцы канала позже смогут добавить бота, transport можно заменить на
Bot API `channel_post`/`edited_channel_post` с webhook secret и numeric `chat.id`
allowlist. Доменный inbox и дальнейший pipeline от transport не зависят.

### Fallback: forward или paste/upload

Если добавить бота в канал нельзя, администратор пересылает пост import-боту либо
вставляет текст/загружает изображение в админке. Forward создаёт тот же durable
event и тот же review flow; Telegram не должен уметь подтверждать `apply` или
`finalize`.

Paste/upload также нужен для исторического backfill и recovery, поскольку Bot
API доставляет будущие updates, а не является интерфейсом надёжной выгрузки всей
истории канала.

### Почему не web scraping

Публичная страница `t.me/s/...` полезна для discovery и начального golden corpus,
но не является production transport: HTML и доступность могут меняться, нет
гарантированной доставки edits и ordering.

MTProto содержит update удаления channel message, но после downtime удалённый
контент уже нельзя надёжно восстановить простым history polling. Поэтому delete
не используется как автоматический rollback signal: исправление или отзыв после
apply создаёт incident/review item, но само не откатывает production.

## Почему нужен гибрид алгоритмов и LLM

| Задача | Лучший механизм |
|--------|-----------------|
| ISO-дата, время, timezone, диапазоны игр | Детерминированная валидация |
| Текст с изображения | OCR |
| Свободная формулировка, роли, замены | LLM structured extraction |
| Поиск активного турнира | Production lookup + правила |
| Никнейм -> `tournament_player.id` | Alias resolver + fuzzy candidates + review |
| Следующий номер и технический диапазон | История серий + детерминированные проверки |
| Решение при конфликте | Человек |
| Production write | Существующий backend service после подтверждения |

Алгоритм хорошо валидирует строгие правила, но плохо понимает произвольную
вёрстку и язык анонса. LLM лучше извлекает смысл, но может ошибиться или уверенно
додумать отсутствующее значение. Поэтому его результат — только один из входов
нормализации.

## Пошаговый сценарий

### 1. Приём и fingerprint

Worker/backend принимает MTProto update, forward, текст или изображение,
проверяет source allowlist, размер/MIME и сохраняет событие до начала внешней
обработки.
Transport duplicate определяется по transport event ID, если он есть, post
identity — по `(source_chat_id, message_id, revision)`, semantic repost — по
`source_hash`. Повторный источник не должен незаметно создать второй импорт.

Изменение `edit_date/content_hash` создаёт immutable revision. Старая
revision не удаляется из audit. Edit до apply инвалидирует draft; edit после
apply создаёт patch proposal или conflict, но не меняет серию молча.

### 2. Classification

Сначала дешёвый детерминированный classifier выделяет:

- `SERIES_ANNOUNCEMENT`;
- `SERIES_RESULT`;
- `LIVE_REMINDER`;
- `SCHEDULE_OR_SEASON_ANNOUNCEMENT`;
- `STANDINGS_OR_STATISTICS`;
- `CORRECTION_OR_RETRACTION`;
- `OTHER`.

Только первые два класса запускают основной pipeline. Для результата actionable
класс требует конкретную серию/день и игровые блоки; слова «результаты» или
hashtag сами по себе недостаточны.

### 3. OCR

Для MVP основной provider —
[Yandex Vision OCR](https://yandex.cloud/ru/docs/vision/quickstart): он вписывается в текущую
Yandex Cloud-инфраструктуру и поддерживает русский/английский текст и координаты
блоков. Worker:

1. скачивает через Telethon максимальный доступный размер фото/альбома;
2. исправляет EXIF orientation, валидирует MIME/размер и при необходимости
   уменьшает изображение без агрессивной потери мелкого текста;
3. вызывает `ocr/v1/recognizeText` с моделью `page` и
   `languageCodes = ["ru", "en"]` от отдельного service account только с ролью
   [`ai.vision.user`](https://yandex.cloud/ru/docs/iam/roles-reference);
4. сохраняет text blocks, строки, bounding boxes и detected languages вместе с
   provider/model version;
5. передаёт caption + OCR-текст в structured extraction, а координаты использует
   для side-by-side review;
6. отправляет в ручной review повреждённое изображение, непрочитанный обязательный
   блок, неоднозначный alias или результат, не прошедший golden-corpus quality
   gates.

OCR ничего не решает о production IDs и не применяет серию. После него backend
детерминированно нормализует пробелы/Unicode, но сохраняет raw text как evidence.
Оригинал и распознанный текст отображаются рядом. API не возвращает надёжный
word-level confidence, поэтому его нельзя выдумывать: подозрительные фрагменты
подсвечиваются по детерминированным проверкам структуры, обязательных полей и
alias matching. Tesseract можно держать как локальный fallback, но выбор
primary provider следует подтвердить на golden corpus реальных афиш: стилизованные
шрифты и фон важнее синтетического OCR benchmark.

### 4. Structured extraction

LLM получает только содержимое анонса и строгую JSON Schema. Возможный результат:

```json
{
  "language": "ru",
  "leagueHint": "ЗЛ",
  "seriesNumber": 21,
  "date": "2026-08-05",
  "startTime": "18:00",
  "timezone": "Europe/Moscow",
  "teamDeadlineHint": null,
  "roster": [
    {
      "rawName": "Player",
      "role": "PLAYER",
      "isSubstitute": false,
      "replacesRawName": null,
      "confidence": 0.98,
      "evidence": "..."
    }
  ],
  "unknownFields": ["teamDeadline"]
}
```

Для каждого значимого поля нужны `confidence` и короткий evidence fragment.
Отсутствующее значение возвращается как `null/unknown`, а не достраивается.
Численные границы confidence должны быть откалиброваны на тестовом корпусе;
условно высокое значение можно предзаполнить, среднее — подсветить, низкое —
оставить нерешённым.

Анонс считается недоверенным контентом. Инструкции внутри изображения или текста
не меняют system prompt и не дают LLM никаких tools.

### 5. Нормализация по production-данным

Backend отдельно и детерминированно:

- находит один активный tournament по названию, сокращению, истории и roster
  overlap;
- определяет `TournamentKind`;
- для `STANDALONE` предлагает `namePrefix` и `gameStartedOn`;
- для `POLEMICA_COMPETITION` предлагает `gameNumFrom`, `gameNumTo` и `gamePhase`;
- выводит следующий `public_number` из истории, если он не указан;
- сопоставляет игроков через `fantasy_player` и aliases;
- исключает явно отмеченных комментаторов/ведущих;
- применяет объявленную предматчевую замену к итоговому roster;
- ищет дубли по tournament, номеру, имени, времени и техническому диапазону;
- рассчитывает предлагаемый `teamDeadline` по подтверждённой политике турнира.

Система не должна автоматически создавать отсутствующего игрока или добавлять его
в tournament roster. Это отдельная административная операция с отдельным
подтверждением.

### 6. Review

Экран делится на источник и черновик. У каждого поля виден статус:

- подтверждено источником;
- выведено из истории;
- исправлено администратором;
- конфликт;
- не определено.

Отдельно показывается mapping:

```text
имя в анонсе -> production nickname -> tournament_player.id
```

Если найдено несколько кандидатов, выбор обязателен. Система не должна молча
брать первый fuzzy match.

### 7. Dry-run и impact preview

Перед применением backend повторно проверяет актуальное состояние:

- не появился ли дубль после открытия review;
- активен ли tournament;
- не изменились ли номер, диапазон игр или roster;
- допустимы ли поля для конкретного `TournamentKind`;
- существуют ли все `tournamentPlayerIds`;
- не истёк ли `teamDeadline`.

Если импорт пытается изменить уже существующий состав, preview показывает, какие
игроки будут удалены/добавлены и какие пользовательские команды или карты могут
быть затронуты. Такая замена требует отдельного явного подтверждения.

### 8. Apply анонса и проверка

Операции разделяются так же, как в текущем admin API:

1. создать серию со статусом `UPCOMING`;
2. проверить сохранённые поля;
3. назначить игроков;
4. read-only проверить итоговый roster;
5. записать audit с ID импорта, серии и администратора.

Если первый шаг успешен, а назначение не прошло, импорт переходит в состояние,
которое позволяет безопасно продолжить существующую серию, а не повторять
создание.

### 9. Обработка результата

`SERIES_RESULT` связывается с серией прежде всего через ранее применённый анонс.
Fallback допускается только при одном кандидате по source mapping, сезону,
tournament, номеру/этапу и дате. Номер серии без сезона или tournament
недостаточен: нумерация повторяется.

Из result post детерминированно извлекаются:

- logical series/day identity;
- номера и ожидаемое количество игр;
- для каждой игры black set, sheriff и winner;
- признаки partial day, final day или correction.

Нельзя разбирать список мафии простым `split(',')`: никнеймы вроде
`Houston, TX` содержат запятую. Parser использует известные aliases, структуру
строк и unresolved candidates. Пост результата не содержит полный красный
ростер и base points, поэтому не создаёт `series_game` и не считает очки.

После exact linkage worker последовательно выполняет:

```text
move ACTIVE -> SCORING if needed
  -> sync games from Polemica
  -> compare Telegram evidence with Polemica cache
  -> calculate fantasy scores
  -> recompute completion/readiness from fresh DB state
```

Если игр ещё нет или часть не закончена, candidate переходит в
`WAITING_FOR_GAMES` и повторяется scheduler'ом. Mismatch не переписывает данные
Polemica: scoring остаётся корректным, а автоматизация блокируется до review.

### 10. Finalization readiness

`READY_TO_FINALIZE` возможен только при одновременном выполнении всех условий:

1. result post связан ровно с одной серией, и source revision актуальна;
2. серия имеет `SCORING`, `finalized = false` и не менялась после preview;
3. ожидаемое число игр известно для этой серии/дня;
4. число уникальных `series_game` ровно равно ожидаемому, без extra games;
5. у каждой игры есть валидный cache и non-null Polemica result;
6. public points доступны без `PARTIAL`, `LOAD_FAILED` или `CACHE_INVALID`;
7. все ожидаемые игры имеют `scored = true` после последнего sync;
8. порядок игр, black set, sheriff и winner не противоречат result post;
9. нет unresolved aliases или других blocking warnings;
10. тот же readiness checksum подтверждён повторно перед финализацией.

Admin preview показывает `announced / synced / finished / scored`, конкретные
расхождения, leaderboard, награды и число списываемых uses. MVP вызывает
`finalize` только после явного подтверждения администратора.

Для будущего auto-finalize дополнительно нужны единый gate для ручного и
автоматического endpoint, grace period после последнего edit и
несколько последовательных стабильных reconciliation polls. Исправление после
финализации создаёт incident: автоматического отката наград и uses нет.

## Текущие backend gaps перед result automation

До автоматизации результата нужно усилить общий lifecycle, а не прятать проверки
только внутри import worker:

- текущий `SeriesFinalizationService` не проверяет status, expected game count,
  finished/scored games и сразу начисляет rewards/списывает uses;
- concurrent finalize защищён pessimistic row lock на `series`; повторный вызов
  после ожидания получает `409`, не повторяя награды и списание uses;
- обычные sync/calculate запрещены после финализации и повторно проверяют
  `finalized` под тем же row lock после внешних HTTP-вызовов;
- `assignPlayers` является полной заменой roster. Auto-apply запрещён при
  существующих командах; после `teamDeadline` изменение roster должно быть hard
  block, а не попытка pruning;
- duplicate preflight серии нуждается в concurrency-safe constraint/lock, потому
  что на natural key серии нет DB uniqueness.

Новый `SeriesCompletionService` должен строить versioned readiness preview и
использоваться импортом, обычной admin-кнопкой и будущим auto-finalize. Внешние
Telegram/Polemica calls остаются вне транзакции; внутри финальной короткой
транзакции повторно проверяются row version/checksum и ставится row lock.

## Модель состояния

Лучше отделить immutable transport evidence от изменяемого business candidate.
Минимальный набор таблиц:

- `telegram_channel_source` — numeric `chat_id`, display username, enabled,
  allowed event types и automation policy;
- `telegram_channel_event` — transport event ID, `message_id`, grouped/media ID,
  posted/edited time, content hash, text/caption, media references и processing
  status;
- `telegram_channel_event_revision` — append-only версии source content;
- `league_import_item` — классификация, extracted payload, warnings, target
  series, optimistic version и business state;
- `series_external_post_link` — связь series с source post в роли
  `ANNOUNCEMENT`, `RESULT` или `CORRECTION`;
- `league_import_audit_event` — actor, parser/model version, redacted diff,
  idempotency key и outcome.

Возможная business state machine `league_import_item`:

```text
RECEIVED -> MATERIAL_PENDING -> CLASSIFIED -> EXTRACTING
  -> NEEDS_REVIEW | READY | IGNORED | DUPLICATE

ANNOUNCEMENT:
READY -> APPLYING -> APPLIED | CONFLICT | FAILED

RESULT:
READY -> WAITING_FOR_GAMES -> RECONCILING
  -> READY_TO_FINALIZE | PARTIAL_RESULT | BLOCKED_MISMATCH
  -> FINALIZED | INCIDENT
```

Хранение оригинального текста/изображения должно иметь ограниченный retention.
Telegram bot token, API key AI-провайдера и MTProto session никогда не хранятся
в этой таблице или browser storage. MTProto session лежит только в root-only
секретном файле worker-а вне репозитория.

Если один пост или album содержит несколько серий, один source event создаёт
несколько `league_import_item`. Один item может быть применён, пока другой
остаётся `NEEDS_REVIEW`.

Uniqueness нужна минимум на `(source_id, message_id, revision)` и на
transport event ID, если выбранный transport его предоставляет. Semantic hash выявляет repost, но
не заменяет transport identity. Apply использует optimistic `version` и
idempotency key; длительная работа claims item коротким lease/CAS, не держит DB
lock во время внешних вызовов.

## API-контур

Возможный admin API:

```http
GET   /api/v1/admin/league-imports?status=&kind=
GET   /api/v1/admin/league-imports/{id}
POST  /api/v1/admin/league-imports
PATCH /api/v1/admin/league-imports/{id}/draft
POST  /api/v1/admin/league-imports/{id}/reprocess
POST  /api/v1/admin/league-imports/{id}/preview
POST  /api/v1/admin/league-imports/{id}/link-series
POST  /api/v1/admin/league-imports/{id}/apply-announcement
POST  /api/v1/admin/league-imports/{id}/retry-reconciliation
POST  /api/v1/admin/league-imports/{id}/finalize
```

`apply-announcement` принимает ожидаемую `version` и idempotency key. Он не
доверяет сохранённому preview, повторяет критические проверки и либо создаёт
`UPCOMING` + roster + source link один раз, либо сохраняет `target_series_id` и
безопасно продолжает частично выполненный шаг.

`finalize` принимает readiness checksum и повторно проверяет его внутри
транзакции с row lock серии. Тот же completion gate должен использоваться
обычной admin-кнопкой; override расхождений, если он вообще нужен, требует
отдельной причины и audit.

OCR и LLM-вызовы выполняются вне DB-транзакции. Результат сохраняется отдельным
коротким шагом. Долгая обработка может быть асинхронной с polling из TanStack
Query.

## Интеграция с AI-провайдером

Нужен внутренний интерфейс, например `AnnouncementExtractor`, чтобы не связывать
доменную логику с одним провайдером:

```text
extract(announcementText, optionalImageMetadata) -> StructuredAnnouncement
```

Правила интеграции:

- schema-constrained output;
- таймаут, ограниченное число retries и circuit breaker;
- лимит размера текста/изображения;
- минимально необходимый payload без credentials и пользовательских данных
  Fantasy;
- логирование provider/model/version, latency и outcome без полного текста
  анонса;
- возможность отключить AI и вручную заполнить draft;
- запрет на tools, network access и production writes из модели.

Production alias candidates лучше подставлять после extraction в серверной
нормализации. Если когда-нибудь LLM потребуется для ранжирования похожих имён,
ему передаётся только минимальный набор nickname-кандидатов, но окончательный ID
по-прежнему подтверждается детерминированно или человеком.

## MVP

1. Read-only Telethon worker под отдельным user account, allowlisted channel ID
   и durable inbox.
2. New/edit polling, history backfill, album aggregation, idempotency и audit.
3. Детерминированная classification канала с `IGNORE` для нецелевых постов.
4. OCR для изображений и schema-constrained extraction через заменяемые
   providers.
5. Нормализация `STANDALONE` / `POLEMICA_COMPETITION`, alias mapping,
   commentators, substitutions, duplicate lookup.
6. Admin Inbox и side-by-side review с source revisions, evidence и unresolved
   fields.
7. Dry-run и явное apply анонса: `UPCOMING`, roster, source link, read-only
   verification.
8. Result pipeline: exact link, auto-sync, reconciliation, scoring и
   `READY_TO_FINALIZE` preview.
9. Явная финализация администратором с fresh readiness checksum и economy
   impact.
10. Paste/upload/forward fallback, metrics, retries, DLQ и независимые kill
    switches для ingest/apply/reconcile.

Не входят в MVP:

- unattended auto-finalize;
- автоматический rollback после edit/delete result post;
- production scraping публичной web preview;
- автоматическое создание игроков;
- изменение существующего активного roster без дополнительного impact preview;
- «AI-агент» с доступом к production API.

## После MVP

- guarded auto-create для exact-match анонсов с `impact = 0`;
- tournament-level opt-in auto-finalize после grace period и стабильных polls;
- correction/reopen workflow для уже финализированной серии;
- накопление подтверждённых исправлений как alias candidates;
- шаблоны для стабильных организаторов;
- локальная/дешёвая модель для простых текстов и fallback к более сильной модели
  только при низкой уверенности;
- автоматическое предложение stream links из анонса с URL allowlist.

## Тестирование и оценка качества

До включения AI нужен обезличенный golden corpus реальных форматов:

- обычный текст;
- screenshot с несколькими колонками;
- несколько времён и timezone;
- никнеймы на кириллице/латинице и OCR-похожие символы;
- комментатор внутри списка;
- «вместо X сыграет Y»;
- несколько серий в одном сообщении;
- `STANDALONE` и `POLEMICA_COMPETITION`;
- явный дубль;
- отсутствующий в roster игрок;
- `LIVE_REMINDER`, расписание, статистика, итоговая таблица и результаты
  розыгрыша как negative examples;
- result post с 4/5 играми, missing/duplicate game number и extra game;
- варианты `Победа: Мафия`, `Мафии`, `Мирные`;
- никнейм с запятой и OCR confusables;
- multi-day final и partial result первого дня;
- correction/edit до и после apply/finalize;
- duplicate, out-of-order и repost Telegram updates;
- prompt injection внутри анонса;
- OCR/LLM/Polemica timeout и недоступный provider.

Измерять нужно не только «распозналось/не распозналось», но и:

- exact match критических полей: дата, время, tournament, kind;
- precision/recall итогового roster;
- долю полей, исправленных человеком;
- долю импортов с unresolved fields;
- false duplicate и missed duplicate rate;
- false-positive actionable classification rate;
- среднее время `post published -> draft ready`;
- среднее время `result published -> ready to finalize`;
- долю `PARTIAL_RESULT` и `BLOCKED_MISMATCH`;
- `announced_game_count != synced_game_count` rate;
- apply/finalize success rate после успешного dry-run;
- ошибочные auto-create/auto-finalize как zero-tolerance safety metrics.

Для production observability подходят низкокардинальные метрики:

- `fantasy_league_import_events_total{kind,outcome}`;
- `fantasy_league_import_processing_duration_seconds{stage}`;
- `fantasy_league_import_apply_total{operation,outcome}`;
- `fantasy_league_import_reconciliation_total{outcome}`;
- `fantasy_league_import_unresolved_fields` как distribution/summary без имени поля
  в label, если набор полей может расти.

Алерт нужен на ingest lag, DLQ/backlog, массовые provider failures, apply errors,
edit after apply и reconciliation blockers. Низкая confidence одного анонса —
нормальный product state, а не инфраструктурный инцидент.

### Release gates

1. **Shadow:** полный игровой цикл, лучше 2–4 недели, только сохраняет и
   классифицирует posts без production effect.
2. **Corpus:** zero critical false positives на actionable classes; exact match
   автоматического подмножества по tournament/series/date/time/roster.
3. **Idempotency:** duplicate/out-of-order/edit/crash-after-create suite не
   создаёт дублей и не повторяет side effects.
4. **Lifecycle hardening:** row lock/idempotent finalize, запрет обычных
   sync/rescore после finalize и единый readiness gate готовы до result automation.
5. **Safe apply:** auto-create, если он включён, разрешён только при
   `impact = 0`; roster rewrite остаётся ручным.
6. **Readiness:** partial/missing/unexpected games никогда не дают green state;
   readiness стабилен несколько polls.
7. **Operations:** независимые kill switches для ingest, apply, reconcile и
   finalize; runbook и append-only audit проверены.

## Риски и защиты

| Риск | Защита |
|------|--------|
| LLM выдумывает отсутствующее поле | Schema, `unknown`, evidence, human review |
| Prompt injection в анонсе | Источник — недоверенный data-only input, у модели нет tools |
| Неверный nickname/OCR создаёт другой roster | Alias resolver, candidates, обязательный review ambiguity |
| Двойное применение | `source_hash`, version, idempotency key, duplicate preflight |
| Между review и apply изменился production | Повторная валидация при apply |
| Частично созданная серия | Сохранённый `target_series_id` и resume вместо повторного create |
| Слишком широкий Telegram-доступ | Отдельный user account, read-only worker, numeric channel allowlist, session `0600`, быстрый revoke |
| Утечка анонса или credentials | Минимальный payload, retention, secrets только server-side |
| Assign roster удаляет существующих игроков | Impact preview и отдельное подтверждение |
| Неверный result post | Exact series link, structural classifier и reconciliation с Polemica |
| Неполные/лишние игры | Expected count, unique games и blocking readiness gate |
| Двойная финализация | Series row lock, idempotency key и readiness checksum |
| Исправление после финализации | Incident/audit; никакого тихого rollback или rescore |
| Провайдер недоступен перед игрой | Ручное редактирование draft и существующий admin flow остаются fallback |

## Почему это хорошо выглядит на конкурсе

- AI применяется там, где правила действительно недостаточны: к неструктурированным
  текстам и изображениям.
- Система демонстрирует зрелую архитектуру: LLM не подменяет доменные правила,
  а работает внутри безопасного pipeline.
- Есть наглядный end-to-end demo: channel post → распознанный draft → серия →
  result post → sync/reconciliation → readiness preview.
- Решается реальная операционная проблема проекта, особенно для серий, которые
  объявляют в день игры.

---

# 3. Рекомендуемая приоритизация

## Для конкурсной версии

**Скаутская лаборатория — headline-функция.** Она видна конечному пользователю,
даёт сильный мобильный интерфейс и использует уникальные данные проекта.

**Telegram series autopilot — supporting engineering story.** Его лучше показать
компактным admin demo, подчёркивая безопасное сочетание channel events,
OCR/LLM, данных Polemica и доменных проверок.

Если времени хватает только на одну полноценную функцию, лаборатория сильнее для
презентации. Если важна быстрая ежедневная польза владельцу проекта, channel
ingest + admin-reviewed draft может окупиться раньше.

## Возможные вертикальные срезы

1. **Channel ingest shadow:** read-only Telethon worker, durable inbox, dedupe,
   classification и review-only draft.
2. **Announcement safe apply:** OCR, normalization, duplicate/impact preview,
   idempotent create + roster assignment.
3. **Result assistant:** exact linking, auto-sync, reconciliation, scoring и
   readiness preview с human finalize.
4. **Lifecycle hardening:** row lock, checksum, finalized mutation guards,
   metrics, alerts и correction incidents.
5. **Guarded automation:** exact-match auto-create; auto-finalize только после
   shadow evidence и отдельного opt-in.
6. **Scout data slice:** один агрегат формы для игроков текущей серии и экран
   сравнения.
7. **Scout lineup slice:** персональные карты, MAIN/BUDGET preview и переход в
   team builder.
8. **Predictive V2:** только после backtesting и проверки качества данных.

## Общие критерии готовности

- Ни одна функция не меняет scoring source of truth.
- Все production writes проходят через существующий service/API слой.
- Result post только запускает reconciliation; `series_game` и points приходят
  из Polemica.
- AI не имеет credentials или инструментов записи.
- Неоднозначность видна пользователю и не маскируется default-значением.
- Тяжёлые внешние вызовы и разбор данных не выполняются внутри DB-транзакций.
- Есть низкокардинальные метрики и понятные alert conditions.
- Есть мобильный happy path и явно обработанные stale/error states.

## Открытые продуктовые решения

Перед delivery-планом нужно подтвердить:

1. Какое историческое окно считать релевантным в лаборатории: последние N игр,
   текущий tournament или смешанный вариант?
2. Нужен ли пользователю один сводный ориентир или только независимые показатели?
3. Должна ли лаборатория поддерживать только UPCOMING-серии или также объяснять
   результат после завершения?
4. Подходит ли Yandex Vision OCR по стоимости/retention, и какой LLM provider
   допустим по политике данных?
5. Какой retention нужен для исходных изображений анонсов?
6. Готовы ли мы завести отдельный Telegram user account для read-only worker или
   временно использовать личную user session?
7. Ростер всегда находится на одном изображении или встречаются albums/text-only
   posts?
8. Как однозначно маппить новый сезон ЗЛ/ЛП на внутренний `tournament`, когда
   номера серий повторяются?
9. Какая deadline policy должна применяться к автоматически найденному времени?
10. Multi-day final — одна Fantasy-серия или отдельная серия на каждый день?
11. Откуда брать expected game count: из поста, tournament policy или отдельного
    per-series поля?
12. Разрешаем ли сразу auto-sync/score после result post?
13. Какой grace period нужен для edits/corrections результата?
14. Нужен ли correction/reopen flow до обсуждения unattended finalize?

Наиболее безопасная исходная позиция: **детерминированная лаборатория; channel
ingest + auto-draft для анонсов; result-driven sync/reconciliation с human
finalize.** Guarded auto-create и auto-finalize включаются только после shadow
режима и усиления lifecycle-инвариантов.

## Статус delivery: guarded create + result finalization (2026-08-11)

Локально реализован первый production-safe срез для text-only анонсов ЛП/ЗЛ:

- Telethon worker остаётся строго `SHADOW`, работает через VPN и передаёт в
  backend только подписанные HMAC evidence-события; admin/DB credentials и Bot
  API token в `BACKEND`-режиме ему запрещены;
- backend повторно парсит текст, использует точный numeric tournament policy,
  проверяет `ACTIVE + STANDALONE`, дату/время по Москве, дедлайн, revision и
  duplicate series;
- в точном админском чате появляется `Проверить создание`, затем fresh preview
  и `Создать без состава`; confirm привязан к тому же Telegram actor, но
  отдельного actor allowlist по принятому продуктовому решению пока нет;
- создаётся только `UPCOMING` shell-series с roster `0`; source link, audit,
  action state и outcome outbox сохраняются атомарно, а success содержит ссылку
  на назначение игроков в админке;
- worker/backend delivery, notifications, callbacks и create имеют независимые
  default-off gates; historical bootstrap молчит, targeted handoff выполняется
  явным replay одной записи.
- для каждой league policy создание и финализация имеют режимы
  `DISABLED | MANUAL | AUTOMATIC`; дополнительные глобальные gates
  `productionWritesEnabled` и `resultProcessingEnabled` выключены по умолчанию;
- strict RESULT parser принимает только один exact hashtag/title, ровно policy
  game count и contiguous блоки с одним победителем; результат связывается
  только с серией, уже имеющей `ANNOUNCEMENT` source link;
- reconciliation выполняет sync и score вне длинной транзакции. Readiness
  требует `SCORING`, exact ordered STANDALONE games, finished cache, полные
  public points, свежий scoring-context fingerprint и совпадение победителей;
- manual finalization использует fresh preview → same-actor confirm. Automatic
  finalization ждёт 15 минут от текущей revision и три одинаковых readiness
  poll с интервалом не меньше 120 секунд; durable jobs, generation/cutover и
  final recheck не позволяют оживить backlog или stale source;
- обычные create/update в `FINISHED` запрещены; assign/add/delete/sync/score
  сериализуются через series lock и не меняют finalized series.

Не входят в этот срез: OCR/media/albums, roster assignment, non-STANDALONE,
correction/reopen после финализации и production rollout. До явного canary
все production/result/mode flags остаются выключенными.
