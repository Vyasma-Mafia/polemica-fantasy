# Progress

## Что реализовано

### Маркетплейс карт (backend M1–M6)
- [x] **Flyway V22:** `marketplace_listing`, `user_card_ownership_history`, индексы (в т.ч. лента продаж), ключ `economy_config.marketplace.commission_percent`, бэкфилл провенанса из `user_card` как `PACK_OPENING`
- [x] **Flyway V23:** потолок цены листинга `marketplace.max_price.{RARITY}`, порог покупок `marketplace.min_pack_opens_before_purchase` (3); `telegram_user.pack_opens_count` (инкремент в `CardPackService.openPack`); бэкфилл: всем пользователям `pack_opens_count = 3`, активные листинги с ценой выше потолка урезаны до max по редкости
- [x] **Flyway V25:** минимальная цена листинга `marketplace.min_price.{RARITY}` (дефолты = `renewal.cost.*`); `EconomyConfigService.getMinListingPrice`, `EconomyInfoDto.marketplaceMinPrices`; валидация листинга и справка TMA используют отдельные минимумы
- [x] **Flyway V26:** `marketplace_listing.sold_card_template_id` → `card_template(id)` — снимок шаблона на момент продажи; `MarketplaceService.buyCard` выставляет снимок при `SOLD`; `getFeed` строит редкость и превью карты по снимку (иначе после EPIC→LEGENDARY апгрейда in-place лента показывала бы текущую легенду). Старые строки без снимка: fallback на текущий `user_card.card_template` (как раньше)
- [x] **Сущности и репозитории:** `MarketplaceListing`, `UserCardOwnershipHistory`, `MarketplaceListingRepository` (фильтры активных листингов, `PESSIMISTIC_WRITE` на покупку, лента SOLD), `UserCardOwnershipHistoryRepository.existsBy…`; причины фантиков `MARKETPLACE_PURCHASE` / `MARKETPLACE_SALE`; `EconomyConfigService.getMarketplaceCommissionPercent()`
- [x] **`UserCardOwnershipService`:** запись при выдаче карт из **CardPackService.openPack** (`PACK_OPENING`) и **CardService.giveCards** (`ADMIN_GRANT`); при покупке на маркетплейсе — `MARKETPLACE_PURCHASE`
- [x] **`MarketplaceService`:** листинг / снятие / покупка (комиссия `⌊price×pct/100⌋`, сброс контракта покупателю), каталог с `canBuy`/`canBuyReason`, мои листинги, лента; событие **после** успешной покупки
- [x] **Блокировки:** активный листинг — нельзя в команду (`UserFantasyTeamService.attachCards`), recycle/renew (`CardLifecycleService`), legendary upgrade (`LegendaryUpgradeService`)
- [x] **Recycle + провенанс:** при переработке карты `CardLifecycleService.recycleCard` вызывает `UserCardOwnershipHistoryRepository.deleteAllByUserCard_Id` до удаления `user_card` (иначе FK `user_card_ownership_history_user_card_id_fkey` после V22)
- [x] **API:** `MarketplaceController` под `/api/v1/marketplace` — `GET listings|my-listings|feed`, `POST listings`, `DELETE listings/{id}`, `POST listings/{id}/buy`
- [x] **Telegram:** `MarketplaceSaleNotificationListener` — `AFTER_COMMIT` + `@Async`, plain-text сообщение продавцу (`TelegramBotApiClient`), баланс после коммита через `UserService.getBalance`; `telegram.bot.notifications.enabled` / токен как у финализации серии
- [x] **Тесты:** расширен `CardLifecycleServiceTest` (mock `MarketplaceListingRepository`, сценарий «карта в листинге»); полный `test` с Testcontainers в CI/локально при доступном Docker; `UserApiIntegrationTest` — лента маркетплейса после покупки EPIC и апгрейда до LEGENDARY остаётся EPIC в `GET /api/v1/marketplace/feed`

