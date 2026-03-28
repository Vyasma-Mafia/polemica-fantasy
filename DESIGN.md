# Polemica Fantasy — System Design Document

## 1. Overview

Polemica Fantasy — сервис для создания фэнтези-команд по игре «спортивная мафия».
Пользователи собирают карточки игроков с различными уровнями редкости и бонусами за достижения,
формируют команды из 3 карточек на серию игр и соревнуются друг с другом по набранным очкам.

Данные об играх и игроках поступают из внешнего API **Polemica** через библиотеку `polemica-library`.

---

## 2. Glossary

| Термин | Описание |
|--------|----------|
| **Tournament** | Собственная сущность сервиса. Объединяет игроков и серии. Имеет **`kind`** (`STANDALONE` или `POLEMICA_COMPETITION`). Турниры без привязки к Полемике — это `STANDALONE` (режим по умолчанию для существующих данных). |
| **TournamentKind** | `STANDALONE` — игры серии подбираются по истории участников и префиксу названия (`name_prefix`), как в исходном дизайне. `POLEMICA_COMPETITION` — турнир привязан к соревнованию Polemica: на турнире хранится `polemica_competition_id`; все серии этого турнира задают только **диапазон номеров игр** (`game_num_from` … `game_num_to`, inclusive по полю `num` в API соревнования). **Внутри одного турнира все серии одного типа** (нельзя смешивать prefix-серии и competition-серии). |
| **Series** | Набор игр внутри турнира. При `STANDALONE`: обязателен `name_prefix` для матчинга названий игр. При `POLEMICA_COMPETITION`: префикс не используется для sync (может быть пустым/NULL); обязательны `game_num_from` и `game_num_to`. |
| **Game** | Конкретная игра из Полемики (`PolemicaGame`), привязанная к серии. |
| **Fantasy Player** | Глобальный игрок сервиса: один на каждый `polemica_user_id` (ник, фото). Создаётся или подставляется при добавлении игрока в турнир. Не привязан к одному турниру. |
| **Tournament Player** | Участие конкретного fantasy player в ростере конкретного турнира (связь турнир ↔ игрок). |
| **Card Template** | Определение карточки: привязка к **fantasy player** (не к турниру) + уровень редкости + набор достижений с бонусными очками. Один и тот же шаблон может использоваться в разных турнирах, если игрок включён в состав серии. |
| **User Card** | Конкретный экземпляр карточки, принадлежащий пользователю. У одного пользователя может быть несколько одинаковых карточек. |
| **Card Pack** | Набор для рандомного получения карточек с настраиваемым распределением по редкостям. |
| **Fantasy Team** | Команда из 3 карточек, выставленная пользователем на серию. |
| **Achievement** | Тип игрового события, за которое карточка получает бонусные очки (найденный шериф, проверка чёрного и т.д.). |
| **TMA** | Telegram Mini App — пользовательский интерфейс внутри Telegram. |

---

## 3. Architecture

### 3.1 High-Level Architecture

```
┌─────────────────────┐     ┌─────────────────────┐
│  Telegram Mini App   │     │   Admin Web Panel    │
│  (React + TS + TMA)  │     │   (React + TS + Ant) │
└─────────┬───────────┘     └─────────┬───────────┘
          │                           │
          │  REST API                 │  REST API
          ▼                           ▼
┌─────────────────────────────────────────────────┐
│              Spring Boot Backend                 │
│              (Kotlin / JDK 21)                   │
│                                                  │
│  ┌───────────┐  ┌───────────┐  ┌──────────────┐ │
│  │  User API  │  │ Admin API │  │ Scoring      │ │
│  │ Controller │  │Controller │  │ Engine       │ │
│  └─────┬─────┘  └─────┬─────┘  └──────┬───────┘ │
│        │              │               │          │
│        └──────┬───────┘               │          │
│               ▼                       ▼          │
│        ┌─────────────┐    ┌────────────────┐     │
│        │  Service     │    │  Polemica      │     │
│        │  Layer       │◄───│  Client        │     │
│        └──┬───────┬───┘    └───────┬────────┘     │
│           │       │                │              │
└───────────┼───────┼────────────────┼──────────────┘
            │       │                │
            ▼       ▼                ▼
     ┌──────────┐ ┌──────────┐ ┌─────────────┐
     │PostgreSQL│ │ S3       │ │ Polemica    │
     │+ Flyway  │ │(images)  │ │ API         │
     └──────────┘ └──────────┘ └─────────────┘
```

