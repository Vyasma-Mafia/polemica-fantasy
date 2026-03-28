# Progress

## Что реализовано

### Инфраструктура
- [x] **VPS:** `fantasy.maftourbot.ru` — TMA; `admin.fantasy.maftourbot.ru` — админ SPA; Docker Compose prod (`docker-compose.prod.yml`), nginx + Let’s Encrypt; см. [`deploy/nginx-fantasy.maftourbot.ru.conf`](../deploy/nginx-fantasy.maftourbot.ru.conf), [`deploy/nginx-admin.fantasy.maftourbot.ru.conf`](../deploy/nginx-admin.fantasy.maftourbot.ru.conf)
- [x] Gradle 9.0.0 + Kotlin 2.3.0 + JDK 21 — скелет проекта
- [x] Дизайн-документ (`DESIGN.md`) — актуализирован под глобальных игроков и карточки (Fantasy Player, §4 / API / S3)
- [x] Memory Bank инициализирован
- [x] Docker Compose (PostgreSQL 16 + MinIO + backend), корневой `docker-compose.yml`, `.env.example`
- [x] Multi-stage `polemica-fantasy-backend/Dockerfile`, `.dockerignore`
- [x] CI: `.github/workflows/docker-publish.yml` (GHCR, cosign keyless с `continue-on-error`, ручной deploy по SSH)

### Backend (Agent A1 — Foundation)
- [x] Spring Boot 3.4.2, Spring Data JPA, Flyway, Security, Actuator + Prometheus (management порт 8081)
- [x] AWS SDK v2 S3, `S3Config` (path-style, MinIO), `ImageStorageService` (upload/delete, ключи players/cards), создание bucket при старте (`S3BucketInitializer`, отключается в профиле `test`)
- [x] Flyway `V1__initial_schema.sql` — все таблицы из DESIGN §4
- [x] Flyway `V3__fantasy_player_global_cards.sql` — таблица `fantasy_player`, карточки на глобального игрока, `tournament_player` только связь с турниром
- [x] JPA entities + enum-классы (`Rarity`, `TournamentStatus`, `SeriesStatus`, `AchievementType`)
- [x] `application.yml` по DESIGN §12.5 + профиль `dev`, опция `s3.ensure-bucket-on-startup`
- [x] Три `SecurityFilterChain` @Order(1–3): admin Basic Auth; user `/api/v1/**` без `/api/v1/admin/**` — `TelegramAuthFilter` + authenticated; остальное — `permitAll`
- [x] Зависимость `polemica-library:1.8.1` (Maven Central + `mavenLocal()`)
- [x] Интеграционный тест контекста: Testcontainers PostgreSQL 16

### Backend (Agent A3 — Admin API)
- [x] Репозитории JPA для admin-сущностей; фильтр списка шаблонов карточек (опционально `tournamentId` через участие игрока в турнире / `fantasyPlayerId` / rarity)
- [x] Сервисы: `TournamentService`, `SeriesService`, `CardService`, `CardPackService`; валидация multipart изображений; выдача карт и открытие паков
- [x] Контроллеры: `TournamentAdminController`, `SeriesAdminController`, `CardAdminController`; DTO + Jakarta Validation; `GlobalExceptionHandler`
- [x] Тесты: вероятности паков, интеграционные сценарии (Basic Auth, give-cards)
- [x] Read API для админки (A6): `GET .../tournaments/{tournamentId}/series`, `GET .../series/{id}`, `GET .../card-packs` (+ опциональный `tournamentId`); `CardPackRepository` list methods; интеграционный тест в `AdminApiIntegrationTest`