### Маркетплейс TMA (M7–M11) и чтение провенанса
- [x] **TMA:** `MarketplacePage` (`/marketplace`) — лента сделок, фильтры (редкость, сортировка, цена, **игрок из справочника** `GET /api/v1/fantasy-players`, **турнир / серия** без обязательного выбора игрока — query `tournamentId`/`seriesId` в `GET /marketplace/listings`), пагинация, покупка; `MyListingsPage` (`/marketplace/my`); `api/marketplace.ts` + типы в `api/types.ts`; навигация в `App.tsx`
- [x] **Коллекция:** кнопка «Продать» (тулбар и модалка карты), модалка цены с превью комиссии; условия: `uses_remaining > 0`, нет активного листинга (`GET my-listings`), карта не в команде серии со статусом ≠ `FINISHED` (загрузка статусов серий по `fantasy-teams`)
- [x] **Провенанс в UI:** `CardOwnershipHistoryBlock` + `GET /api/v1/user-cards/{userCardId}/ownership-history` (`UserCardOwnershipController`, `UserCardOwnershipService.listOwnershipHistory`, репозиторий `findAllByUserCard_IdOrderByAcquiredAtAsc`); подписи `acquisitionLabel` на русском
- [x] **Economy info:** в `EconomyInfoDto` поля `marketplaceCommissionPercent`, `marketplaceMinPrices`, `marketplaceMaxPrices`, `minPackOpensBeforeMarketplacePurchase`; в `UserProfileDto` — `packOpensCount`; интеграционный тест `UserApiIntegrationTest` проверяет комиссию и поля economy-info маркетплейса

### Награды лидерборда серии: топ-25 и топ-50
- [x] **Flyway V21:** ключи `economy_config` `series.reward.top25`, `series.reward.top50`; подпись участия — «51+ место»
- [x] **`EconomyConfigService`:** `getSeriesReward` — диапазоны 11–25 и 26–50; `seriesRewardKeysInOrder` — 7 тиров
- [x] **Админка:** порядок строк наград серии в таблице Economy
- [x] **TMA Справка:** текст про диапазоны мест + список сумм из `economy-info`
- [x] **Тест:** `UserApiIntegrationTest` ожидает `seriesRewards.length() == 7`

### Детекторы достижений (polemica-fantasy-backend)
- [x] **`votingOnlyForBlack`:** учитывается только если у мирного есть хотя бы одно финальное голосование (`mine.isNotEmpty()`), иначе пустой список давал бы «все голоса за чёрных» по `Collection.all`.
- [x] **`sniper`:** дополнительно требуется смерть шерифа в **ночь 1** (`getKilled` + `night == 1`), а не только «реальный ком-убийца» по первой жертве в целом.

### STANDALONE: подбор игр серии по профилю (min(8, N))
- [x] **`DefaultGameSyncService.fetchStandalonePrepared`:** частоты match id по страницам профиля; порог **≥ min(8, N)** игроков ростера, затем `name_prefix`; константа `STANDALONE_MIN_PLAYERS_IN_PROFILE_OVERLAP`.
- [x] **`scripts/trace_series_game_sync.py`:** та же логика, `--min-overlap` (default 8).

### Документация в репозитории
- [x] **Структура `docs/`:** [`architecture/DESIGN.md`](../docs/architecture/DESIGN.md) (SDD), [`plans/archive/`](../docs/plans/archive/) (V2/V3 планы, CHANGES-V2), [`features/DESIGN-LEGENDARY-CARDS.md`](../docs/features/DESIGN-LEGENDARY-CARDS.md); в корне — [`README.md`](../README.md), указатель [`docs/README.md`](../docs/README.md)
- [x] **Актуализация SDD:** команды 1–3 карты и награды; базовые очки через `GamePointsService`; V11 `can_appear_on_random_cards`; сущности и API (display name, legendary upgrade, free pack opens, GET `/achievements`, порядок серий, автофинализация, Phase 4+)

### Поддержка через Telegram Forum + webhook
- [x] **Flyway V18:** `telegram_support_topic` (`telegram_user_id`, `forum_message_thread_id`)
- [x] **Backend:** `telegram.support.*` (`TELEGRAM_SUPPORT_ENABLED`, `TELEGRAM_SUPPORT_FORUM_CHAT_ID`, `TELEGRAM_SUPPORT_WEBHOOK_SECRET`); расширен `TelegramBotApiClient` (`getMe`, `createForumTopic`, `sendMessage` с `message_thread_id`, `forwardMessage`, `copyMessage`); `TelegramSupportRelayService`, `TelegramSupportUpdateService`, `TelegramSupportBotIdentity`; `POST /api/v1/telegram/webhook` + `TelegramWebhookController`; `TelegramUserRepository.findByTelegramIdForUpdate` (PESSIMISTIC_WRITE)
- [x] **Тесты:** `TelegramSupportUpdateServiceTest`, `TelegramWebhookControllerTest`; `mockito-kotlin` в test scope
- [x] Текст `/start`: мини-приложение + как писать в поддержку; перед `copyMessage` ответа админа в личку — отдельное сообщение «Ответ от поддержки:» (`TelegramSupportRelayService.SUPPORT_REPLY_HEADER`); `TelegramSupportRelayServiceTest`