### 3.1.1 S3 Storage

S3-совместимое хранилище используется для фотографий игроков, отображаемых на карточках.

- Админ загружает фото через Admin API → бэкенд сохраняет файл в S3 → URL записывается в `FantasyPlayer.photo_url` и/или `CardTemplate.image_url`
- Фронтенды получают URL изображений из API и загружают их напрямую из S3 (публичный доступ на чтение)
- S3-провайдер не фиксирован: AWS S3, MinIO (для dev), Yandex Object Storage, и т.д. — используется стандартный AWS SDK

**TMA (`polemica-fantasy-webapp`) — отображение карточки пользователя:**

- Основное изображение — фото fantasy-игрока (`playerPhotoUrl` в ответе `GET /me/cards`, источник — `FantasyPlayer.photo_url`). Если фото нет, показывается арт шаблона (`imageUrl` → `CardTemplate.image_url`).
- Рамка карточки в UI стилизуется по редкости (`COMMON` / `RARE` / `EPIC` / `LEGENDARY`): коллекция, сборка команды, история фэнтези (включая модалку деталей). На бэкенде контракт не менялся — поля уже отдаёт `UserCardItemDto`.

### 3.2 Module Structure

```
polemica-fantasy/
├── DESIGN.md                        # This document
├── memory-bank/                     # Project context for AI agents
├── docker-compose.yml               # Dev: PostgreSQL + MinIO + backend
├── .env.example                     # Template for env variables
├── .github/
│   └── workflows/
│       └── docker-publish.yml       # CI/CD: build, push to GHCR, deploy
├── polemica-fantasy-backend/        # Spring Boot + Kotlin
│   ├── Dockerfile                   # Multi-stage: gradle build → JDK 21 runtime
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/io/github/mralex1810/fantasy/
│       │   │   ├── FantasyApplication.kt
│       │   │   ├── config/          # Spring, Security, Polemica, S3 config
│       │   │   ├── auth/            # Telegram initData + admin auth
│       │   │   ├── entity/          # JPA entities
│       │   │   ├── repository/      # Spring Data JPA repositories
│       │   │   ├── dto/             # Request / Response DTOs
│       │   │   ├── service/         # Business logic + ImageStorageService
│       │   │   ├── scoring/         # Achievement detection + scoring
│       │   │   ├── polemica/        # Polemica integration layer
│       │   │   └── controller/
│       │   │       ├── user/        # User-facing endpoints
│       │   │       └── admin/       # Admin endpoints
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/    # Flyway V*.sql
│       └── test/
├── polemica-fantasy-webapp/         # User-facing TMA (React + TS)
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
└── polemica-fantasy-admin/          # Admin panel (React + TS + Ant Design)
    ├── package.json
    ├── vite.config.ts
    └── src/
```

---

## 4. Domain Model

### 4.1 Entity Relationship Diagram

```
FantasyPlayer (1) ──── (*) TournamentPlayer (*) ──── (1) Tournament
        │                        │
        │ (*)                    │ referenced by
        │                        ▼
        │                  SeriesPlayer
        │                        │
        ▼                        │
CardTemplate (*)                 │
    │         │                  │
    │ (*)     │ (1)              │
    ▼         ▼                  ▼
CardTemplateAchievement    Series (*)
                                │
                                ▼
                           SeriesGame

TelegramUser (1) ──── (*) UserCard ──── (*) FantasyTeamCard
    │                                          │
    │ (1)                                      │ (*)
    ▼                                          ▼
FantasyTeam (*) ──────────────── (1) FantasyTeam
                                     (series_id)

CardPack (1) ──── (*) CardPackRarityConfig
```

**Инварианты:** шаблон карточки (`CardTemplate`) ссылается на `FantasyPlayer`, а не на `TournamentPlayer`. Участие в турнире и серии идёт через `TournamentPlayer` / `SeriesPlayer`. Сборка фэнтези-команды на серию допускает только карточки игроков, которые назначены на эту серию (тот же `FantasyPlayer`, что и у соответствующего `SeriesPlayer`). Все серии одного турнира соответствуют одному `TournamentKind` родителя.

### 4.2 Core Entities

#### TelegramUser
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| telegram_id | BIGINT | Unique, Telegram user ID |
| username | VARCHAR | Telegram username |
| first_name | VARCHAR | Display name |
| created_at | TIMESTAMP | Registration time |

