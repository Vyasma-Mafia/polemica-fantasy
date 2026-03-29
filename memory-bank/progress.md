# Progress

## Что реализовано

### Инфраструктура
- [x] **VPS:** `fantasy.maftourbot.ru` — TMA; `admin.fantasy.maftourbot.ru` — админ SPA; Docker Compose prod (`docker-compose.prod.yml`), nginx + Let’s Encrypt; см. [`deploy/nginx-fantasy.maftourbot.ru.conf`](../deploy/nginx-fantasy.maftourbot.ru.conf), [`deploy/nginx-admin.fantasy.maftourbot.ru.conf`](../deploy/nginx-admin.fantasy.maftourbot.ru.conf)
- [x] **VPS — репозиторий:** `~/polemica-fantasy` на `mafia@51.250.18.236` — **git** (ветка `master`, remote по SSH), не «голая» копия без `.git`; обновление кода прежде всего **`git pull`**, не только rsync
- [x] Gradle 9.0.0 + Kotlin 2.3.0 + JDK 21 — скелет проекта
- [x] Дизайн-документ (`DESIGN.md`) — актуализирован под глобальных игроков и карточки (Fantasy Player, §4 / API / S3)
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
- [x] Зависимость `polemica-library:1.8.1` (Maven Central + `mavenLocal()`)
- [x] Интеграционный тест контекста: Testcontainers PostgreSQL 16

### Backend (Agent A3 — Admin API)
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
- [x] `AchievementDetector` + 9 компонентов + `AchievementDetectorRegistry` (**V2:** идентификаторы — строки `achievement.id`, не enum)
- [x] `DefaultScoringService` — очки по формуле DESIGN §5.3, `FantasyTeamRepository.findAllWithCardsForScoring`
- [x] Flyway `V2__series_game_unique.sql`
- [x] Flyway `V4__tournament_kind_competition.sql` — `tournament.kind`, `tournament.polemica_competition_id`, `series.game_num_from` / `game_num_to`, `series.name_prefix` nullable
- [x] `TournamentKind`, ветвление `DefaultGameSyncService` (STANDALONE vs POLEMICA_COMPETITION); `PolemicaIntegrationService` — competitions + `getGamesFromCompetition` / `getGameFromCompetition`; `PolemicaAdminController` — read-only список/деталь Competition
- [x] Тесты: `AchievementDetectorRegistryTest`, `WonGameDetectorTest`; admin integration — sync без кредов → 400

### Backend (Agent A5 — User API + Telegram)
- [x] `TelegramProperties`, `TelegramInitDataValidator` (HMAC по доке Telegram Web Apps), `TelegramAuthentication` / principal `TelegramUser`
- [x] `UserService` (профиль + `getOrCreateAndUpdateProfile`), рефактор `CardService` на `UserService`
- [x] User endpoints по DESIGN §6.1: турниры (ACTIVE), турнир + серии, серия (игроки, игры), лидерборд, `/me`, `/me/cards`, fantasy team CRUD; дополнительно `GET /tournaments/{id}/participants` (ростер турнира для TMA)
- [x] Репозитории: `UserCardRepository` фильтры, `FantasyTeamRepository` + leaderboard, `FantasyTeamCardRepository`, `SeriesRepository` / `SeriesPlayerRepository` доп. запросы
- [x] Тесты: `TelegramInitDataValidatorTest`, `UserApiIntegrationTest` (401 без заголовка, 200 `/me` с подписанным initData)

### Frontend (TMA)
- [x] Проект `polemica-fantasy-webapp/` (Vite + React 19 + TS)
- [x] `@telegram-apps/sdk` + `InitDataProvider` (`retrieveRawInitData`, опционально `VITE_DEV_INIT_DATA`)
- [x] Страницы: турниры, хаб турнира, выбор серии, турнирный лидерборд (Общий + по сериям), правила, история фэнтези, участники, серия, сборка команды, лидерборд серии, коллекция (`?tournamentId`)
- [x] UI: тёмная тема, градиентные CTA, карточки по редкости (фото игрока с fallback на арт шаблона, цветная рамка по редкости в коллекции/команде/истории фэнтези), `PageHeader` / бейджи статусов
- [x] TanStack Query, React Router, proxy `/api` в `vite.config.ts`
- [x] **Agent B7:** анимация открытия пака — `components/PackOpening.tsx` + стили `pf-pack-open-*` в `index.css`; интеграция в `StorePage` (имя пака из кэша query), кнопка «В коллекцию» → `/cards`

### Frontend (Admin)
- [x] Проект `polemica-fantasy-admin/` (Vite + React 19 + TS + Ant Design 6 + TanStack Query + React Router 7)
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
- [x] JPA: `Achievement`, `AchievementApplicableRole`, `FantikiTransaction`, `CardPackPlayer`, `FantasyTeamCardGameScore`, `FantasyTeamCardGameAchievement`; обновлены `TelegramUser`, `CardTemplateAchievement`, `CardPack`, `CardPackRarityConfig`, `FantasyTeamCard`; репозитории для новых сущностей
- [x] Admin/TMA типы и UI под `achievementId` / `achievementName` и паки без `probability`

### polemica-library (опционально, не блокер)
- [ ] Отдельный `getPlayerGames` / фильтрация на сервере — сейчас используется пагинация `getProfileGames` из артефакта 1.8.1

## Известные проблемы
- Детекторы сложных достижений (`VOTED_OUT_BLACK`, `CORRECT_GUESS`) зависят от полноты модели `PolemicaGame`; при необходимости уточнить по реальным логам API
- Точный список достижений не определён (в БД заложены типы из DESIGN §5.2)

## Технический долг
- TMA SDK: npm предупреждает о deprecated пакетах `@telegram-apps/*` в пользу `@tma.js/*` — миграция по желанию
- GitHub Actions: cosign может требовать доп. настройку OIDC — шаг помечен `continue-on-error`
- Docker Compose: переменная `POSTGRES_HOST_PORT` (по умолчанию 5433), если порт занят
