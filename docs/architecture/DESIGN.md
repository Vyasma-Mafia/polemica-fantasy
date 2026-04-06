# Polemica Fantasy — System Design Document

> **Где лежит файл:** [`docs/architecture/DESIGN.md`](./DESIGN.md). Навигация по документации: [`docs/README.md`](../README.md). Текущий контекст для разработки: [`memory-bank/`](../../memory-bank/).

## 1. Overview

Polemica Fantasy — сервис для создания фэнтези-команд по игре «спортивная мафия».
Пользователи собирают карточки игроков с различными уровнями редкости и бонусами за достижения,
формируют команды из **1–3** карточек на серию игр (неполный состав уменьшает награду за место в лидерборде при финализации) и соревнуются по набранным очкам.

Данные об играх и игроках поступают из внешнего API **Polemica** через библиотеку `polemica-library`.

**Экономика V3:** карточки имеют ограниченное число использований (контракт); одно использование списывается при **финализации серии** администратором (после скоринга). Игроки перерабатывают ненужные карты и продлевают истёкшие за фантики; награды за места в лидерборде серии начисляются при финализации. Числовые параметры задаются таблицей `economy_config` и редактируются в админке. Подробности — §2, §4.1–4.2, §6, §10 (Phase 3 и последующие итерации).

---

## 2. Glossary

| Термин | Описание |
|--------|----------|
| **Tournament** | Собственная сущность сервиса. Объединяет игроков и серии. Имеет **`kind`** (`STANDALONE` или `POLEMICA_COMPETITION`). Турниры без привязки к Полемике — это `STANDALONE` (режим по умолчанию для существующих данных). |
| **TournamentKind** | `STANDALONE` — игры серии подбираются по истории участников и префиксу названия (`name_prefix`). `POLEMICA_COMPETITION` — турнир привязан к соревнованию Polemica: на турнире хранится `polemica_competition_id`; все серии этого турнира задают **диапазон номеров игр** (`game_num_from` … `game_num_to`). **Внутри одного турнира все серии одного типа.** |
| **Series** | Набор игр внутри турнира. При `STANDALONE`: обязателен `name_prefix`. При `POLEMICA_COMPETITION`: обязательны `game_num_from` и `game_num_to`. |
| **Game** | Конкретная игра из Полемики (`PolemicaGame`), привязанная к серии. |
| **Fantasy Player** | Глобальный игрок сервиса: один на каждый `polemica_user_id` (ник, фото). Создаётся или подставляется при добавлении игрока в турнир. Не привязан к одному турниру. |
| **Tournament Player** | Участие конкретного fantasy player в ростере конкретного турнира (связь турнир ↔ игрок). |
| **Card Template** | Определение карточки: привязка к **fantasy player** (не к турниру) + уровень редкости + набор достижений. Один и тот же шаблон может использоваться в разных турнирах, если игрок включён в серию. Шаблоны могут создаваться как вручную (через админку), так и автоматически при открытии auto-gen пака (с переиспользованием при совпадении игрок + редкость + набор ачивок). |
| **User Card** | Конкретный экземпляр карточки, принадлежащий пользователю. У одного пользователя может быть несколько одинаковых карточек. |
| **Card Pack** | Набор для получения карточек. Может быть **legacy** (выбирает из существующих шаблонов) или **auto-generated** (генерирует шаблоны на лету из пула игроков). Задаёт точное количество карт каждой редкости. Может иметь стоимость в фантиках для покупки через магазин. |
| **Fantasy Team** | Команда из **1–3** карточек разных игроков (`fantasy_player_id` в слотах уникален), выставленная пользователем на серию. |
| **Legendary upgrade** | Апгрейд EPIC → LEGENDARY in-place (`UserCard` тот же): новый `CardTemplate`, +1 достижение, списание фантиков; на карте может храниться `crafted_by_telegram_user_id`. Лимит LEGENDARY в одной команде на серию задаётся `economy_config` (`legendary.team.max_per_series`). Подробнее — [`features/DESIGN-LEGENDARY-CARDS.md`](../features/DESIGN-LEGENDARY-CARDS.md). |
| **Display name** | Кастомный ник в TMA: колонка `telegram_user.display_name`, не перетирается синхронизацией из Telegram; отдаётся в публичных DTO. |
| **Achievement** | Системная сущность (справочник) — тип игрового события с метаданными: системный бонус, тип повторяемости (`ONCE_PER_GAME` / `MULTIPLE_PER_GAME`), допустимые роли (`applicableRoles`), флаг возможности появления на случайных картах (`canAppearOnRandomCards`). |
| **Фантики** | Внутриигровая валюта. Стартовый баланс — 1000. Используется для покупки паков в магазине. Начисляется админом. Все операции с балансом логируются в `fantiki_transaction`. |
| **Store** | Магазин паков в TMA. Пользователь видит доступные паки с ценами и покупает их за фантики. |
| **TMA** | Telegram Mini App — пользовательский интерфейс внутри Telegram. |
| **Card Contract** | Лимит использований карточки: одно использование = карта участвовала в команде на серию, по которой админ выполнил **finalize** (тогда списывается один `uses_remaining`). Дефолтное число uses при выдаче карты задаётся редкостью через `economy_config` (`card.uses.*`); на экземпляре — поля `uses_remaining`, `times_renewed`. |
| **Recycle (переработка)** | Удаление карточки у пользователя с начислением фантиков (`CARD_RECYCLE`). Нельзя, если карта в команде серии с `finalized = false`. |
| **Renewal (продление)** | Оплата фантиков за восстановление `uses_remaining` для истёкшей карты (`CARD_RENEWAL`), с ограничением `renewal.max_times` из `economy_config`. |
| **Finalize (финализация серии)** | Админ-операция после скоринга: декремент `uses_remaining` у всех карт в командах серии, начисление наград лидерборда (`SERIES_REWARD`), установка `series.finalized = true`. Необратима. |
| **Economy Config** | Таблица key–value `economy_config` — параметры экономики (uses, recycle, renewal, награды серии). Редактируется только через админку (обновление существующих ключей); кэш на бэкенде инвалидируется при сохранении. |

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
- Рамка карточки в UI стилизуется по редкости (`COMMON` / `RARE` / `EPIC` / `LEGENDARY`): коллекция, сборка команды, история фэнтези (включая модалку деталей). `UserCardItemDto` включает контракт карточки: `usesRemaining`, `timesRenewed` (V3).