#### Tournament
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| name | VARCHAR | Tournament name |
| description | TEXT | Optional description |
| status | VARCHAR | DRAFT / ACTIVE / FINISHED |
| kind | VARCHAR | `STANDALONE` или `POLEMICA_COMPETITION` (NOT NULL, default `STANDALONE`) |
| polemica_competition_id | BIGINT | FK-логика на соревнование Polemica; заполнен iff `kind = POLEMICA_COMPETITION` (UNIQUE среди не-NULL, если один fantasy-контекст на соревнование) |
| created_at | TIMESTAMP | |

#### FantasyPlayer
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| polemica_user_id | BIGINT | Player ID in Polemica (unique) |
| nickname | VARCHAR | Display name |
| photo_url | VARCHAR | Player photo URL in S3 (nullable) |

#### TournamentPlayer
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| tournament_id | BIGINT | FK → Tournament |
| fantasy_player_id | BIGINT | FK → FantasyPlayer |
| | | Unique pair `(tournament_id, fantasy_player_id)` — один и тот же Polemica-игрок не дублируется в одном турнире |

#### Series
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| tournament_id | BIGINT | FK → Tournament |
| name | VARCHAR | Series display name |
| name_prefix | VARCHAR | Prefix for matching game names (используется при `tournament.kind = STANDALONE`; иначе NULL/не используется для sync) |
| game_num_from | BIGINT | Нижняя граница номера игры в соревновании (inclusive); используется при `POLEMICA_COMPETITION` |
| game_num_to | BIGINT | Верхняя граница номера игры в соревновании (inclusive); используется при `POLEMICA_COMPETITION` |
| status | VARCHAR | UPCOMING / ACTIVE / SCORING / FINISHED |
| starts_at | TIMESTAMP | When series starts |
| team_deadline | TIMESTAMP | Deadline for submitting/editing fantasy teams |

#### SeriesPlayer
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| series_id | BIGINT | FK → Series |
| tournament_player_id | BIGINT | FK → TournamentPlayer |

#### SeriesGame
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| series_id | BIGINT | FK → Series |
| polemica_game_id | BIGINT | Game ID in Polemica |
| game_name | VARCHAR | Game name from Polemica |
| game_data_cache | JSONB | Full PolemicaGame JSON for offline scoring |
| scored | BOOLEAN | Whether scores have been calculated |
| played_at | TIMESTAMP | When the game was played |

#### CardTemplate
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| fantasy_player_id | BIGINT | FK → FantasyPlayer (глобальный игрок; не привязка к турниру) |
| rarity | VARCHAR | COMMON / RARE / EPIC / LEGENDARY |
| image_url | VARCHAR | Card artwork URL |
| description | TEXT | Flavor text |

#### CardTemplateAchievement
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| card_template_id | BIGINT | FK → CardTemplate |
| achievement_type | VARCHAR | Achievement enum key |
| bonus_points | DOUBLE | Points awarded when achievement triggers |

#### UserCard
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| telegram_user_id | BIGINT | FK → TelegramUser |
| card_template_id | BIGINT | FK → CardTemplate |
| acquired_at | TIMESTAMP | When the card was given to user |

#### CardPack
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| name | VARCHAR | Pack display name |
| tournament_id | BIGINT | FK → Tournament (контекст выдачи/каталога; не ограничивает пул шаблонов при открытии) |
| active | BOOLEAN | Available for opening |

При открытии пака шаблоны выбираются по редкости из **глобального** набора всех `CardTemplate` (не только игроков текущего турнира).

#### CardPackRarityConfig
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| card_pack_id | BIGINT | FK → CardPack |
| rarity | VARCHAR | Rarity level |
| probability | DOUBLE | 0.0–1.0, sum must equal 1.0 |
| cards_count | INT | How many cards of this rarity in one pack opening |

#### FantasyTeam
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| telegram_user_id | BIGINT | FK → TelegramUser |
| series_id | BIGINT | FK → Series |
| total_score | DOUBLE | Calculated total, nullable until scored |
| submitted_at | TIMESTAMP | |
| UNIQUE(telegram_user_id, series_id) | | One team per user per series |

#### FantasyTeamCard
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| fantasy_team_id | BIGINT | FK → FantasyTeam |
| user_card_id | BIGINT | FK → UserCard |
| slot | INT | 1, 2, or 3 |
| score | DOUBLE | Calculated score, nullable until scored |