### Порядок серий турнира (новые сверху)
- [x] **Backend:** `findAllByTournament_IdOrderByIdDesc` в `SeriesRepository`; `UserTournamentService.getTournament`, `SeriesService.listSeriesByTournament`
- [x] **TMA:** `SeriesPickerPage` — бейдж номера серии не зависит от порядка в списке (`gameNumFrom` или порядок по `id`)

### Очистка «призрачных» карт при смене ростера серии
- [x] **`FantasyTeamRosterPruningService.pruneInvalidCardsForSeries`:** удаляет `fantasy_team_card`, если `card_template.fantasy_player_id` больше не в `series_player` для этой серии; только пока `now <= team_deadline`; уплотняет слоты 1..n; при отсутствии карт удаляет `fantasy_team`; возвращает **`FantasyTeamRosterPruneResult`** (снятые карты по пользователям для уведомлений)
- [x] Вызовы: после **`SeriesService.assignPlayers`**; в начале **`UserFantasyTeamService`** — `getTeamForSeries`, `getTeamDetailsForSeries`, `getPublicTeamForSeries`, `getPublicTeamDetailsForSeries` (методы переведены с `readOnly` на обычную `@Transactional` из-за prune)
- [x] **`FantasyTeamRepository.findAllBySeries_IdWithCards`** (+ `JOIN FETCH telegram_user` для prune)
- [x] **Flyway V17** — одноразовая data-migration для **`series_id = 5`** (тот же алгоритм, только пока дедлайн не прошёл)
- [x] **Тесты:** `FantasyTeamRosterPruningServiceTest`, `SeriesRosterReplacementTelegramMessageTest`
- [x] **Telegram:** при **`assignPlayers`**, если у пользователей срезаны карты — **`SeriesRosterReplacementNotificationEvent`** + **`SeriesRosterReplacementNotificationListener`** (как финализация серии); текст с именами убранного и нового игрока при zip пар `tournament_player` (см. `systemPatterns.md`)

### Рассылка в Telegram из админки
- [x] **Backend:** `POST /api/v1/admin/notifications/broadcast`, `AdminBroadcastNotificationService`, `TelegramBroadcastAsyncSender`, `TelegramUserRepository.findAllTelegramIds`; тесты `AdminBroadcastNotificationServiceTest`, `AdminBroadcastApiIntegrationTest`; рассылка с `parse_mode` **MarkdownV2** в `TelegramBroadcastAsyncSender`
- [x] **Админка:** маршрут `/broadcast`, меню Broadcast, `api/notifications.ts`; подсказка по MarkdownV2 и блок preview (`telegramMarkdownV2Preview.tsx`)

### Бесплатные открытия паков (per user, per pack)
- [x] **Flyway V16:** `card_pack.free_opens_per_user`; таблица `user_card_pack_free_usage` (счётчик использованных бесплатных открытий на пару user+pack)
- [x] **Backend:** `UserStoreService` — для платного пака и положительного лимита: `INSERT … ON CONFLICT DO NOTHING` + атомарный `UPDATE … WHERE free_opens_used < limit`; иначе полное списание; для цены 0 квота не расходуется; `GET /store/packs` с `@AuthenticationPrincipal`, в ответе `freeOpensRemaining`
- [x] **Admin:** поле в `CreateCardPackRequest` / `UpdateCardPackRequest` и `CardPackDto`; формы Card packs
- [x] **TMA:** `StorePackItem.freeOpensRemaining`, подсказки и доступность кнопки, инвалидация списка паков после покупки
- [x] **Тесты:** `UserApiIntegrationTest` — сценарий с двумя бесплатными и третьей платной покупкой

