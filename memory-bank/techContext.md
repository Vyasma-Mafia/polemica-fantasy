# Tech Context

## Backend Stack

| Технология | Версия | Назначение |
|------------|--------|------------|
| Kotlin | 2.3.0 | Язык бэкенда |
| JDK | 21 | Runtime (через Gradle toolchain) |
| Gradle | 9.0.0 | Build system (Kotlin DSL) |
| Spring Boot | 3.4.2 | Web framework |
| Spring Data JPA | via Spring Boot | ORM / Repository layer |
| Hibernate | via Spring Boot | JPA implementation |
| Flyway | via Spring Boot | Database migrations |
| Spring Security | via Spring Boot | Auth (TMA + Admin) |
| Jackson | via Spring Boot | JSON serialization |
| PostgreSQL | 16+ | Primary database |
| polemica-library | 1.8.1 | Polemica API client (`io.github.mralex1810:polemica-library`); `getProfileGames`, `getMatch`, игры турниров/клубов; в `build.gradle.kts` также `mavenLocal()` |

## Frontend Stack (User — TMA)

| Технология | Назначение |
|------------|------------|
| React 19 | UI (`polemica-fantasy-webapp/`) |
| TypeScript | Type safety |
| Vite 8 | Build tool, dev proxy `/api` → `localhost:8080` |
| @telegram-apps/sdk-react / @telegram-apps/sdk | `retrieveRawInitData`, `InitDataProvider` + опционально `VITE_DEV_INIT_DATA` |
| TanStack Query 5 | Server state |
| React Router 7 | Routing |

## Frontend Stack (Admin)

| Технология | Назначение |
|------------|------------|
| React 19 | UI (`polemica-fantasy-admin/`) |
| TypeScript | Type safety |
| Vite 8 | Build tool, dev proxy `/api` → `localhost:8080` |
| Ant Design 6 + @ant-design/icons | UI |
| dayjs | DatePicker / серии (ISO ↔ backend `Instant`) |
| React Router 7 | Routing |
| TanStack Query 5 | Server state |
| Basic Auth | Учётные данные в `sessionStorage`, заголовок из `api/client.ts` |

## Infrastructure

| Технология | Назначение |
|------------|------------|
| Docker | Multi-stage build (gradle:jdk21 → eclipse-temurin:21-jdk-jammy) |
| Docker Compose | Dev: PostgreSQL 16 + MinIO + backend; Prod: `docker-compose.prod.yml` — PostgreSQL + backend only; S3 = Yandex (`S3_ENDPOINT=https://storage.yandexcloud.net`, `S3_REGION=ru-central1`, bucket из `.env`, пароль статического ключа как в overlay) |
| S3 (`software.amazon.awssdk:s3` + BOM 2.29.x) | Хранение фотографий игроков и артворков карточек |
| MinIO | S3-совместимый storage для локальной разработки |
| GitHub Actions | CI/CD: build image → push to GHCR → deploy via SSH |
| GHCR | GitHub Container Registry для Docker images |
| cosign | Подпись Docker images (Sigstore) |
| Prometheus + Actuator | Мониторинг (опционально, management port 8081) |
| Nginx (будущее) | Reverse proxy для frontend SPA + backend API |

## Deployment (VPS fantasy.maftourbot.ru)

- **Репозиторий на сервере:** `~/polemica-fantasy` — **git clone** ветки **`master`**, remote `git@github.com:Vyasma-Mafia/polemica-fantasy.git`. У пользователя `mafia` на ВМ настроен SSH-доступ к GitHub; **HTTPS `git clone`/`pull` с сервера не подходит** для этого репо (приватный доступ). Секреты только в **`~/polemica-fantasy/.env`** (файл не в git; при переустановке каталога — сохранить `.env` вручную и вернуть после `git clone`).
- **Compose:** `docker compose -f docker-compose.prod.yml up -d --build` в `~/polemica-fantasy` на VPS (`51.250.18.236`). Отдельный файл от dev: MinIO без портов на хост, PostgreSQL и API только на `127.0.0.1` (`15433`, `18080`, `18081` management/Actuator).
- **Nginx:** `server_name fantasy.maftourbot.ru` — TMA в `/var/www/fantasy.maftourbot.ru`, [`deploy/nginx-fantasy.maftourbot.ru.conf`](../deploy/nginx-fantasy.maftourbot.ru.conf). **Админка:** `https://admin.fantasy.maftourbot.ru` — статика `/var/www/admin.fantasy.maftourbot.ru`, тот же `proxy_pass` для `/api/`, [`deploy/nginx-admin.fantasy.maftourbot.ru.conf`](../deploy/nginx-admin.fantasy.maftourbot.ru.conf). HTTPS — certbot.
- **Webapp:** на VPS установлен **Node.js 22.x** (NodeSource); `npm ci && npm run build` в `polemica-fantasy-webapp/` проходит на сервере. Статику по-прежнему можно выкладывать через `rsync` `dist/` → `/var/www/fantasy.maftourbot.ru/`.
- **Секреты:** `~/polemica-fantasy/.env` — обязательно `TELEGRAM_BOT_TOKEN` для TMA; после изменения `.env`: `docker compose -f docker-compose.prod.yml up -d`.