### 3.2 Module Structure

```
polemica-fantasy/
├── docs/
│   ├── README.md                    # Указатель документации
│   ├── architecture/
│   │   └── DESIGN.md                # This document (SDD)
│   ├── plans/archive/               # Исторические планы (V2, V3, …)
│   └── features/                    # Дизайн отдельных фич
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
│       │       └── db/migration/    # Flyway V1+ (см. каталог миграций)
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
    │                           │
    │ (*:1)                     ▼
    ▼                      SeriesGame
Achievement (1)                 │
    │                           │ (1)
    │ (*)                       ▼
    ▼               FantasyTeamCardGameScore (*)
AchievementApplicableRole       │
                                │ (*)
                                ▼
                   FantasyTeamCardGameAchievement

TelegramUser (1) ──── (*) UserCard ──── (*) FantasyTeamCard (*) ── FantasyTeamCardGameScore
    │                     │ uses_remaining, times_renewed              │
    │ (1)                 │                                            │ (*)
    │                     ▼                                            ▼
    │           CardTemplate ──────────────────────────── FantasyTeam (series_id)
    │                                                         │
    │                                                         └── series.finalized (V3)
    ├──── (*) FantikiTransaction (reason: + SERIES_REWARD, CARD_RECYCLE, CARD_RENEWAL)
    └──── fantiki (balance)

EconomyConfig                           # key-value, V3 (card uses, recycle, renewal, series rewards)

CardPack (1) ──── (*) CardPackRarityConfig
    │
    ├──── (*) CardPackPlayer ──── (1) FantasyPlayer
    │
    ├──── auto_generated, price_fantiki, use_all_tournament_players
    └──── (1) Tournament
```

