# System Patterns

## Архитектура

**Monolithic backend + 2 SPA frontends.**

Backend — единый Spring Boot сервис с двумя группами эндпоинтов (user API и admin API), разделёнными на уровне URL-префиксов и security-фильтров.

Два фронтенда — отдельные React-приложения:
- `polemica-fantasy-webapp` — Telegram Mini App для пользователей
- `polemica-fantasy-admin` — веб-админка

**Продакшен (VPS):** как быстро пересобрать бэкенд (Docker), выложить `dist` TMA и админки, обновить `.env` — в [`techContext.md`](techContext.md), раздел «Быстрое обновление на VPS после правок».

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
- **DTO separation:** entity-классы не выходят за пределы service layer; контроллеры работают с DTO
- **JSONB caching:** полные данные игр из Полемики кэшируются в PostgreSQL JSONB для оффлайн-скоринга
- **Strategy pattern для достижений:** каждый AchievementType реализует интерфейс `AchievementDetector` с методом `detect(game: PolemicaGame, playerPosition: Position): Boolean`
- **Manual trigger scoring:** расчёт очков запускается вручную через админку (не real-time)

### Security
- User API: `TelegramAuthFilter` в цепочке только для путей `/api/v1/**` кроме `/api/v1/admin/**` (`UserApiRequestMatcher`); заголовок `Authorization: tma <initData>`; HMAC в `TelegramInitDataValidator`. Экземпляр фильтра создаётся внутри `userApiSecurityFilterChain`, не как отдельный `@Bean` типа `Filter` — иначе Spring Boot регистрирует глобальный servlet filter.
- Admin API: Basic Auth (`InMemoryUserDetailsManager`), matcher `/api/v1/admin/**`, `@Order(1)`
- Default: `@Order(3)` — `permitAll` (actuator и т.д.)

## Паттерны фронтенда

### Telegram Mini App
- `@telegram-apps/sdk-react` для интеграции с Telegram
- TanStack Query для server state management
- Компонентный подход (React)

### Admin Panel
- Ant Design как UI framework
- React Router для навигации
- TanStack Query для data fetching

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
- При **`STANDALONE`**: прежняя логика — пересечение профильных матчей участников серии + префикс названия; `series.game_num_*` NULL.
- Смена `kind` / `polemica_competition_id` при существующих сериях у турнира — **409 CONFLICT**.

## Модель игроков и карточек

- **`fantasy_player`** — глобальная сущность: `polemica_user_id` (уникально), ник, фото. Создаётся/находится при добавлении игрока в турнир.
- **`tournament_player`** — связь «турнир ↔ fantasy_player» (участие в ростере турнира).
- **`card_template`** ссылается на **`fantasy_player`**, не на `tournament_player`: одна карточка одного игрока может участвовать в командах по разным турнирам/сериям, если этот игрок попал в серию через `series_player`.
- Открытие пака: выбор шаблонов по редкости из **глобального** пула (не ограниченного турниром пака).

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
| SSH deploy | Простота: git pull + docker compose up -d; достаточно для single server |
| Глобальный `fantasy_player` + `card_template` → FK на него | Одна карточка реального игрока переиспользуется между турнирами; ростер турнира/серии остаётся явным через `tournament_player` / `series_player` |