---

## 5. Achievement System

Достижения извлекаются из модели `PolemicaGame`. Для каждого игрока в игре анализируются события.

### 5.1 Available Game Data for Achievement Detection

Из `polemica-library` доступны:

| Поле PolemicaGame | Что даёт |
|-------------------|----------|
| `checks: List<PolemicaCheck>` | Проверки шерифа/дона: night, role (кто проверяет), player (кого) |
| `shots: List<PolemicaShot>` | Выстрелы мафии: night, shooter, victim |
| `votes: List<PolemicaVote>` | Голосования: day, voter, candidate |
| `players[].role` | Роль игрока (DON, MAFIA, PEACE, SHERIFF) |
| `players[].award` | Бонус/лучший ход (Double) |
| `players[].guess` | Угадайка (civs, mafs, vice) |
| `players[].fouls/techs` | Фолы и замечания |
| `players[].disqual` | Дисквалификация |
| `result` | RED_WIN / BLACK_WIN |
| `comKiller` | Кто стреляет после смерти шерифа |
| `bonuses` | Бонусы игры |
| `GameUtils.*` | Утилиты: getKilled, getSheriff, getDon, playersOnTable и др. |

### 5.2 Planned Achievement Types (preliminary, final list TBD)

| Achievement Key | Description | Detection Logic |
|-----------------|-------------|-----------------|
| `SHERIFF_FOUND_BLACK` | Шериф проверил чёрного | `checks` where checker=SHERIFF, target role is MAFIA/DON |
| `DON_FOUND_SHERIFF` | Дон нашёл шерифа | `checks` where checker=DON, target role is SHERIFF |
| `FIRST_NIGHT_SURVIVED` | Пережил первую ночь | Player not in `getKilled()` for night 1 |
| `WON_GAME` | Команда игрока победила | `result` matches player's team (red/black) |
| `BEST_MOVE` | Получил лучший ход | `players[].award > 0` |
| `SURVIVED_TILL_END` | Дожил до конца игры | Player in `playersOnTable()` at game end |
| `VOTED_OUT_BLACK` | Голосовал за вылет чёрного | `getFinalVotes()` where voted for black player who was expelled |
| `CORRECT_GUESS` | Угадал 3 мафии | `guess` matches actual roles |
| `NO_FOULS` | Сыграл без фолов | `fouls.isEmpty() && techs.isEmpty()` |

### 5.3 Score Calculation Formula

For each card in a fantasy team, per game in the series:

```
card_game_score = player_base_points + Σ(achievement_bonus)
```

Where:
- `player_base_points` = `players[position].award` or points from `GamePointsService` (игрок в игре определяется по `polemica_user_id` из `FantasyPlayer` шаблона карточки)
- `achievement_bonus` = `CardTemplateAchievement.bonus_points` for each triggered achievement

Total card score across series:
```
card_total = Σ(card_game_score) for all games in series where player participated
```

Fantasy team total:
```
team_total = Σ(card_total) for all 3 cards
```

---

## 6. API Design

### 6.1 User API (Telegram Mini App)

Base path: `/api/v1`

Authentication: Telegram `initData` in `Authorization` header, validated via HMAC-SHA256 with bot token.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/tournaments` | List active tournaments (в ответе: `kind`, `polemicaCompetitionId` при необходимости) |
| GET | `/tournaments/{id}` | Tournament with series list (те же поля турнира) |
| GET | `/tournaments/{id}/participants` | Ростер турнира: список `SeriesPlayerEntry` (ник, фото, `tournamentPlayerId`) |
| GET | `/series/{id}` | Series details: players, games, status |
| GET | `/series/{id}/leaderboard` | Fantasy team rankings |
| GET | `/me` | Current user profile |
| GET | `/me/cards` | User's card collection (filters: optional `tournamentId` — карты игроков, участвующих в этом турнире; `rarity`). В ответе по каждой карточке: `fantasyPlayerId`, `rarity`, `imageUrl` (арт шаблона), `playerPhotoUrl` (фото игрока), ник, достижения и др. — см. `UserCardItemDto`. |
| GET | `/me/fantasy-teams` | All user's fantasy teams |
| GET | `/me/fantasy-teams/{seriesId}` | Fantasy team for specific series |
| POST | `/series/{id}/fantasy-team` | Submit fantasy team (body: 3 `user_card_id`). Каждая карточка должна относиться к игроку из состава серии (тот же `FantasyPlayer`, что у `SeriesPlayer`). |
| PUT | `/series/{id}/fantasy-team` | Edit fantasy team (before deadline); те же правила по составу серии |

### 6.2 Admin API

Base path: `/api/v1/admin`

Authentication: Username/password (Basic Auth or JWT — start simple).

**Tournament Management:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/tournaments` | Create tournament (`kind`, при `POLEMICA_COMPETITION` — `polemicaCompetitionId`) |
| PUT | `/tournaments/{id}` | Update tournament (смена `kind` / `polemicaCompetitionId` при наличии серий — отклоняется) |
| GET | `/tournaments` | List all tournaments |
| GET | `/tournaments/{id}` | Tournament details |
| POST | `/tournaments/{id}/players` | Add player (`polemica_user_id` + nickname). Создаётся или переиспользуется `FantasyPlayer`; в ответе — `id` участника турнира и `fantasyPlayerId` для создания шаблонов карточек |
| DELETE | `/tournaments/{id}/players/{playerId}` | Remove player |
| POST | `/tournaments/{id}/players/{playerId}/photo` | Upload player photo (multipart) → S3 |