### Отображаемое имя и конкурентное создание пользователя
- [x] **Flyway V14:** `telegram_user.display_name`
- [x] **Backend:** `UserProfileDto` / `UserPublicDto` + `displayName`; `PATCH /api/v1/me` (`UpdateProfileRequest`); `TelegramUserBootstrapService` (`REQUIRES_NEW`) для устойчивости к параллельному первому входу (`23505`); маппинг в лидерборде и публичной команде
- [x] **Тесты:** `UserApiIntegrationTest` (PATCH, сброс, лидерборд/owner), `UserServiceFantikiIntegrationTest` (конкурентные вызовы)
- [x] **TMA:** `formatUserDisplayName`, форма на странице магазина, `PATCH` в `api/client`

### V3 — Экономика (контракты + финализация серии) — [`PLAN-V3.md`](../docs/plans/archive/PLAN-V3.md)
- [x] **Flyway V13:** `user_card.uses_remaining`, `times_renewed`; `series.finalized`; `economy_config` + сиды; бэкофилл uses по редкости
- [x] **Backend:** `EconomyConfigService` (кэш + инвалидация из админки), `CardLifecycleService` (recycle/renew), `SeriesFinalizationService` (декремент uses + награды по лидерборду); причины `SERIES_REWARD`, `CARD_RECYCLE`, `CARD_RENEWAL`; выдача карт с uses из конфига; проверка uses при сборке команды; API user `/me/cards/{id}/recycle|renew`, `/me/economy-info`; admin `POST /series/{id}/finalize`, `GET/PUT /economy-config`
- [x] **Тесты:** `CardLifecycleServiceTest`, `SeriesFinalizationServiceTest`, интеграция admin economy config в `AdminApiIntegrationTest`
- [x] **Админка:** страница Economy, колонка Finalized в списке серий турнира, финализация на деталке серии
- [x] **TMA:** типы и API экономики, коллекция и TeamPage, страница **«Справка»** `/help` (очки, достижения из `GET /api/v1/achievements`, экономика из `GET /me/economy-info`; подписи наград за лидерборд серии — из `economy_config.description`, числа — из `value`)
- [x] **Коллекция — модалка карты:** как в лидерборде/истории фэнтези — полный список достижений, блок «Очки в сериях» из `GET /me/fantasy-teams`, детализация «По играм серии» через `GET /me/fantasy-teams/{seriesId}/details` (селектор серии, если карта участвовала в нескольких); переработка/продление в модалке и на сетке; общий компонент разбивки очков — `ScoreBreakdownBlock` (`LeaderboardPlayerTeamPage`, `FantasyHistoryPage`, `CardsPage`)

### Статистика для баланса достижений (этап 1)
- [x] **`AchievementStatisticsService`** + **POST** `/api/v1/admin/achievement-statistics/collect` — выборка игр через публичный профиль (100 игр на игрока) и `getMatch`, дедуп по матчу, агрегаты по детекторам; **`FantasyPlayerRepository.findAllPolemicaUserIds`**
- [x] Тест `AchievementStatisticsServiceTest`; исправление **`CardPackFindOrCreateTemplateIntegrationTest`** — вызов приватного метода на `AopTestUtils.getUltimateTargetObject` (иначе CGLIB-прокси с null полями)

### Отладка
- [x] **`scripts/trace_series_game_sync.py`** — пошаговая трассировка `DefaultGameSyncService` (STANDALONE: профиль + пересечение + опционально `getMatch`/префикс; POLEMICA_COMPETITION: список игр турнира + диапазон `num` + полная загрузка). Требует `ADMIN_*`; для полного прогона STANDALONE с фильтром имени — `POLEMICA_USERNAME`/`POLEMICA_PASSWORD` (как на бэкенде для sync).