**Инварианты:** шаблон карточки (`CardTemplate`) ссылается на `FantasyPlayer`, а не на `TournamentPlayer`. Участие в турнире и серии идёт через `TournamentPlayer` / `SeriesPlayer`. Сборка фэнтези-команды на серию — **1–3** слота, только карточки игроков из ростера серии; в одной команде нельзя две карты одного `fantasy_player_id`; **V3:** в команду нельзя поставить карту с `uses_remaining ≤ 0`; в команде не больше `legendary.team.max_per_series` карт редкости LEGENDARY. До дедлайна при смене ростера серии лишние карты снимаются с команды (`FantasyTeamRosterPruningService`). Все серии одного турнира соответствуют одному `TournamentKind` родителя. Achievement — справочник; `CardTemplateAchievement.bonus_points` nullable (NULL = системный из `Achievement`, иначе override). **V3:** списание одного «использования» контракта и награда за место в лидерборде (с масштабированием при 1–2 картах в команде, см. `SeriesFinalizationService.scaleSeriesRewardByRosterSize`) происходят при **финализации** серии (`series.finalized`), а не при каждой отдельной игре. **API:** список серий в ответе `GET /tournaments/{id}` — **от новых к старым** (`series.id` DESC).

### 4.2 Core Entities

#### TelegramUser
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| telegram_id | BIGINT | Unique, Telegram user ID |
| username | VARCHAR | Telegram username |
| first_name | VARCHAR | Имя из Telegram |
| display_name | VARCHAR | Кастомный ник для UI (nullable); `PATCH /api/v1/me` |
| fantiki | BIGINT | Баланс внутриигровой валюты (NOT NULL, DEFAULT 1000) |
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
| finalized | BOOLEAN | Финализация серии: `true` после админ-операции «finalize» (списание uses и награды лидерборда). NOT NULL DEFAULT false (V3) |

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

#### Achievement
| Field | Type | Description |
|-------|------|-------------|
| id | VARCHAR(64) | PK (ключ: `SHERIFF_FOUND_BLACK`, `WON_GAME`, …) |
| name | VARCHAR | Отображаемое имя |
| description | TEXT | Описание |
| bonus_points | DOUBLE | Системный бонус за срабатывание (NOT NULL, DEFAULT 1) |
| occurrence_type | VARCHAR(32) | `ONCE_PER_GAME` / `MULTIPLE_PER_GAME` |
| can_appear_on_random_cards | BOOLEAN | Может ли выпасть на автосгенерированной карте (DEFAULT false) |

#### AchievementApplicableRole
| Field | Type | Description |
|-------|------|-------------|
| achievement_id | VARCHAR(64) | FK → Achievement, часть composite PK |
| role | VARCHAR(32) | `DON`, `MAFIA`, `PEACE`, `SHERIFF` |
| | | PK = (achievement_id, role) |

#### CardTemplateAchievement
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| card_template_id | BIGINT | FK → CardTemplate |
| achievement_id | VARCHAR(64) | FK → Achievement |
| bonus_points | DOUBLE | **Nullable.** NULL = системный `Achievement.bonus_points`; если задан — override |

#### UserCard
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| telegram_user_id | BIGINT | FK → TelegramUser |
| card_template_id | BIGINT | FK → CardTemplate |
| crafted_by_telegram_user_id | BIGINT | FK → TelegramUser, nullable — кто выполнил EPIC→LEGENDARY upgrade; у выданных админом карт NULL |
| acquired_at | TIMESTAMP | When the card was given to user |
| uses_remaining | INT | Остаток использований контракта (одно использование = одна серия в команде). NOT NULL (V3) |
| times_renewed | INT | Сколько раз продлевали карту. NOT NULL DEFAULT 0 (V3) |

#### EconomyConfig (V3)
| Field | Type | Description |
|-------|------|-------------|
| key | VARCHAR(64) | PK, например `card.uses.COMMON`, `recycle.value.RARE`, `series.reward.1`, `renewal.max_times`, `legendary.upgrade.cost`, `legendary.team.max_per_series` |
| value | VARCHAR(256) | Строковое значение (числа для экономики) |
| description | TEXT | Человекочитаемое описание для админки |