### Backend (Agent A4 — Scoring + Game Sync)
- [x] `PolemicaProperties`, `PolemicaConfig` — bean `PolemicaClient` (`PolemicaClientImpl` + `Jackson2ObjectMapperBuilder`)
- [x] `PolemicaIntegrationService` — пагинация `getProfileGames`, `getMatch`, JSON в `JsonNode`
- [x] `DefaultGameSyncService` — игроки серии → профильные match id → `getMatch` → фильтр по `namePrefix` → upsert `SeriesGame`, без кредов Polemica → HTTP 400
- [x] `AchievementDetector` + 9 компонентов + `AchievementDetectorRegistry`
- [x] `DefaultScoringService` — очки по формуле DESIGN §5.3, `FantasyTeamRepository.findAllWithCardsForScoring`
- [x] Flyway `V2__series_game_unique.sql`
- [x] Flyway `V4__tournament_kind_competition.sql` — `tournament.kind`, `tournament.polemica_competition_id`, `series.game_num_from` / `game_num_to`, `series.name_prefix` nullable
- [x] `TournamentKind`, ветвление `DefaultGameSyncService` (STANDALONE vs POLEMICA_COMPETITION); `PolemicaIntegrationService` — competitions + `getGamesFromCompetition` / `getGameFromCompetition`; `PolemicaAdminController` — read-only список/деталь Competition
- [x] Тесты: `AchievementDetectorRegistryTest`, `WonGameDetectorTest`; admin integration — sync без кредов → 400

### Backend (Agent A5 — User API + Telegram)
- [x] `TelegramProperties`, `TelegramInitDataValidator` (HMAC по доке Telegram Web Apps), `TelegramAuthentication` / principal `TelegramUser`
- [x] `UserService` (профиль + `getOrCreateAndUpdateProfile`), рефактор `CardService` на `UserService`
- [x] User endpoints по DESIGN §6.1: турниры (ACTIVE), турнир + серии, серия (игроки, игры), лидерборд, `/me`, `/me/cards`, fantasy team CRUD
- [x] Репозитории: `UserCardRepository` фильтры, `FantasyTeamRepository` + leaderboard, `FantasyTeamCardRepository`, `SeriesRepository` / `SeriesPlayerRepository` доп. запросы
- [x] Тесты: `TelegramInitDataValidatorTest`, `UserApiIntegrationTest` (401 без заголовка, 200 `/me` с подписанным initData)

### Frontend (TMA)
- [x] Проект `polemica-fantasy-webapp/` (Vite + React 19 + TS)
- [x] `@telegram-apps/sdk` + `InitDataProvider` (`retrieveRawInitData`, опционально `VITE_DEV_INIT_DATA`)
- [x] Страницы: турниры, турнир → серии, серия, коллекция, сборка команды, лидерборд
- [x] TanStack Query, React Router, proxy `/api` в `vite.config.ts`

### Frontend (Admin)
- [x] Проект `polemica-fantasy-admin/` (Vite + React 19 + TS + Ant Design 6 + TanStack Query + React Router 7)
- [x] Турниры: список, create/edit, деталь — игроки (add/remove/photo), серии (список + create)
- [x] Серия: редактирование полей, assign players, sync games, calculate scores
- [x] Шаблоны карт и паки: CRUD-операции, фильтры, загрузка изображения карты, добавление achievement
- [x] User tools: give cards, open pack по `telegramUserId`

### polemica-library (опционально, не блокер)
- [ ] Отдельный `getPlayerGames` / фильтрация на сервере — сейчас используется пагинация `getProfileGames` из артефакта 1.8.1

## Известные проблемы
- Детекторы сложных достижений (`VOTED_OUT_BLACK`, `CORRECT_GUESS`) зависят от полноты модели `PolemicaGame`; при необходимости уточнить по реальным логам API
- Точный список достижений не определён (в БД заложены типы из DESIGN §5.2)

## Технический долг
- TMA SDK: npm предупреждает о deprecated пакетах `@telegram-apps/*` в пользу `@tma.js/*` — миграция по желанию
- GitHub Actions: cosign может требовать доп. настройку OIDC — шаг помечен `continue-on-error`
- Docker Compose: переменная `POSTGRES_HOST_PORT` (по умолчанию 5433), если порт занят