### Импорт ростеров
- [x] **`scripts/import_closed_league_from_html.py`** — HTML Закрытой лиги → турнир Fantasy + фото с Полемики
- [x] **`scripts/import_tournament_from_mafoverlay.py`** — страница [MafOverlay](https://mafoverlay.ru) `…/admin/photos/tournaments/POLEMICA/{id}` → парсинг `#polemicaId`, ника, `data-photo-url` на MAIN → Admin API (`create` / `update` с `--refresh-photos`), опц. `--remove-bg` (rembg). Зависимости: `scripts/requirements-import-mafoverlay.txt`. Разметка соответствует проекту `overlay` (sibling `mafia/overlay`).

### Неполная фэнтези-команда (1–2 карты)
- [x] **Один игрок — одна карта в составе:** в `attachCards` проверяется уникальность `fantasy_player_id` (нельзя две разные `user_card` одного игрока, например COMMON+RARE); TMA `TeamPage` — нельзя выбрать вторую карту того же игрока (disabled + подсказка). Интеграционные тесты в `UserApiIntegrationTest`
- [x] **Ответ POST/PUT fantasy-team:** после `attachCards` вызываются `entityManager.flush()` и `refresh(team)` перед `team.toDto()` — иначе в JSON уходил пустой `slots` из-за кэша Hibernate по коллекции `FantasyTeam.cards`
- [x] **API / валидация:** `SubmitFantasyTeamRequest` и `UserFantasyTeamService.attachCards` — 1–3 различных карты (дубликаты `user_card` id запрещены)
- [x] **Финализация:** `SeriesFinalizationService.scaleSeriesRewardByRosterSize` — при 1 карте ⌈R/3⌉, при 2 картах ⌈2R/3⌉, при 3 — полная награда R; 0 карт или R≤0 → 0
- [x] **Лидерборд:** `findLeaderboardForSeries` — `JOIN FETCH ft.cards` для подсчёта слотов при начислении
- [x] **TMA TeamPage:** отправка при 1–3 выбранных картах; подсказка про пониженную награду
- [x] **Тесты:** `SeriesFinalizationServiceTest` — масштабирование и verify `addBalance` по неполному составу

### Скоринг и названия игр (март 2026)
- [x] **Только завершённые игры:** в `DefaultScoringService.calculateScores` учитываются строки `series_game` с кэшем, у которых в `PolemicaGame` задан `result` (победа красных/чёрных); live/незавершённые с `result == null` не попадают в сумму и не получают `scored = true`
- [x] **Синтетическое имя:** при пустом `name` из API в `DefaultGameSyncService` в БД пишется `Игра {num}` или `Игра #{id}`; общее отображение — `formatSeriesGameDisplayName` (`SeriesGameDisplayName.kt`), используется в `UserSeriesService` и `UserFantasyTeamService`

### Исправления (после релиза V2)
- [x] **Обновление фэнтези-команды (`PUT .../series/{id}/fantasy-team`):** вместо bulk `deleteAllByFantasyTeam_Id` — `findAllByFantasyTeam_Id` + `deleteAll`, затем **`fantasyTeamCardRepository.flush()`** до `team.cards.clear()`. Иначе lazy-инициализация коллекции после отложенного DELETE снова поднимала старые строки из БД, а INSERT новых слотов давал `23505` на `fantasy_team_card_fantasy_team_id_slot_key`
- [x] **Повторный расчёт скоринга серии (`POST .../calculate-scores`):** после `card.gameScores.clear()` вызывается `fantasyTeamRepository.flush()`, чтобы DELETE сирот ушёл в БД до INSERT новых строк с тем же `(fantasy_team_card_id, series_game_id)` — иначе Hibernate мог выполнять INSERT раньше DELETE и ловить `23505` на `fantasy_team_card_game_score_*_key`
- [x] **Дубликаты достижений на автокартах:** `CardPackService.findOrCreateCardTemplate` сравнивает набор `achievement_id` через запрос к БД (не через in-memory `ct.achievements`); после сохранения `CardTemplateAchievement` строка добавляется в `saved.achievements`; в `UserCardItemMapping` дедуп по `achievementId` для выдачи; Flyway `V12` — удаление дублей в `card_template_achievement` + уникальный индекс `(card_template_id, achievement_id)`; админка `addAchievement` — отказ с `409 CONFLICT` при повторной привязке

### Планировщик sync + скоринга (активные серии)
- [x] **`ActiveSeriesSyncScheduler`** — каждые 10 минут для серий `ACTIVE`/`SCORING`, не `finalized`; в тестах отключение `spring.task.scheduling.enabled: false`

### Инфраструктура
- [x] **VPS:** `fantasy.maftourbot.ru` — TMA; `admin.fantasy.maftourbot.ru` — админ SPA; Docker Compose prod (`docker-compose.prod.yml`), nginx + Let’s Encrypt; см. [`deploy/nginx-fantasy.maftourbot.ru.conf`](../deploy/nginx-fantasy.maftourbot.ru.conf), [`deploy/nginx-admin.fantasy.maftourbot.ru.conf`](../deploy/nginx-admin.fantasy.maftourbot.ru.conf)
- [x] **VPS — репозиторий:** `~/polemica-fantasy` на `mafia@51.250.18.236` — **git** (ветка `master`, remote по SSH), не «голая» копия без `.git`; обновление кода прежде всего **`git pull`**, не только rsync
- [x] Gradle 9.0.0 + Kotlin 2.3.0 + JDK 21 — скелет проекта
- [x] Дизайн-документ [`docs/architecture/DESIGN.md`](../docs/architecture/DESIGN.md) — SDD (глобальные игроки, карточки, API, V3+, легендарки)
- [x] Memory Bank инициализирован
- [x] Docker Compose (PostgreSQL 16 + MinIO + backend), корневой `docker-compose.yml`, `.env.example`
- [x] Multi-stage `polemica-fantasy-backend/Dockerfile`, `.dockerignore`
- [x] CI: `.github/workflows/docker-publish.yml` (ветка **`master`**, GHCR, cosign keyless с `continue-on-error`, ручной deploy по SSH)
- [x] CI: `.github/workflows/deploy-vps.yml` — push в **`master`** или **workflow_dispatch** → SSH на VPS: `git pull`, сборка webapp/admin, `docker compose -f docker-compose.prod.yml up -d --build`, `sudo rsync` в `/var/www/fantasy.maftourbot.ru` и `/var/www/admin.fantasy.maftourbot.ru` (секреты `SSH_HOST`, `SSH_USER`, `SSH_KEY`, опционально `DEPLOY_PATH`)

### Backend (Agent A1 — Foundation)
- [x] Spring Boot 3.4.2, Spring Data JPA, Flyway, Security, Actuator + Prometheus (management порт 8081)
- [x] AWS SDK v2 S3, `S3Config` (path-style, MinIO), `ImageStorageService` (upload/delete, ключи players/cards), создание bucket при старте (`S3BucketInitializer`, отключается в профиле `test`)
- [x] Flyway `V1__initial_schema.sql` — все таблицы из DESIGN §4
- [x] Flyway `V3__fantasy_player_global_cards.sql` — таблица `fantasy_player`, карточки на глобального игрока, `tournament_player` только связь с турниром
- [x] JPA entities + enum-классы (`Rarity` с `scoreModifier`, `TournamentStatus`, `SeriesStatus`, …); **V2 (B1):** enum `AchievementType` заменён справочником `Achievement` + `card_template_achievement.achievement_id` FK
- [x] `application.yml` по DESIGN §12.5 + профиль `dev`, опция `s3.ensure-bucket-on-startup`
- [x] Три `SecurityFilterChain` @Order(1–3): admin Basic Auth; user `/api/v1/**` без `/api/v1/admin/**` — `TelegramAuthFilter` + authenticated; остальное — `permitAll`
- [x] Зависимость `polemica-library:1.8.2` (Maven Central + `mavenLocal()`); `ProfileGameRow.mmr` — десериализация числа или вложенного объекта (публичный профиль)
- [x] Интеграционный тест контекста: Testcontainers PostgreSQL 16

### Backend (Agent A3 — Admin API)
- [x] **GET `/api/v1/admin/users`:** список пользователей; с `tournamentId` + `seriesId` — поле `cardsInSeries` (подсчёт `user_card` по ростеру серии); `AdminUserListService`, `TelegramUserRepository.findAllWithCardsInSeriesCount`; тесты в `AdminApiIntegrationTest`
- [x] Репозитории JPA для admin-сущностей; фильтр списка шаблонов карточек (опционально `tournamentId` через участие игрока в турнире / `fantasyPlayerId` / rarity)
- [x] Сервисы: `TournamentService`, `SeriesService`, `CardService`, `CardPackService`; валидация multipart изображений; выдача карт и открытие паков
- [x] Контроллеры: `TournamentAdminController`, `SeriesAdminController`, `CardAdminController`; DTO + Jakarta Validation; `GlobalExceptionHandler`
- [x] Тесты: валидация конфигов паков (без `probability`), интеграционные сценарии (Basic Auth, give-cards)
- [x] Read API для админки (A6): `GET .../tournaments/{tournamentId}/series`, `GET .../series/{id}`, `GET .../card-packs` (+ опциональный `tournamentId`); `CardPackRepository` list methods; интеграционный тест в `AdminApiIntegrationTest`
- [x] `SeriesDto` включает `tournamentPlayerIds` (состав серии для админки); `assignPlayers`: `flush()` после bulk-delete, иначе UNIQUE `(series_id, tournament_player_id)` при сохранении

### Backend (Agent A4 — Scoring + Game Sync)
- [x] `PolemicaProperties`, `PolemicaConfig` — bean `PolemicaClient` (`PolemicaClientImpl` + `Jackson2ObjectMapperBuilder`)
- [x] `PolemicaIntegrationService` — пагинация `getProfileGames`, `getMatch`, JSON в `JsonNode`
- [x] `DefaultGameSyncService` — игроки серии → профильные match id → `getMatch` → фильтр по `namePrefix` → upsert `SeriesGame`, без кредов Polemica → HTTP 400
- [x] `AchievementDetector` + 8 компонентов (логика как в polemica-achievement-service: `sniper`, `winThreeToThree`, …) + `AchievementDetectorRegistry` (**V2:** идентификаторы — строки `achievement.id`, не enum)
- [x] `DefaultScoringService` — очки по формуле DESIGN §5.4, `FantasyTeamRepository.findAllWithCardsForScoring`; базовые очки из `GamePointsService` (polemica-library), префетч по `polemica_game_id` при расчёте серии
- [x] Flyway `V2__series_game_unique.sql`
- [x] Flyway `V4__tournament_kind_competition.sql` — `tournament.kind`, `tournament.polemica_competition_id`, `series.game_num_from` / `game_num_to`, `series.name_prefix` nullable
- [x] `TournamentKind`, ветвление `DefaultGameSyncService` (STANDALONE vs POLEMICA_COMPETITION); `PolemicaIntegrationService` — competitions + `getGamesFromCompetition` / `getGameFromCompetition`; `PolemicaAdminController` — read-only список/деталь Competition
- [x] Тесты: `AchievementDetectorRegistryTest`; admin integration — sync без кредов → 400

### Backend (Agent A5 — User API + Telegram)
- [x] `TelegramProperties`, `TelegramInitDataValidator` (HMAC по доке Telegram Web Apps), `TelegramAuthentication` / principal `TelegramUser`
- [x] `UserService` (профиль + `getOrCreateAndUpdateProfile`), рефактор `CardService` на `UserService`
- [x] User endpoints по DESIGN §6.1: турниры (ACTIVE), турнир + серии, серия (игроки, игры), лидерборд, `/me`, `/me/cards`, fantasy team CRUD; дополнительно `GET /tournaments/{id}/participants` (ростер турнира для TMA)
- [x] Репозитории: `UserCardRepository` фильтры, `FantasyTeamRepository` + leaderboard, `FantasyTeamCardRepository`, `SeriesRepository` / `SeriesPlayerRepository` доп. запросы
- [x] Тесты: `TelegramInitDataValidatorTest`, `UserApiIntegrationTest` (401 без заголовка, 200 `/me` с подписанным initData)

### Frontend (TMA)
- [x] Проект `polemica-fantasy-webapp/` (Vite + React 19 + TS)
- [x] `@telegram-apps/sdk` + `InitDataProvider` (`retrieveRawInitData`, опционально `VITE_DEV_INIT_DATA`)
- [x] Страницы: турниры, хаб турнира, выбор серии, турнирный лидерборд (Общий + по сериям), правила, история фэнтези, участники, серия, сборка команды, лидерборд серии, коллекция (`?tournamentId`, фильтр турнира — select по активным турнирам API)
- [x] UI: тёмная тема, градиентные CTA, карточки по редкости (фото игрока с fallback на арт шаблона, цветная рамка по редкости в коллекции/команде/истории фэнтези), `PageHeader` / бейджи статусов
- [x] TanStack Query, React Router, proxy `/api` в `vite.config.ts`
- [x] **Agent B7:** анимация открытия пака — `components/PackOpening.tsx` + стили `pf-pack-open-*` в `index.css`; интеграция в `StorePage` (имя пака из кэша query), кнопка «В коллекцию» → `/cards`

### Frontend (Admin)
- [x] Проект `polemica-fantasy-admin/` (Vite + React 19 + TS + Ant Design 6 + TanStack Query + React Router 7)
- [x] **Users overview:** маршрут `/users` — таблица пользователей, выбор турнира и серии, колонки актуального баланса фантиков и числа карт по серии; `GET /api/v1/admin/users` (`AdminUserListItemDto.fantiki`, в native-запросе с фильтром серии — `tu.fantiki`); `api/usersList.ts`
- [x] Турниры: список, create/edit, деталь — игроки (add/remove/photo), серии (список + create)
- [x] Серия: редактирование полей, assign players, sync games, calculate scores
- [x] Шаблоны карт и паки: CRUD-операции, фильтры, загрузка изображения карты, добавление achievement
- [x] User tools: give cards, open pack по `telegramUserId`
- [x] **Agent B5:** страница `/achievements` (каталог + редактирование через модалку); паки V2 в UI (auto, фантики, пул игроков турнира, подсказка auto); User Tools — начисление фантиков; шаблоны — выбор достижения из `GET /admin/achievements`, без bonus в форме; API `achievements.ts`, `users.ts`, `getCardPack` / `updateCardPackPlayers` в `packs.ts`

### Backend (Agent B3 — Фантики + Store API)
- [x] `TelegramUserRepository.addFantiki` / `deductFantikiIfSufficient` (`@Modifying` queries)
- [x] `UserService`: баланс + аудит `fantiki_transaction`; `grantFantikiByTelegramId`; INITIAL при регистрации
- [x] `UserProfileDto.fantiki`; `GiveFantikiRequest`; `UserAdminController`; `StoreController` + `UserStoreService`; DTO `StorePackItemDto`, `BuyPackResponseDto`
- [x] `CardPackRepository.findAllByActiveTrueAndPriceFantikiGreaterThanEqualOrderByIdAsc`
- [x] Тесты: `UserServiceFantikiIntegrationTest`, расширены `AdminApiIntegrationTest` / `UserApiIntegrationTest`

### Backend (Agent B1 — Foundation V2: schema + entities)
- [x] Flyway `V5__fantiki.sql` — `telegram_user.fantiki`, `fantiki_transaction`
- [x] Flyway `V6__achievement_system.sql` — `achievement`, `achievement_applicable_role`, seed 9 достижений, `card_template_achievement` → FK `achievement_id`, `bonus_points` nullable
- [x] Flyway `V7__auto_packs.sql` — колонки `card_pack` (auto_generated, price_fantiki, use_all_tournament_players), `card_pack_player`, drop `card_pack_rarity_config.probability`
- [x] Flyway `V8__game_score_details.sql` — `fantasy_team_card_game_score`, `fantasy_team_card_game_achievement`
- [x] Flyway `V10__replace_achievement_catalog.sql` — замена справочника достижений (8 шт., `voteForBlack` = `MULTIPLE_PER_GAME`; очистка `fantasy_team_card_game_achievement` и `card_template_achievement` перед вставкой)
- [x] Flyway `V11__achievement_all_random_cards.sql` — `UPDATE achievement SET can_appear_on_random_cards = TRUE` для всего каталога
- [x] JPA: `Achievement`, `AchievementApplicableRole`, `FantikiTransaction`, `CardPackPlayer`, `FantasyTeamCardGameScore`, `FantasyTeamCardGameAchievement`; обновлены `TelegramUser`, `CardTemplateAchievement`, `CardPack`, `CardPackRarityConfig`, `FantasyTeamCard`; репозитории для новых сущностей
- [x] Admin/TMA типы и UI под `achievementId` / `achievementName` и паки без `probability`

### polemica-library (опционально, не блокер)
- [ ] Отдельный `getPlayerGames` / фильтрация на сервере — сейчас используется пагинация `getProfileGames` из артефакта 1.8.2

## Известные проблемы
- Детекторы достижений зависят от полноты модели `PolemicaGame` (голосования, кики, лучший ход); при расхождениях с Полемикой уточнять по реальным логам API

## Технический долг
- TMA SDK: npm предупреждает о deprecated пакетах `@telegram-apps/*` в пользу `@tma.js/*` — миграция по желанию
- GitHub Actions: cosign может требовать доп. настройку OIDC — шаг помечен `continue-on-error`
- Docker Compose: переменная `POSTGRES_HOST_PORT` (по умолчанию 5433), если порт занят