Сиды задают дефолты (uses по редкостям, recycle/renewal, награды за 1–3 / top10 / participation). Создание новых ключей через API не поддерживается — только обновление существующих.

#### FantikiTransaction
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| telegram_user_id | BIGINT | FK → TelegramUser |
| amount | BIGINT | Положительный = начисление, отрицательный = списание |
| reason | VARCHAR(64) | `INITIAL`, `ADMIN_GRANT`, `PACK_PURCHASE`, `SERIES_REWARD`, `CARD_RECYCLE`, `CARD_RENEWAL`, `LEGENDARY_UPGRADE` (V3+) |
| created_at | TIMESTAMP | NOT NULL DEFAULT NOW() |

#### CardPack
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| name | VARCHAR | Pack display name |
| tournament_id | BIGINT | FK → Tournament (контекст: пул игроков при auto-gen) |
| active | BOOLEAN | Available for opening / purchase |
| auto_generated | BOOLEAN | Автогенерация карт при открытии (NOT NULL, DEFAULT false) |
| price_fantiki | BIGINT | Стоимость в фантиках (NOT NULL, DEFAULT 0; 0 = выдаётся только через админку) |
| free_opens_per_user | INT | Лимит бесплатных открытий на пользователя на этот пак (0 = только платные); учёт в `user_card_pack_free_usage` |
| use_all_tournament_players | BOOLEAN | При auto-gen: использовать всех игроков турнира как пул (NOT NULL, DEFAULT false) |

**Legacy-пак** (`auto_generated = false`): при открытии шаблоны выбираются по редкости из **глобального** набора `CardTemplate`.

**Auto-gen пак** (`auto_generated = true`): при открытии карточки генерируются на лету из пула игроков. COMMON — без ачивок; RARE — 1 случайная (`canAppearOnRandomCards = true`); EPIC — 2 случайных различных; LEGENDARY — не участвует. Если найден существующий `CardTemplate` с идентичным набором (игрок + редкость + ачивки) — переиспользуется; иначе создаётся новый. При генерации `applicableRoles` **не** фильтруется — проверка роли только при скоринге.

#### CardPackPlayer
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| card_pack_id | BIGINT | FK → CardPack (ON DELETE CASCADE) |
| fantasy_player_id | BIGINT | FK → FantasyPlayer |
| | | UNIQUE (card_pack_id, fantasy_player_id) |

#### CardPackRarityConfig
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| card_pack_id | BIGINT | FK → CardPack |
| rarity | VARCHAR | Rarity level |
| cards_count | INT | Точное количество карт этой редкости в одном открытии |

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
| score | DOUBLE | Суммарные очки по всем играм серии, nullable until scored |

#### FantasyTeamCardGameScore
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| fantasy_team_card_id | BIGINT | FK → FantasyTeamCard (ON DELETE CASCADE) |
| series_game_id | BIGINT | FK → SeriesGame |
| base_points | DOUBLE | Базовые очки игрока в этой игре |
| achievement_bonus | DOUBLE | Суммарный бонус за все сработавшие достижения |
| rarity_modifier | DOUBLE | Множитель редкости карточки |
| total_score | DOUBLE | `(base_points + achievement_bonus) × rarity_modifier` |
| | | UNIQUE (fantasy_team_card_id, series_game_id) |

#### FantasyTeamCardGameAchievement
| Field | Type | Description |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| card_game_score_id | BIGINT | FK → FantasyTeamCardGameScore (ON DELETE CASCADE) |
| achievement_id | VARCHAR(64) | FK → Achievement |
| bonus_points | DOUBLE | Бонус за конкретное сработавшее достижение |
| | | UNIQUE (card_game_score_id, achievement_id) |

---

## 5. Achievement System

Достижения — **системный справочник** (таблица `achievement`). Каждое достижение хранит метаданные: бонус, тип повторяемости, допустимые роли, флаг доступности для автогенерации карт. Детекция происходит при скоринге — для каждого игрока в каждой игре анализируются события из `PolemicaGame`.

### 5.1 Achievement Properties

