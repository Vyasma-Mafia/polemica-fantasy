# System Patterns

## Архитектура

**Monolithic backend + 2 SPA frontends.**

Backend — единый Spring Boot сервис с двумя группами эндпоинтов (user API и admin API), разделёнными на уровне URL-префиксов и security-фильтров.

Два фронтенда — отдельные React-приложения:
- `polemica-fantasy-webapp` — Telegram Mini App для пользователей
- `polemica-fantasy-admin` — веб-админка

**Продакшен (VPS):** на сервере **`~/polemica-fantasy`** — git-репозиторий (clone ветки `master`); выкладка правок: **`git pull`** → Docker Compose → при необходимости сборка SPA и выкладка в `/var/www/...`. Детали и rsync с локальной машины — [`techContext.md`](techContext.md), разделы «Deployment (VPS…)» и «Быстрое обновление на VPS после правок».

## Паттерны бэкенда

### Layered Architecture
```
Controller → Service → Repository → PostgreSQL
                 ↓
          Polemica Client → Polemica API
```

- **Controller** — HTTP endpoints, валидация входных данных, маппинг DTO ↔ Entity
- **Service** — бизнес-логика, транзакции
- **Repository** — Spring Data JPA
- **Polemica layer** — обёртка над polemica-library для fetch + cache