**Series Management:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/tournaments/{id}/series` | Create series (поля зависят от `kind` турнира: prefix или `gameNumFrom`/`gameNumTo`) |
| PUT | `/series/{id}` | Update series (name, prefix или num-диапазон, deadline, status) |
| POST | `/series/{id}/players` | Assign players to series |
| POST | `/series/{id}/sync-games` | STANDALONE: игры по профилю + prefix; POLEMICA_COMPETITION: игры соревнования по диапазону `num` |
| GET | `/polemica/competitions` | Список соревнований Polemica (read-only, для выбора ID в админке) |
| GET | `/polemica/competitions/{id}` | Деталь соревнования Polemica |
| POST | `/series/{id}/calculate-scores` | Trigger scoring |

**Card Management:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/card-templates` | Create card template (body: `fantasyPlayerId`, rarity, …) |
| PUT | `/card-templates/{id}` | Update card template |
| GET | `/card-templates` | List templates: опционально `tournamentId` (шаблоны игроков, заявленных в этом турнире), `fantasyPlayerId`, `rarity` |
| POST | `/card-templates/{id}/achievements` | Add achievement to card template |
| POST | `/card-templates/{id}/image` | Upload card artwork (multipart) → S3 |
| POST | `/card-packs` | Create card pack |
| PUT | `/card-packs/{id}` | Update pack configuration |
| POST | `/users/{telegramUserId}/give-cards` | Give specific cards to user |
| POST | `/users/{telegramUserId}/open-pack/{packId}` | Open pack → случайные карты по редкостям из **глобального** пула шаблонов |

---

## 7. Game Sync Flow

### 7.1 Турнир `STANDALONE` (как в исходном процессе)

1. Админ создаёт серию с `name_prefix` (например, `"МФЛ Сезон 5 Тур 3"`)
2. Админ назначает игроков в серию
3. После проведения игр админ нажимает «Sync Games»
4. Backend для каждого игрока серии собирает профильные матчи (публичный API профиля) и пересекает множества match id
5. Из загруженных по id матчей отбираются те, чьё название начинается с `name_prefix`
6. Для каждой подходящей игры вызывается полная загрузка `PolemicaGame` (`getMatch`)
7. Данные кэшируются в `series_games.game_data_cache` (JSONB)
8. Админ запускает расчёт очков → Scoring Engine обрабатывает все игры серии

### 7.2 Турнир `POLEMICA_COMPETITION`

1. Админ создаёт турнир с `kind = POLEMICA_COMPETITION` и выбирает `polemica_competition_id` (соревнование в Polemica).
2. Для каждой серии задаётся диапазон номеров игр `game_num_from` … `game_num_to` (inclusive, поле `num` в списке игр соревнования).
3. По кнопке «Sync Games» backend вызывает `getGamesFromCompetition(competitionId)`, фильтрует по `num`, для каждой подходящей игры — `getGameFromCompetition` и кэш в `series_games` как выше. Список матчей **не** строится через профили игроков серии (игроки по-прежнему нужны для скоринга и фэнтези-составов).
4. Расчёт очков — тот же Scoring Engine по кэшированным играм.

---

## 8. Authentication

### 8.1 Telegram Mini App (Users)