| Свойство | Описание |
|----------|----------|
| `bonus_points` | Системный бонус (default 1). Может быть переопределён на уровне `CardTemplateAchievement.bonus_points` (nullable override). |
| `occurrence_type` | `ONCE_PER_GAME` — засчитывается максимум 1 раз за игру. `MULTIPLE_PER_GAME` — сколько раз сработало, столько раз начисляется. |
| `applicable_roles` | Список ролей (`DON`, `MAFIA`, `PEACE`, `SHERIFF`), на которых достижение может сработать. Пустой список = достижение не применяется ни к кому. Проверяется **только при скоринге**, не при генерации карт. |
| `can_appear_on_random_cards` | Может ли попасть на автосгенерированную карточку (RARE/EPIC) при открытии auto-gen пака. |

### 5.2 Available Game Data for Achievement Detection

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

### 5.3 Achievement Types

| Achievement Key | Description | Detection Logic | Default Random |
|-----------------|-------------|-----------------|----------------|
| `SHERIFF_FOUND_BLACK` | Шериф проверил чёрного | `checks` where checker=SHERIFF, target role is MAFIA/DON | No |
| `DON_FOUND_SHERIFF` | Дон нашёл шерифа | `checks` where checker=DON, target role is SHERIFF | No |
| `FIRST_NIGHT_SURVIVED` | Пережил первую ночь | Player not in `getKilled()` for night 1 | No |
| `WON_GAME` | Команда игрока победила | `result` matches player's team (red/black) | **Yes** |
| `BEST_MOVE` | Получил лучший ход | `players[].award > 0` | **Yes** |
| `SURVIVED_TILL_END` | Дожил до конца игры | Player in `playersOnTable()` at game end | **Yes** |
| `VOTED_OUT_BLACK` | Голосовал за вылет чёрного | `getFinalVotes()` where voted for black player who was expelled | No |
| `CORRECT_GUESS` | Угадал 3 мафии | `guess` matches actual roles | No |
| `NO_FOULS` | Сыграл без фолов | `fouls.isEmpty() && techs.isEmpty()` | No |

По умолчанию все 9 достижений seed-ятся с `bonus_points = 1`, `occurrence_type = ONCE_PER_GAME`, `applicable_roles` = все 4 роли. В миграции **V11** для **всех** записей справочника выставлено `can_appear_on_random_cards = true` (раньше в V9 часть была `false`) — чтобы auto-gen паки могли брать любую ачивку из каталога; фильтрация по роли остаётся только на этапе скоринга.

### 5.4 Score Calculation Formula

For each card in a fantasy team, per game in the series:

```
card_game_score = (player_base_points + Σ(achievement_bonus)) × rarity_modifier
```

Where:
- `player_base_points` = очки игрока в этой игре с **публичной страницы матча** Polemica (то же поле `points`, что на `/match/{id}` по позиции за столом). В коде: `GamePointsService.fetchPlayerStats(polemicaGameId)` → словарь позиция → очки; игрок сопоставляется с карточкой по `polemica_user_id` из `FantasyPlayer`. **Не** используется сырое `PolemicaPlayer.award` из JSON матча как единственный источник базы.
- В расчёт попадают **только завершённые** игры (`PolemicaGame.result != null`); для таких игр выставляется `series_game.scored = true`.
- `achievement_bonus` = `CardTemplateAchievement.bonus_points ?? Achievement.bonus_points` for each triggered achievement (с учётом `occurrence_type` и `applicable_roles`)
- `rarity_modifier` = модификатор редкости карточки:

| Rarity | Modifier |
|--------|----------|
| COMMON | 1.00 |
| RARE | 1.10 |
| EPIC | 1.15 |
| LEGENDARY | 1.25 |

Total card score across series:
```
card_total = Σ(card_game_score) for all games in series where player participated
```

Fantasy team total:
```
team_total = Σ(card_total) for all cards in the team (1 to 3 slots)
```

### 5.5 Score Storage

При скоринге сохраняется полная детализация:
- `FantasyTeamCard.score` = `card_total`
- `FantasyTeamCardGameScore` — per-game breakdown (base_points, achievement_bonus, rarity_modifier, total_score)
- `FantasyTeamCardGameAchievement` — какие конкретно достижения сработали в каждой игре с бонусом каждого
- `FantasyTeam.total_score` = `team_total`

