# AGENTS.md — Polemica Fantasy

## Project Overview

Polemica Fantasy — fantasy-sports service for competitive Mafia (the social deduction game). Users collect player cards and compete by assembling fantasy teams of 1–3 cards for game series. Points are scored based on real game results from the Polemica platform.

**Production:** TMA `https://fantasy.maftourbot.ru`, Admin `https://admin.fantasy.maftourbot.ru`

## Architecture

Monolithic backend + 2 SPA frontends:

```
polemica-fantasy-backend/    Kotlin · Spring Boot 3.4.2 · JDK 21 · PostgreSQL 16 · Flyway
polemica-fantasy-webapp/     React 19 · TypeScript · Vite 8 · Telegram Mini App
polemica-fantasy-admin/      React 19 · TypeScript · Vite 8 · Ant Design 6
```

```
Controller → Service → Repository → PostgreSQL
                 ↓
          Polemica Client → Polemica API (external)
```

Two API groups on the same backend:
- **User API** `/api/v1/**` (excluding `/api/v1/admin/**`) — TMA auth via `Authorization: tma <initData>` (HMAC validation)
- **Admin API** `/api/v1/admin/**` — Basic Auth (`InMemoryUserDetailsManager`)

## Codex Workflow

- Start non-trivial tasks by reading this file plus the relevant `memory-bank/` files. For broad/product work, read `activeContext.md`, `progress.md`, `systemPatterns.md`, and `techContext.md`; for small fixes, read the narrowly relevant files first.
- Check `git status --short` before editing. Preserve user changes and keep edits scoped to the requested feature or fix.
- Keep backend/user/admin API contracts synchronized: Kotlin DTOs, frontend `src/api/types.ts`, API clients, and UI call sites should move together.
- After meaningful feature, architecture, dependency, or deployment changes, append a short dated note to `memory-bank/activeContext.md` and update `memory-bank/progress.md` when status changed.
- Prefer the focused verification commands below. Use `./scripts/codex-check.sh quick` before handing off broad cross-module changes when dependencies are installed locally.
- For interactive local UI testing, use the project skill at `.codex/skills/polemica-local-testing` when available. It covers `local-up`, browser checks, and fresh TMA `VITE_DEV_INIT_DATA` generation.

## Tech Stack

| Layer | Stack |
|-------|-------|
| Backend | Kotlin 2.3.0, Gradle 9.0.0 (Kotlin DSL), Spring Boot 3.4.2, Spring Data JPA, Flyway, Spring Security, AWS SDK v2 (S3) |
| User frontend | React 19, Vite 8, `@telegram-apps/sdk-react`, TanStack Query 5, React Router 7 |
| Admin frontend | React 19, Vite 8, Ant Design 6, TanStack Query 5, React Router 7, dayjs |
| External | `io.github.mralex1810:polemica-library:1.8.8` (Polemica API client), PostgreSQL 16, S3 (MinIO in dev, Yandex Object Storage in prod) |
| Infra | Docker (multi-stage), Docker Compose, GitHub Actions (GHCR + deploy), Nginx, Prometheus/Actuator |

## Backend Package Structure

```
io.github.mralex1810.fantasy
├── config/           # Spring beans: S3, Security, Telegram, Polemica, Admin properties
├── auth/             # TelegramAuthFilter, TelegramInitDataValidator, TelegramAuthentication
├── entity/           # JPA @Entity classes (TournamentKind, FantasyPlayer, TournamentPlayer, etc.)
├── repository/       # JpaRepository interfaces
├── dto/              # Request/Response DTOs (user/ and admin/ subpackages)
├── service/          # Business services (UserService, MarketplaceService, CardLifecycleService, etc.)
├── scoring/          # ScoringService, AchievementDetector strategy pattern, AchievementDetectorRegistry
├── polemica/         # PolemicaIntegrationService, DefaultGameSyncService
├── controller/
│   ├── user/         # User-facing controllers
│   └── admin/        # Admin controllers
├── event/            # Domain events (finalization, notifications, marketplace)
├── telegram/         # TelegramBotApiClient, support relay
├── schedule/         # ActiveSeriesSyncScheduler (every 10 min)
└── util/             # Helpers (SeriesGameDisplayName, etc.)
```