1. TMA SDK предоставляет `initData` — подписанную строку с данными пользователя
2. Frontend отправляет `initData` в заголовке `Authorization: tma <initData>`
3. Backend валидирует подпись через HMAC-SHA256 с использованием bot token
4. Из `initData` извлекается `user.id` (telegram_id)
5. Если пользователь не существует — автоматическая регистрация

### 8.2 Admin Panel

Начальная реализация — Basic Auth или статический API key.
В будущем можно перейти на JWT с логин-эндпоинтом.

Админские эндпоинты защищены отдельным security-фильтром (`/api/v1/admin/**`).

---

## 9. Gaps in polemica-library

Для работы сервиса необходимо добавить в `polemica-library`:

| # | Что нужно | Зачем | Приоритет |
|---|-----------|-------|-----------|
| 1 | Поле `name: String?` в `PolemicaGame` | Матчинг игр с серией по name_prefix | Критичный |
| 2 | Метод `getPlayerGames(userId: Long): List<GameReference>` | Поиск игр участников серии | Критичный |
| 3 | Фильтрация игр по названию (серверная) | Оптимизация при большом количестве игр | Желательный |

---

## 10. Agent Work Split

Реализация разделена на 6 агентов. Каждый получает изолированный scope с чёткими входами и выходами.

### Agent A1: Foundation

**Scope:** скелет бэкенда, инфраструктура, схема БД.

**Deliverables:**
- `build.gradle.kts` — Spring Boot 3.x, Spring Data JPA, Flyway, Security, Jackson, AWS S3 SDK, polemica-library, Actuator/Prometheus
- `application.yml` — полная конфигурация (см. Section 12.5)
- `Dockerfile` — multi-stage build (см. Section 12.1)
- `docker-compose.yml` — PostgreSQL 16 + MinIO + backend (см. Section 12.2)
- `.env.example` — шаблон переменных окружения
- `.github/workflows/docker-publish.yml` — CI/CD pipeline (см. Section 12.3)
- Flyway-миграции: `V1__initial_schema.sql` (базовая схема), далее инкрементальные (`V2__series_game_unique.sql`, `V3__fantasy_player_global_cards.sql` — глобальные игроки и привязка карточек к `fantasy_player`)
- JPA entities для всех таблиц
- Базовые Enum-классы (Rarity, TournamentStatus, SeriesStatus, AchievementType)
- S3 конфигурация и `ImageStorageService` (upload/delete)
- `FantasyApplication.kt` — main class

**Outputs:** рабочий проект, который собирается в Docker image, запускается через `docker compose up`, подключается к PostgreSQL и MinIO, применяет миграции.

**Dependencies:** нет (стартует первым).

---

### Agent A2: polemica-library Updates

**Scope:** изменения в отдельном репозитории `~/personal/mafia/polemica-library`.

**Deliverables:**
- Добавить поле `name: String?` в `PolemicaGame`
- Добавить метод `getPlayerGames(userId: Long)` в `PolemicaClient` / `PolemicaClientImpl`
- Тесты на новые методы
- Bump version

**Dependencies:** нет (параллельно с A1).

---

### Agent A3: Admin Backend

**Scope:** все административные эндпоинты и бизнес-логика.

**Deliverables:**
- Admin auth filter (Basic Auth)
- Controllers: `TournamentAdminController`, `SeriesAdminController`, `CardAdminController`
- Services: `TournamentService`, `SeriesService`, `CardService`, `CardPackService`
- Repositories для всех admin-managed entities
- DTO-классы для request/response
- Validation (Jakarta Validation)

**Dependencies:** A1 (entities, DB schema).

---

### Agent A4: Scoring Engine + Game Sync

**Scope:** интеграция с Полемикой, синхронизация игр, расчёт очков.

**Deliverables:**
- `PolemicaIntegrationService` — обёртка над polemica-library client
- `GameSyncService` — поиск игр по истории игроков + name_prefix, кэширование
- `AchievementDetector` — анализ PolemicaGame, определение сработавших достижений
- `ScoringService` — расчёт очков карточек и команд
- Unit-тесты на detection и scoring logic

**Dependencies:** A1 (entities), A2 (новые методы polemica-library).

---

### Agent A5: User Backend + TMA Frontend

**Scope:** пользовательский API и Telegram Mini App.