Перед повторным расчётом старые per-game записи очищаются (CASCADE).

---

## 6. API Design

### 6.1 User API (Telegram Mini App)

Base path: `/api/v1`

Authentication: Telegram `initData` in `Authorization` header, validated via HMAC-SHA256 with bot token.

**Profile & Collection:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/me` | Current user profile (`fantiki`, `displayName`, …) |
| PATCH | `/me` | Обновить `displayName` (тело `{"displayName":…}`; `null` / `""` — сброс на дефолт из Telegram) |
| GET | `/me/cards` | User's card collection (filters: optional `tournamentId`, **`seriesId`** — только карты игроков из ростера серии, `rarity`). В ответе: `fantasyPlayerId`, `rarity`, `imageUrl`, `playerPhotoUrl`, ник, достижения; **`usesRemaining`**, **`timesRenewed`**, **`craftedByTelegramUserId`** (V3+). Истёкшие карты не скрываются — клиент помечает по `usesRemaining`. |
| GET | `/me/economy-info` | Агрегат параметров экономики для UI: uses по редкостям, recycle/renewal, max renewals, таблица наград серии (V3) |
| GET | `/achievements` | Публичный каталог достижений (read-only; для экрана «Справка» в TMA) |
| POST | `/me/cards/{id}/recycle` | Переработать карту → фантики, карта удаляется (V3) |
| POST | `/me/cards/{id}/renew` | Продлить контракт истёкшей карты за фантики (V3) |
| GET | `/legendary-upgrade/info` | Стоимость апгрейда, лимит LEGENDARY в команде на серию, прочие параметры из `economy_config` |
| POST | `/legendary-upgrade` | EPIC → LEGENDARY: тело с `userCardId` и `achievementId` (+1 достижение к шаблону) |
| GET | `/me/fantasy-teams` | All user's fantasy teams |
| GET | `/me/fantasy-teams/{seriesId}` | Fantasy team for specific series |
| GET | `/me/fantasy-teams/{seriesId}/details` | Полная per-game детализация: по каждой карте → по каждой игре → base/achievements/modifier/total |

**Tournaments & Series:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/tournaments` | List active tournaments |
| GET | `/tournaments/{id}` | Tournament with series list (**серии от новых к старым** по `id`) |
| GET | `/tournaments/{id}/participants` | Ростер турнира |
| GET | `/series/{id}` | Series details: players, games, status |
| GET | `/series/{id}/leaderboard` | Fantasy team rankings |
| GET | `/series/{id}/users/{telegramId}/fantasy-team` | Чужая команда в серии: владелец (`UserPublicDto`), слоты с полной карточкой (`UserCardItemDto`) и очком по слоту |
| GET | `/series/{id}/users/{telegramId}/fantasy-team/details` | Как `GET /me/fantasy-teams/{seriesId}/details`, но для команды пользователя с данным `telegramId` |
| POST | `/series/{id}/fantasy-team` | Submit fantasy team (body: **1–3** различных `user_card_id`, валидация uses / LEGENDARY limit / ростер) |
| PUT | `/series/{id}/fantasy-team` | Edit fantasy team (before deadline) |