## Быстрое обновление на VPS после правок

Ориентиры: хост **`mafia@51.250.18.236`**, каталог на сервере **`~/polemica-fantasy`**, ключ SSH **`~/personal/mafia/id_rsa`**. Сначала изменения **пушатся в GitHub** с локальной машины, затем на ВМ.

**Типовой цикл (репозиторий уже клонирован на сервере):**

```bash
ssh -i ~/personal/mafia/id_rsa mafia@51.250.18.236
cd ~/polemica-fantasy && git pull origin master
docker compose -f docker-compose.prod.yml up -d --build fantasy-backend
```

При изменениях только во **frontend** — после `git pull` на сервере: `npm ci && npm run build` в `polemica-fantasy-webapp/` и/или `polemica-fantasy-admin/`, затем `sudo rsync` в соответствующие каталоги `/var/www/...` (см. ниже).

### Бэкенд (`polemica-fantasy-backend/`)

1. Доставить код на сервер:
   - **основной способ:** **`git pull origin master`** в `~/polemica-fantasy` на ВМ (после push в GitHub).
   - **запасной (без git на сервере):** **rsync** с локальной машины:  
     `rsync -avz --delete --exclude build --exclude .gradle -e "ssh -i ~/personal/mafia/id_rsa" polemica-fantasy-backend/ mafia@51.250.18.236:~/polemica-fantasy/polemica-fantasy-backend/`
2. Пересобрать и перезапустить **только API** (остальные контейнеры не трогаются):  
   `ssh -i ~/personal/mafia/id_rsa mafia@51.250.18.236 'cd ~/polemica-fantasy && docker compose -f docker-compose.prod.yml up -d --build fantasy-backend'`
3. Только **изменения `.env`** (без пересборки образа):  
   `ssh … 'cd ~/polemica-fantasy && docker compose -f docker-compose.prod.yml up -d fantasy-backend'`  
   (контейнер пересоздастся с новыми переменными окружения).

После деплоя health: на сервере `curl -sS http://127.0.0.1:18081/actuator/health` (management на **18081**).

### Telegram Mini App (`polemica-fantasy-webapp/`)

1. Сборка: **`npm ci && npm run build`** в `polemica-fantasy-webapp/` (локально или на VPS — на сервере Node 22).
2. Выкладка статики в корень TMA:  
   `rsync -avz -e "ssh -i ~/personal/mafia/id_rsa" polemica-fantasy-webapp/dist/ mafia@51.250.18.236:~/polemica-fantasy/polemica-fantasy-webapp/dist/`  
   `ssh -i ~/personal/mafia/id_rsa mafia@51.250.18.236 'sudo rsync -a --delete ~/polemica-fantasy/polemica-fantasy-webapp/dist/ /var/www/fantasy.maftourbot.ru/'`

Публичный URL: **`https://fantasy.maftourbot.ru`**. Для same-origin API **`VITE_API_BASE_URL` не задаётся** (запросы на `/api/...` через nginx).

### Админка (`polemica-fantasy-admin/`)

1. Сборка: **`npm ci && npm run build`** в `polemica-fantasy-admin/`.
2. Выкладка:  
   `rsync -avz -e "ssh -i ~/personal/mafia/id_rsa" polemica-fantasy-admin/dist/ mafia@51.250.18.236:~/polemica-fantasy/polemica-fantasy-admin/dist/`  
   `ssh -i ~/personal/mafia/id_rsa mafia@51.250.18.236 'sudo rsync -a --delete ~/polemica-fantasy/polemica-fantasy-admin/dist/ /var/www/admin.fantasy.maftourbot.ru/'`

Публичный URL: **`https://admin.fantasy.maftourbot.ru`**.

### Что трогать при типичных изменениях

| Изменение | Действие |
|-----------|----------|
| Kotlin / `application.yml` / Dockerfile | раздел «Бэкенд» |
| Flyway-миграции | как бэкенд; перед деплоем проверить порядок миграций |
| React TMA | раздел «Telegram Mini App» |
| React Admin | раздел «Админка» |
| Секреты, токен бота, пароль админа | правка `~/polemica-fantasy/.env` на сервере + `docker compose … up -d fantasy-backend` |

## Deployment (общий паттерн)