## Key Domain Concepts

- **`fantasy_player`** — global entity per real Mafia player (`polemica_user_id`, unique). Card templates reference this, not tournament-specific entries.
- **`tournament_player`** — links a tournament to a fantasy_player (roster membership).
- **`card_template`** — references `fantasy_player` with rarity (COMMON/RARE/EPIC/LEGENDARY) and achievements. Reusable across tournaments.
- **`user_card`** — owned card instance with `uses_remaining` and `times_renewed`.
- **`TournamentKind`** — `STANDALONE` (match by player profile overlap + `name_prefix`) or `POLEMICA_COMPETITION` (games from competition by `game_num_from`/`game_num_to` range, optional `game_phase` filter).
- **Leagues** — MAIN + BUDGET per series; budget has `value_cap`; rewards scale by `reward_scale`; uses decremented per league.
- **Scoring** — `(base_points + Σ achievement_bonus) × rarity_modifier`, per-game breakdown stored in DB; base points from `GamePointsService` (Polemica public match page); only finished games (`result != null`) scored.
- **Economy** — fantiki (in-game currency), card contracts (uses/renewal/recycle), series rewards, marketplace (commission, min/max prices).

## Patterns and Conventions

### Backend
- **Layered architecture**: Controller → Service → Repository. Entities never leave the service layer — controllers work with DTOs.
- **JSONB caching**: Full Polemica game data cached in PostgreSQL JSONB for offline scoring.
- **Strategy pattern for achievements**: Each `AchievementDetector` has `type: String` matching `achievement.id` in DB, method `matchCount(game, player)`.
- **Sync/scoring outside transaction**: HTTP calls to Polemica API are NOT inside `@Transactional`. Data is fetched first, then persisted in a short transaction via `TransactionTemplate` to avoid Hikari pool exhaustion.
- **Event-driven notifications**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` for Telegram messages (finalization, marketplace sale, roster changes, deadline reminders, marketplace watches). `NotificationDeliveryService` centralizes delivery with retry on 429, mark `bot_blocked` on 403.
- **Telegram Bot API**: `TelegramBotApiClient` using `RestClient`. URL built as absolute `URI.create("https://api.telegram.org/bot{token}/...")` — NOT via RestClient template (Spring encodes `:` in token as `%3A` → 404).
- **Auto-finalization**: Setting series status to `FINISHED` triggers `SeriesFinalizationService.finalizeSeries` automatically.
- **Scheduled sync**: `ActiveSeriesSyncScheduler` runs every 10 min for `ACTIVE`/`SCORING` non-finalized series. Disabled in tests (`spring.task.scheduling.enabled: false`).

### Frontend (both apps)
- TanStack Query 5 for server state management.
- React Router 7 for routing.
- Vite dev proxy: `/api` → `localhost:8080`.
- Card image URL: `playerPhotoUrl ?? imageUrl` (helper in `lib/cardImage.ts`).
- Rarity visual: CSS modifiers `*--common | *--rare | *--epic | *--legendary`.

### Frontend (TMA-specific)
- `@telegram-apps/sdk-react` for Telegram integration; `VITE_DEV_INIT_DATA` for local dev.
- Dark theme, gradient CTAs, card frames by rarity.

### Frontend (Admin-specific)
- Ant Design 6 as UI framework.
- Basic Auth credentials in `sessionStorage`, header from `api/client.ts`.

## Database Migrations

Flyway manages schema. `spring.jpa.hibernate.ddl-auto=validate`.
Migrations are in `polemica-fantasy-backend/src/main/resources/db/migration/` (V1 through V42+).

## Testing

- **Quick cross-module check**: `./scripts/codex-check.sh quick` runs backend Kotlin compilation plus both frontend builds.
- **Backend**: Testcontainers PostgreSQL 16. Key test classes: `AdminApiIntegrationTest`, `UserApiIntegrationTest`, `TelegramInitDataValidatorTest`, `CardPackRarityConfigValidationTest`, `CardPackFindOrCreateTemplateIntegrationTest`, `SeriesFinalizationServiceTest`, `CardLifecycleServiceTest`, achievement detector tests.
- **Frontend**: `npm run build` as verification (no unit test suite).
- Run backend compile check: `cd polemica-fantasy-backend && ./gradlew compileKotlin compileTestKotlin`
- Run backend tests: `cd polemica-fantasy-backend && ./gradlew test`
- Run specific: `./gradlew test --tests "io.github.mralex1810.fantasy.XXX"`

## Build & Run (Local Dev)

```bash
# Start PostgreSQL + MinIO
docker compose up -d