### Key Patterns
- **Маркетплейс карт:** таблицы `marketplace_listing`, `user_card_ownership_history`; комиссия из `economy_config.marketplace.commission_percent`; диапазон цены листинга — `marketplace.min_price.*` / `marketplace.max_price.*` (`EconomyConfigService`, валидация в `MarketplaceService.createListing`); покупка с `PESSIMISTIC_WRITE` на листинг; уведомление продавцу в Telegram — `MarketplaceSaleNotificationEvent` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` + `TelegramBotApiClient` (как финализация серии). Карта в **ACTIVE**-листинге заблокирована для команды, **renew** и legendary upgrade; **recycle разрешён** и выполняется как **soft-delete** (`user_card.deleted_at`), при этом ACTIVE-листинги карты переводятся в `CANCELLED`, а история владения/участия в прошлых сериях сохраняется. **Админ-антимодерация (перелив):** `telegram_user.marketplace_banned` (V27) — общий флаг; **санкция** `ban-pair` **не** выставляет его и **не** отменяет листинги. `MarketplaceAdminService` — анализ пар, сделки, `ban-pair`: снятие **полного** нетто по **всем** SOLD в направлении к партнёру через `sumSellerReceivedForSalesTo` (в **т.ч.** карта уже у третьего); изъятие `user_card` **только** у исходного покупателя у партнёра: `findUserCardsBoughtOnMarketplaceFromPartner` с `ml.buyer.id = uc.telegramUser.id` в `EXISTS`. **V29** — после успешного `ban-pair` пишется `marketplace_pair_sanction_history` (аудит). **Read-only до применения:** `GET .../ban-pair/preview` (та же арифметика и список карт, без списаний/удалений). **История:** `GET .../ban-pair/history` с `Pageable`. `POST /unban/{id}` снимает только `marketplace_banned` (насле­дие/ручной сценарий). **Жалобы и точечные санкции по сделкам (V39):** `marketplace_complaint` + суточный лимит (`marketplace.daily_complaint_limit`), `marketplace_listing_sanction`, временный бан через `telegram_user.marketplace_banned_until`, штрафы/награды через `FantikiTransactionReason.MARKETPLACE_SANCTION_FINE` и `MARKETPLACE_COMPLAINT_REWARD`, уведомления `MarketplaceSanctionAppliedEvent`. В публичном `GET /marketplace/listings` скрываются seller и `card.value`, но в `my-listings` данные видны владельцу. Эндпоинты: `/api/v1/admin/marketplace/*` (Basic Auth).
- **DTO separation:** entity-классы не выходят за пределы service layer; контроллеры работают с DTO
- **JSONB caching:** полные данные игр из Полемики кэшируются в PostgreSQL JSONB для оффлайн-скоринга
- **Strategy pattern для достижений:** каждый детектор реализует `AchievementDetector` с полем `type: String` (совпадает с `achievement.id` в БД) и методом `matchCount(game, player)`; справочник — таблица `achievement` (каталог после Flyway V10: `sniper`, `voteForBlack`, …)
- **Синхронизация игр и скоринг:** вручную через админку (`sync`, `calculate`) и **по расписанию** — каждые 10 минут `ActiveSeriesSyncScheduler` для серий со статусом `ACTIVE` или `SCORING` и `finalized == false` вызывает `SeriesService.syncGames` → `calculateScores`; ошибки по одной серии логируются, остальные обрабатываются. В тестах `spring.task.scheduling.enabled: false`. Вызовы Polemica (HTTP) **вне** длинной транзакции БД: `DefaultGameSyncService` и `DefaultScoringService` готовят данные снаружи, запись — в `TransactionTemplate`, чтобы снизить удержание соединений Hikari.
- **Уведомления Telegram после финализации серии:** `SeriesFinalizationService` публикует `SeriesFinalizedNotificationEvent` с данными для каждого участника лидерборда и полем `winnerPublicName` (первая команда в порядке лидерборда; строка как в TMA: displayName → firstName → username → telegramId). `buildSeriesFinalizedTelegramMessage` добавляет всем получателям строку «Победитель серии: …». `SeriesFinalizedNotificationListener` обрабатывает событие в фазе `AFTER_COMMIT` и асинхронно (`@Async`, включён `@EnableAsync` на приложении) вызывает Bot API `sendMessage` через `TelegramBotApiClient` (`RestClient`). URL собирается как абсолютный `https://api.telegram.org/bot{token}/sendMessage` через `URI.create`, без шаблона `RestClient` с `{token}` в пути — иначе Spring кодирует `:` в токене как `%3A`, Telegram возвращает 404. Отправка не в транзакции БД; сбои Telegram логируются, финализация не откатывается. Флаг `telegram.bot.notifications.enabled` / `TELEGRAM_NOTIFICATIONS_ENABLED` отключает рассылку без смены токена.
- **Массовая рассылка из админки:** `POST /api/v1/admin/notifications/broadcast` (Basic Auth), тело `BroadcastMessageRequest` (текст до 4096 символов). `AdminBroadcastNotificationService` выбирает все `telegram_id` из `telegram_user` (`TelegramUserRepository.findAllTelegramIds`); при отключённых уведомлениях или пустом токене — **503**. Ответ **202 Accepted** с `recipientCount`; фактическая отправка в `TelegramBroadcastAsyncSender` (`@Async`, задержка 50 ms между чатами, ошибки по чату — warn в лог, как у финализации серии). Сообщение уходит с `parse_mode = MarkdownV2` (`TelegramBotApiClient.sendMessage(..., parseMode = PARSE_MODE_MARKDOWN_V2)`); остальные вызовы `sendMessage` без режима разметки.
- **Ростер серии и фэнтези-команды:** при смене состава серии (`assignPlayers`) старые выборы в `fantasy_team_card` могли ссылаться на игрока, которого больше нет в `series_player`. **`FantasyTeamRosterPruningService`** удаляет такие слоты (только до `team_deadline`), перенумеровывает оставшиеся; пустая команда удаляется; возвращает **`FantasyTeamRosterPruneResult`** (список снятых карт с `telegram_chat_id` и `fantasy_player_id`). Вызывается при назначении игроков и при чтении команды пользователем/публично. **`findAllBySeries_IdWithCards`** делает `JOIN FETCH telegram_user` для обрезки. После **`assignPlayers`**, если что-то срезано, **`SeriesService`** публикует **`SeriesRosterReplacementNotificationEvent`** (получатели с готовым текстом): сопоставление «убранный ↔ новый» игрок серии — при **равном** числе убранных и добавленных `tournament_player` — **zip** после сортировки по id; иначе текст без имён замены. **`SeriesRosterReplacementNotificationListener`** — как у финализации серии: `AFTER_COMMIT`, `@Async`, plain `sendMessage`, флаг `telegram.bot.notifications.enabled`.
- **Сборка команды:** в **`UserFantasyTeamService.attachCards`** запрещены дубликаты `user_card` id и дубликаты одного и того же **`fantasy_player_id`** (один игрок — не больше одной карты в команде). После успешного **`createFantasyTeam` / `updateFantasyTeam`** перед маппингом в DTO — **`entityManager.flush()`** и **`refresh(team)`**, чтобы коллекция слотов в ответе API совпадала с БД (иначе возможен пустой `slots` из L1 persistence context).

### Security
- User API: `TelegramAuthFilter` в цепочке только для путей `/api/v1/**` кроме `/api/v1/admin/**` (`UserApiRequestMatcher`); заголовок `Authorization: tma <initData>`; HMAC в `TelegramInitDataValidator`. Экземпляр фильтра создаётся внутри `userApiSecurityFilterChain`, не как отдельный `@Bean` типа `Filter` — иначе Spring Boot регистрирует глобальный servlet filter.
- Admin API: Basic Auth (`InMemoryUserDetailsManager`), matcher `/api/v1/admin/**`, `@Order(1)`
- Default: `@Order(3)` — `permitAll` (actuator и т.д.)

## Паттерны фронтенда

### Telegram Mini App
- `@telegram-apps/sdk-react` для интеграции с Telegram
- TanStack Query для server state management
- Компонентный подход (React)
- **Карточки пользователя:** URL для картинки — `playerPhotoUrl ?? imageUrl` (хелпер `polemica-fantasy-webapp/src/lib/cardImage.ts`, `cardDisplayImageUrl`). Рамка по редкости — CSS-модификаторы `*--common` | `*--rare` | `*--epic` | `*--legendary` на контейнерах карточек (коллекция, сборка команды, история фэнтези, модалки деталей). Разбивка per-game очков в UI — компонент `ScoreBreakdownBlock`.

### Admin Panel
- Ant Design как UI framework
- React Router для навигации
- TanStack Query для data fetching
- Страница **Broadcast** (`/broadcast`) — рассылка всем пользователям через бота (MarkdownV2, подсказка по ссылкам, приблизительный preview; подтверждение перед отправкой)
- Страница **Marketplace** (`/marketplace-moderation`) — анализ пар, сделки между двумя Telegram id, превью изъятий (`ban-pair/preview`), история применённых санкций (`ban-pair/history`), санкция пары (`ban-pair`: фантики/карты **без** бана маркетплейса и **без** снятия листингов), разбан `marketplace_banned` — `unban` (насле­дие); плюс complaints-flow: таб **«Жалобы»** (`complained-transactions` + модалка `transactions/{id}/sanction`) и таб **«Игроки по жалобам»** (`users-by-complaints`, бан/разбан по сроку)

## Структура пакетов бэкенда

```
io.github.mralex1810.fantasy
├── config/           # Spring, Security, Polemica beans
├── auth/             # TelegramAuthFilter, AdminAuthFilter
├── entity/           # JPA @Entity classes
├── repository/       # JpaRepository interfaces
├── dto/
│   ├── request/      # Request DTOs
│   └── response/     # Response DTOs
├── service/          # Business services
├── scoring/          # DefaultScoringService, ScoringService, achievement/* (detectors + registry)
├── polemica/         # PolemicaIntegrationService, DefaultGameSyncService (GameSyncService)
└── controller/
    ├── user/         # User-facing controllers
    └── admin/        # Admin controllers (+ PolemicaAdminController: read-only список/деталь Competition)
```

## Турниры и sync игр

- **`TournamentKind`**: `STANDALONE` | `POLEMICA_COMPETITION` (колонка `tournament.kind`, NOT NULL).
- При **`POLEMICA_COMPETITION`**: `tournament.polemica_competition_id` обязателен (UNIQUE среди не-NULL). Серии хранят `game_num_from` / `game_num_to` (inclusive по `num` из API); `DefaultGameSyncService` вызывает `getGamesFromCompetition` + `getGameFromCompetition`.
- При **`STANDALONE`**: match id из публичного профиля (до 500 игр на игрока) попадает в кандидаты, если id встречается у **≥ min(8, N)** игроков ростера серии (**N** игроков), затем фильтр по `name_prefix`; `series.game_num_*` NULL.
- Смена `kind` / `polemica_competition_id` при существующих сериях у турнира — **409 CONFLICT** (проверка по *фактическому* изменению после merge полей запроса с сущностью, а не по «поле присутствует в JSON» — иначе админка не могла менять статус/имя, отправляя те же kind и polemicaCompetitionId).

## Модель игроков и карточек

- **`fantasy_player`** — глобальная сущность: `polemica_user_id` (уникально), ник, фото. Создаётся/находится при добавлении игрока в турнир.
- **`tournament_player`** — связь «турнир ↔ fantasy_player» (участие в ростере турнира).
- **`card_template`** ссылается на **`fantasy_player`**, не на `tournament_player`: одна карточка одного игрока может участвовать в командах по разным турнирам/сериям, если этот игрок попал в серию через `series_player`.
- Открытие пака: выбор шаблонов по редкости из пула турнира; игроки с `tournament_player.excluded_from_pack_pool = true` не попадают в пул (админка — переключатель на странице турнира). При явном списке в паке исключённые тоже отфильтровываются при открытии; сохранение пака требует ≥1 игрока, не исключённого из пула.

## Решения и обоснования

| Решение | Причина |
|---------|---------|
| Монолит, а не микросервисы | Простота на старте, одна команда, одна БД |
| JSONB для game cache | Гибкость: структура PolemicaGame может меняться без миграций |
| Manual scoring trigger | Простота; real-time можно добавить позже через events/cron |
| Separate frontends | Разные аудитории, разные UI-фреймворки, независимый деплой |
| Basic Auth для админки | Минимум усилий на старте; JWT можно добавить позже |
| S3 для изображений | Стандартный подход; фронтенд читает напрямую из S3, бэкенд только загружает |
| MinIO для dev | S3-совместимый, запускается в Docker, не нужен реальный AWS для разработки |
| Multi-stage Dockerfile | Отделяет сборку от runtime; минимальный production image |
| GHCR + cosign | Стандартный подход для GitHub-проектов; подпись для безопасности |
| SSH deploy | На VPS: **git pull** в `~/polemica-fantasy` + `docker compose -f docker-compose.prod.yml up -d --build`; SPA — `npm run build` на сервере и `rsync` в nginx root; приватный репо — clone/pull по **SSH**, не HTTPS |
| Глобальный `fantasy_player` + `card_template` → FK на него | Одна карточка реального игрока переиспользуется между турнирами; ростер турнира/серии остаётся явным через `tournament_player` / `series_player` |