Паттерн взят из проекта `overlay` (`~/personal/mafia/overlay`):
- Multi-stage Dockerfile: dependency caching → build → minimal runtime image
- Docker Compose с healthcheck на PostgreSQL
- CI: push to **`master`** → build + push to GHCR + cosign
- Deploy на VPS: вручную — `git pull` + `docker compose … up -d --build` (образ бэкенда собирается на сервере из `docker-compose.prod.yml`); либо GitHub Actions **workflow_dispatch** → SSH → `git pull` + `docker compose pull` + `up -d` (если compose на сервере переведён на образы из GHCR)
- Secrets через .env файл (не в git)
- S3-провайдер для prod: Yandex Object Storage (endpoint: storage.yandexcloud.net, region: ru-central1)

## Ключевые зависимости polemica-library

Библиотека написана на Kotlin, использует Spring WebFlux WebClient.

Пакеты:
- `client` — PolemicaClient, PolemicaClientImpl, GamePointsClient
- `model.game` — PolemicaGame, PolemicaPlayer, Role, Position, Stage, и др.
- `utils` — GameUtils, RatingUtils, MetricsUtils, RoleUtils

Endpoint'ы API Полемики (через библиотеку):
- `GET /v1/clubs/{id}/games` — список игр клуба
- `GET /v1/clubs/{id}/games/{gameId}` — полная игра клуба
- `GET /v1/competitions` — список соревнований
- `GET /v1/competitions/{id}/members` — участники
- `GET /v1/competitions/{id}/metrics` — метрики/рейтинг
- История игрока для sync (режим STANDALONE): публичный `getProfileGames` + полная модель `getMatch(matchId)` (id строки профиля = match id)
- Режим POLEMICA_COMPETITION: `getGamesFromCompetition`, `getGameFromCompetition`, `getCompetitions`, `getCompetition`

Admin API (прокси к Polemica для UI): `GET /api/v1/admin/polemica/competitions`, `GET /api/v1/admin/polemica/competitions/{id}` (Basic Auth)

## Настройки и конфигурация

Файл `application.yml` должен содержать:
- `spring.datasource.*` — PostgreSQL connection
- `spring.flyway.*` — миграции
- `spring.jpa.hibernate.ddl-auto=validate` — Flyway управляет схемой
- `polemica.api.base-url` — URL API Полемики
- `polemica.api.profile-site-base-url` — база публичного сайта для `getProfileGames` (по умолчанию совпадает с API-хостом в конфиге)
- `polemica.api.username/password` — credentials для Polemica API (обязательны для sync-games)
- `telegram.bot.token` — токен бота для валидации initData
- `s3.endpoint` / `s3.region` / `s3.bucket` / `s3.access-key` / `s3.secret-key` — S3 storage
- `app.admin.username` / `app.admin.password` — Basic Auth для `/api/v1/admin/**` (env: `ADMIN_USERNAME`, `ADMIN_PASSWORD`)

## Текущее состояние проекта

Бэкенд (`polemica-fantasy-backend/`):
- Gradle 9.0.0 + Kotlin 2.3.0 + JDK 21 + Spring Boot 3.4.2
- JPA, Flyway, Security, Actuator/Prometheus, PostgreSQL driver, AWS S3 SDK v2, polemica-library 1.8.1
- `src/main`: `FantasyApplication`, `config/` (S3, Security, `TelegramProperties`, AdminProperties, `PolemicaProperties`, `PolemicaConfig`), `auth/` (`TelegramInitDataValidator`, `TelegramAuthFilter`, `TelegramAuthentication`, `UserApiRequestMatcher`), `entity/` (в т.ч. `TournamentKind`, `FantasyPlayer`, `TournamentPlayer`, `CardTemplate`), `repository/`, `service/` (в т.ч. `UserService`, `UserTournamentService`, `UserSeriesService`, `UserCardCollectionService`, `UserFantasyTeamService`), `controller/user/*`, `controller/admin/*` (в т.ч. `PolemicaAdminController`), `dto/user/*`, `dto/admin/*`, `polemica/`, `scoring/`, `resources/application.yml`, Flyway: `V1` … `V4__tournament_kind_competition.sql`
- `src/test`: Testcontainers PostgreSQL 16; `AdminApiIntegrationTest`, `UserApiIntegrationTest`, `TelegramInitDataValidatorTest`, `CardPackServiceProbabilityTest`, `FantasyApplicationTests`, achievement unit tests
- Docker: multi-stage `Dockerfile`, артефакт `build/libs/app.jar` (bootJar)
- Корень репозитория: `docker-compose.yml`, `.env.example`

Фронтенды:
- `polemica-fantasy-webapp/` — Vite React TMA (см. README в каталоге)
- `polemica-fantasy-admin/` — Vite React admin (см. README в каталоге)