**Store (магазин паков):**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/store/packs` | Список доступных паков (active, цена, раскладка по редкостям, **`freeOpensRemaining`** при лимите бесплатных открытий) |
| POST | `/store/packs/{id}/buy` | Покупка/открытие пака: учёт бесплатной квоты или списание фантиков → открытие → список выпавших карт |

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
| GET | `/tournaments/{id}/series` | Список серий турнира; в каждой записи — **`finalized`** (V3) |
| GET | `/series/{id}` | Детали серии для админки (в т.ч. **`finalized`**, состав игроков) (V3) |
| PUT | `/series/{id}` | Update series (name, prefix или num-диапазон, deadline, status) |
| POST | `/series/{id}/players` | Assign players to series |
| POST | `/series/{id}/sync-games` | STANDALONE: игры по профилю + prefix; POLEMICA_COMPETITION: игры соревнования по диапазону `num` |
| GET | `/polemica/competitions` | Список соревнований Polemica (read-only, для выбора ID в админке) |
| GET | `/polemica/competitions/{id}` | Деталь соревнования Polemica |
| POST | `/series/{id}/calculate-scores` | Trigger scoring |
| POST | `/series/{id}/finalize` | Финализация серии: декремент `uses_remaining` у карт в командах, награды по лидерборду, `finalized = true` (V3) |

**Economy config (V3):**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/economy-config` | Все строки `economy_config` |
| PUT | `/economy-config/{key}` | Обновить значение по ключу (`{ "value": "..." }`, валидация — целое число) |
| PUT | `/economy-config` | Bulk-обновление: `{ "items": [ { "key", "value" }, ... ] }` |

**Achievement Management:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/achievements` | Список всех достижений из справочника |
| PUT | `/achievements/{id}` | Обновить достижение (bonus_points, occurrence_type, applicable_roles, can_appear_on_random_cards) |

**Card Management:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/card-templates` | Create card template (body: `fantasyPlayerId`, rarity, …) |
| PUT | `/card-templates/{id}` | Update card template |
| GET | `/card-templates` | List templates: опционально `tournamentId`, `fantasyPlayerId`, `rarity` |
| POST | `/card-templates/{id}/achievements` | Add achievement to card template (achievement_id + optional bonus_points override) |
| POST | `/card-templates/{id}/image` | Upload card artwork (multipart) → S3 |
| POST | `/card-packs` | Create card pack (body: name, tournamentId, active, autoGenerated, priceFantiki, useAllTournamentPlayers, rarityConfigs, playerIds) |
| PUT | `/card-packs/{id}` | Update pack configuration |
| GET | `/card-packs` | List packs (optional `tournamentId`) |
| GET | `/card-packs/{id}` | Pack details (с player pool) |
| PUT | `/card-packs/{id}/players` | Обновить пул игроков для auto-gen пака |
| POST | `/users/{telegramUserId}/give-cards` | Give specific cards to user |
| POST | `/users/{telegramUserId}/open-pack/{packId}` | Open pack → карты (legacy: из глобального пула; auto-gen: на лету) |