# Backend (from repo root)
cd polemica-fantasy-backend && ./gradlew bootRun

# TMA frontend
cd polemica-fantasy-webapp && npm ci && npm run dev

# Admin frontend
cd polemica-fantasy-admin && npm ci && npm run dev
```

Fresh local TMA auth:

```bash
./scripts/generate-tma-init-data.py
./scripts/local-up.sh --generate-init-data
```

Dev proxy in both frontends: `/api` → `http://localhost:8080`.

For TMA auth in local dev: set `VITE_DEV_INIT_DATA` in webapp `.env`.

## Deployment (VPS)

Host: `mafia@51.250.18.236`, repo: `~/polemica-fantasy` (git clone via SSH, branch `master`).

```bash
# Backend update
ssh mafia@VPS 'cd ~/polemica-fantasy && git pull origin master && docker compose -f docker-compose.prod.yml up -d --build fantasy-backend'

# TMA update (on VPS after git pull)
cd polemica-fantasy-webapp && npm ci && npm run build
sudo rsync -a --delete dist/ /var/www/fantasy.maftourbot.ru/

# Admin update (on VPS after git pull)
cd polemica-fantasy-admin && npm ci && npm run build
sudo rsync -a --delete dist/ /var/www/admin.fantasy.maftourbot.ru/
```

Health check: `curl -sS http://127.0.0.1:18081/actuator/health`

Secrets: `~/polemica-fantasy/.env` (not in git). Key vars: `TELEGRAM_BOT_TOKEN`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `POLEMICA_USERNAME`, `POLEMICA_PASSWORD`, S3 credentials.

## Configuration (`application.yml`)

- `spring.datasource.*` — PostgreSQL
- `spring.flyway.*` — migrations
- `polemica.api.base-url`, `polemica.api.username/password` — Polemica API
- `telegram.bot.token` — TMA validation + Bot API
- `telegram.bot.notifications.enabled` — toggle Telegram notifications
- `telegram.support.*` — webhook support relay (forum topics)
- `s3.endpoint/region/bucket/access-key/secret-key` — S3 storage
- `app.admin.username/password` — Basic Auth for admin API
- `easter-egg.tyulenchik-image-url` — Easter egg companion image

## Key External Dependencies

- **polemica-library** (`io.github.mralex1810:polemica-library:1.8.8`): Kotlin client for Polemica API. Source at `../polemica-library/` for local development. Uses `mavenLocal()` in `build.gradle.kts`. Publish locally: `cd ../polemica-library && ./gradlew publishToMavenLocal`.

## Documentation

- `docs/architecture/DESIGN.md` — System Design Document
- `docs/features/DESIGN-MARKETPLACE.md` — Marketplace spec
- `docs/features/DESIGN-LEGENDARY-CARDS.md` — Legendary upgrade spec
- `docs/features/DESIGN-CARD-VALUE-AND-LEAGUES.md` — Card value & leagues spec
- `docs/features/DESIGN-NOTIFICATIONS.md` — Notifications spec
- `memory-bank/` — Detailed project context (product, tech, patterns, progress, active context)

## Common Pitfalls

- **Hibernate flush ordering**: After bulk-delete + re-insert on unique constraints, call `flush()` between operations to avoid `23505` errors.
- **Lazy collections after mutation**: After `attachCards` / `createFantasyTeam`, call `entityManager.flush()` + `refresh(entity)` before mapping to DTO.
- **Roster pruning**: `FantasyTeamRosterPruningService` uses `saveAndFlush` per card (not `saveAll`) to avoid unique constraint violations on slot renumbering.
- **Telegram Bot API URL**: Must use `URI.create(...)` not RestClient template variables — Spring URL-encodes `:` in bot token, breaking the URL.
- **Polemica library quirks**: `mmr` can be object or number (custom deserializer), `candidate: 0` in votes → null, `referee` field can be null.