**Deliverables (Backend):**
- `TelegramAuthFilter` — валидация initData
- `UserController`, `TournamentController`, `FantasyTeamController`
- `UserService`, `FantasyTeamService`
- Leaderboard query

**Deliverables (Frontend — `polemica-fantasy-webapp/`):**
- Vite + React + TS проект
- `@telegram-apps/sdk-react` интеграция
- Страницы: список турниров, серия (игроки, статус), коллекция карточек, сборка команды, лидерборд
- API client (fetch / TanStack Query)

**Dependencies:** A1 (entities), A4 (scoring для отображения результатов).

---

### Agent A6: Admin Frontend

**Scope:** веб-админка.

**Deliverables (`polemica-fantasy-admin/`):**
- Vite + React + TS + Ant Design проект
- Страницы: управление турнирами, сериями, игроками, карточками, паками
- Кнопки действий: sync games, calculate scores, give cards, open pack
- API client

**Dependencies:** A3 (admin API contract).

---

### Execution Graph

```
     A1 (Foundation)          A2 (polemica-library)
      │                        │
      ├───────────┬────────────┤
      │           │            │
      ▼           ▼            │
  A3 (Admin    A5 (User       │
   Backend)    Backend+TMA)   │
      │           │            │
      │           │            ▼
      │           │◄──── A4 (Scoring)
      │           │
      ▼           ▼
  A6 (Admin
   Frontend)
```

Параллельные пары:
- **A1 ‖ A2** — разные репозитории, полностью независимы
- **A3 ‖ A4** — разные пакеты, не пересекаются по файлам (после A1)
- **A5 ‖ A6** — разные модули (после зависимостей)

---

## 11. S3 Image Storage

### Назначение
Хранение фотографий игроков и артворков карточек.

### Bucket Structure
```
polemica-fantasy-images/
├── players/
│   └── {fantasyPlayerId}/photo.{ext}
└── cards/
    └── {cardTemplateId}/image.{ext}
```

### Upload Flow
1. Админ отправляет `POST .../photo` с `multipart/form-data`
2. Backend валидирует файл (тип: JPEG/PNG/WebP, размер: до 5 MB)
3. Backend загружает файл в S3 bucket с публичным ACL на чтение
4. URL сохраняется в БД (`photo_url` / `image_url`)
5. Фронтенды используют URL напрямую (без прокси через backend)

### Конфигурация (`application.yml`)
```yaml
s3:
  endpoint: ${S3_ENDPOINT:http://localhost:9000}    # MinIO for dev
  region: ${S3_REGION:us-east-1}
  bucket: ${S3_BUCKET:polemica-fantasy-images}
  access-key: ${S3_ACCESS_KEY}
  secret-key: ${S3_SECRET_KEY}
```

### Dev Environment
MinIO в Docker Compose для локальной разработки (S3-совместимый).

---

## 12. Deployment Infrastructure

Деплой основан на паттернах проекта `overlay` (тот же стек: Kotlin + Spring Boot + PostgreSQL + S3).

### 12.1 Dockerfile (multi-stage)

```dockerfile
# Stage 1: Build
FROM gradle:jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew /app/
COPY gradle /app/gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies || true   # cache deps layer
COPY src /app/src
RUN ./gradlew --no-daemon build -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 12.2 docker-compose.yml (dev + prod)

**Для локальной разработки** (`docker-compose.yml`):

```yaml
services:
  fantasy-db:
    image: postgres:16
    environment:
      POSTGRES_DB: fantasy
      POSTGRES_USER: ${DB_USER:-fantasy}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-fantasy}
    ports:
      - "5433:5432"
    volumes:
      - fantasy-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-fantasy}"]
      interval: 10s
      retries: 5
      timeout: 5s

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${S3_ACCESS_KEY:-minioadmin}
      MINIO_ROOT_PASSWORD: ${S3_SECRET_KEY:-minioadmin}
    ports:
      - "9000:9000"    # S3 API
      - "9001:9001"    # MinIO Console
    volumes:
      - fantasy-minio-data:/data

  fantasy-backend:
    build: ./polemica-fantasy-backend
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://fantasy-db:5432/fantasy
      DATABASE_USER: ${DB_USER:-fantasy}
      DATABASE_PASSWORD: ${DB_PASSWORD:-fantasy}
      S3_ENDPOINT: http://minio:9000
      S3_REGION: us-east-1
      S3_BUCKET: polemica-fantasy-images
      S3_ACCESS_KEY: ${S3_ACCESS_KEY:-minioadmin}
      S3_SECRET_KEY: ${S3_SECRET_KEY:-minioadmin}
      TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN}
      POLEMICA_USERNAME: ${POLEMICA_USERNAME}
      POLEMICA_PASSWORD: ${POLEMICA_PASSWORD}
      SPRING_PROFILES_ACTIVE: dev
    depends_on:
      fantasy-db:
        condition: service_healthy
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