**User Management (Fantiki):**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/users/{telegramUserId}/give-fantiki` | Начислить фантики (body: `{ amount }`) |

### 6.3 Порядок операций по серии (V3)

1. Игры синхронизируются (`POST .../sync-games`), при необходимости считаются очки (`POST .../calculate-scores`). Повторный расчёт допускается, пока серия не финализирована. Для активных серий периодический sync/score может выполнять планировщик (`ActiveSeriesSyncScheduler`, cron каждые 10 мин).
2. **`POST .../series/{id}/finalize`** — отдельный шаг: для каждой карты в каждой команде серии уменьшается `uses_remaining` (минимум 0); участникам команд начисляются фантики по позиции в лидерборде с учётом размера команды (1–3 карты); у серии выставляется `finalized = true` (и при операции из админки обычно `status = FINISHED`).
3. **Автофинализация:** при `PUT /admin/series/{id}` (или создании серии) если после сохранения `status == FINISHED` и `finalized == false`, вызывается тот же пайплайн финализации, что и у `POST .../finalize`.
4. Повторный вызов finalize для той же серии отклоняется (`400`). Пока `finalized = false`, нельзя переработать карту, если она стоит в команде этой серии.

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

## 10. Implementation History

### Phase 1 (V1): Core System

Реализация выполнена 6 агентами (A1–A6):

- **A1 (Foundation):** Gradle, Spring Boot, Flyway V1–V4, JPA entities, S3, Docker, CI/CD
- **A2 (polemica-library):** поле `name` в `PolemicaGame`, `getPlayerGames`
- **A3 (Admin Backend):** Tournament/Series/Card admin API, CardPackService, Basic Auth
- **A4 (Scoring + Game Sync):** PolemicaIntegrationService, GameSyncService, AchievementDetector (9 штук), ScoringService
- **A5 (User Backend + TMA):** TelegramAuthFilter, User API, TMA (React + TS + TanStack Query)
- **A6 (Admin Frontend):** Ant Design admin panel

### Phase 2 (V2): Economy & Enhanced Scoring

Реализация выполнена 7 агентами (B1–B7):

- **B1 (Schema V2):** Flyway V5–V9, новые entities (Achievement, FantikiTransaction, CardPackPlayer, FantasyTeamCardGameScore, FantasyTeamCardGameAchievement), `Rarity.scoreModifier`, удалён `AchievementType` enum
- **B2 (Achievement Refactor + Scoring V2):** справочник достижений, `applicableRoles` + `occurrenceType` при скоринге, формула с `rarity_modifier`, per-game breakdown
- **B3 (Фантики + Store):** баланс пользователя, `fantiki_transaction`, `UserStoreService`, Store API (`GET /store/packs`, `POST /store/packs/{id}/buy`), admin `give-fantiki`
- **B4 (Auto-gen Packs):** генерация карт при открытии, пул игроков, переиспользование шаблонов, валидация LEGENDARY
- **B5 (Admin Frontend V2):** страница достижений, паки V2 (auto-gen, цена, пул игроков), начисление фантиков
- **B6 (TMA Frontend V2):** баланс фантиков в хедере, магазин паков, per-game детализация очков
- **B7 (Pack Opening Animation):** анимация открытия пака (glow по редкости, flip, particles)

### Phase 3 (V3): Card contracts & closed-loop economy

Реализация по архивному плану [`PLAN-V3.md`](../plans/archive/PLAN-V3.md) (блоки C1–C5):

- **C1 (Schema + entities):** Flyway `V13__economy_contracts`, поля `user_card.uses_remaining` / `times_renewed`, `series.finalized`, таблица `economy_config` + сиды; enum-причины фантиков для recycle/renewal/series reward; DTO `UserCardItem` с контрактом.
- **C2 (Backend lifecycle):** `EconomyConfigService`, `CardLifecycleService` (recycle/renew), `SeriesFinalizationService`; выдача карт с uses из конфига; проверка uses при `POST/PUT` fantasy-team; user/admin API см. §6.1–6.2.
- **C3 (Economy admin API):** CRUD-обновление `economy_config`, инвалидация кэша.
- **C4 (Admin SPA):** страница `/economy`, кнопка финализации серии, признак `finalized` в списке/деталке серии.
- **C5 (TMA):** бейджи использований, коллекция (фильтры/переработка/продление), экран **«Справка»** `/help` (механика очков, достижения, экономика из `economy-info`; редирект со старого `/economy`), сборка команды с блокировкой истёкших карт и предупреждением о последнем использовании.

### Phase 4+ (после V3): UX, легендарки, инфраструктура

- **Неполные команды и награды:** 1–3 карты в команде; при финализации награда за место масштабируется ⌈⅓⌉ / ⌈⅔⌉ / 100% (`SeriesFinalizationService.scaleSeriesRewardByRosterSize`).
- **Порядок серий:** API отдаёт серии турнира **от новых к старым** (`series.id` DESC); UI номера серий не должен путаться с порядком списка.
- **Ростер серии:** при смене состава до дедлайна с команды снимаются карты игроков, выпавших из ростера (`FantasyTeamRosterPruningService`).
- **Бесплатные паки:** `card_pack.free_opens_per_user` + `user_card_pack_free_usage`.
- **Легендарный апгрейд:** EPIC → LEGENDARY in-place, API `GET/POST /api/v1/legendary-upgrade`, поле `crafted_by_telegram_user_id`, лимит LEGENDARY в команде — см. [`features/DESIGN-LEGENDARY-CARDS.md`](../features/DESIGN-LEGENDARY-CARDS.md).
- **Отображаемое имя:** `telegram_user.display_name`, `PATCH /api/v1/me`.
- **Sync/scoring:** HTTP к Polemica вне длинных `@Transactional`; запись в БД короткими транзакциями (`TransactionTemplate`).
- **Операционно:** деплой TMA / админки / бэкенда (Docker, nginx) — см. §12 и `memory-bank/activeContext.md`.

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