volumes:
  fantasy-postgres-data:
  fantasy-minio-data:
```

**Для production** — тот же compose, но:
- `fantasy-backend` использует pre-built image из GHCR вместо `build: .`
- `minio` заменяется на Yandex Object Storage (или другой S3-провайдер)
- Добавляется Prometheus для мониторинга (опционально)

### 12.3 CI/CD (GitHub Actions)

Workflow: `.github/workflows/docker-publish.yml`

```
Push to main
  ├─► Build Docker image (multi-stage)
  ├─► Push to GHCR (ghcr.io/<org>/polemica-fantasy-backend:<tag>)
  └─► Sign with cosign

Manual deploy (workflow_dispatch)
  └─► SSH to VM
      ├─► git pull
      ├─► docker compose pull
      └─► docker compose up -d
```

Ключевые моменты:
- Docker image публикуется в **GitHub Container Registry** (GHCR)
- Автоматический build при push в `main`
- Ручной deploy через `workflow_dispatch` (SSH на сервер)
- Image signing через **cosign** (Sigstore)

### 12.4 Переменные окружения (.env)

```env
# Database
DB_USER=fantasy
DB_PASSWORD=<secret>

# S3 (Yandex Object Storage for prod, MinIO for dev)
S3_ACCESS_KEY=<access-key>
S3_SECRET_KEY=<secret-key>
S3_ENDPOINT=https://storage.yandexcloud.net   # prod
S3_REGION=ru-central1                          # prod
S3_BUCKET=polemica-fantasy-images

# Telegram
TELEGRAM_BOT_TOKEN=<bot-token>

# Polemica API
POLEMICA_USERNAME=<username>
POLEMICA_PASSWORD=<password>

# Admin
ADMIN_PASSWORD=<admin-password>
```

### 12.5 application.yml (Spring Boot)

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      connection-timeout: 10000
      idle-timeout: 600000
      max-lifetime: 1800000
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
    show-sql: false
    open-in-view: false
  flyway:
    enabled: true
    clean-disabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB

s3:
  endpoint: ${S3_ENDPOINT:http://localhost:9000}
  region: ${S3_REGION:us-east-1}
  bucket: ${S3_BUCKET:polemica-fantasy-images}
  access-key: ${S3_ACCESS_KEY}
  secret-key: ${S3_SECRET_KEY}

telegram:
  bot:
    token: ${TELEGRAM_BOT_TOKEN}

polemica:
  api:
    base-url: https://polemicagame.com
    username: ${POLEMICA_USERNAME}
    password: ${POLEMICA_PASSWORD}

app:
  admin:
    password: ${ADMIN_PASSWORD:defaultPassword123}

management:
  endpoints:
    web:
      exposure:
        include: info, health, prometheus
  server:
    port: 8081
```

### 12.6 Frontend Deployment

Оба фронтенда — статические SPA, собранные через Vite:

```bash
# Build
cd polemica-fantasy-webapp && npm run build   # → dist/
cd polemica-fantasy-admin && npm run build     # → dist/
```

Варианты деплоя:
- **Nginx** — отдать `dist/` как static files + reverse proxy на backend API
- **GitHub Pages / Vercel / Netlify** — для SPA без серверной части
- **Docker** — Nginx-контейнер со static files (добавить в docker-compose)

Для TMA — URL Mini App указывается в настройках Telegram-бота через @BotFather.

---

## 13. Non-Functional Requirements

- **Latency:** API response < 500ms для user endpoints
- **Polemica API:** rate limiting — не более 10 req/s; кэшировать game data в JSONB
- **Scalability:** single instance достаточно на начальном этапе
- **Security:** TMA initData validation обязательна; admin endpoints за отдельным auth
- **Data consistency:** fantasy team не может быть изменена после `team_deadline` серии; в команду можно поставить только карточки игроков, входящих в состав серии (`SeriesPlayer` / тот же `FantasyPlayer`)
- **Monitoring:** Prometheus metrics на management port 8081 (опционально)
