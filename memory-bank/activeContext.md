# Active Context

## Текущий фокус
**Деплой на VPS:** TMA `https://fantasy.maftourbot.ru`, админка `https://admin.fantasy.maftourbot.ru`, бэкенд в Docker (`docker-compose.prod.yml`). На сервере **`~/polemica-fantasy`** — **git clone** ветки **`master`** (`git@github.com:Vyasma-Mafia/polemica-fantasy.git`, SSH); типовое обновление: **`git pull origin master`** → `docker compose … up -d --build` → при необходимости сборка SPA и `sudo rsync` в `/var/www/...`. Подробно и запасной **rsync** с локальной машины — **«Быстрое обновление на VPS после правок»** и **«Deployment (VPS…)»** в [`techContext.md`](techContext.md).

**Agent A6 выполнен:** Admin SPA `polemica-fantasy-admin/` (Vite + React 19 + TS + Ant Design 6 + TanStack Query + React Router 7). Создание турнира с `POLEMICA_COMPETITION`: поле **Polemica competition ID** — `InputNumber` (прямой ввод id), без загрузки списка соревнований и без спиннера в селекте (`TournamentFormModal.tsx`). Basic Auth через форму логина → `sessionStorage` (`polemica_admin_basic_b64`), `api/client.ts` добавляет `Authorization` ко всем запросам. Страницы: турниры, деталь турнира (игроки, фото, серии), серия (редактирование, assign players, sync games, calculate scores), шаблоны карт, паки, user tools (give cards / open pack). Dev proxy `/api` → 8080. Дополнительно к A3: read API `GET /api/v1/admin/tournaments/{id}/series`, `GET /api/v1/admin/series/{id}`, `GET /api/v1/admin/card-packs` (опционально `tournamentId`) — для списков в UI.

**Agent A5 (предыдущий):** User API под `/api/v1` с `Authorization: tma <initData>`, TMA `polemica-fantasy-webapp/`.

## Текущие решения
- **Карточки не привязаны к турниру:** таблица `fantasy_player` (уникальный `polemica_user_id`), `card_template.fantasy_player_id`; `tournament_player` — только связь турнир ↔ игрок; состав серии и фэнтези-команда проверяются по участию `FantasyPlayer` в серии. Подробно: [`DESIGN.md`](../DESIGN.md) §4, §6; миграция `V3__fantasy_player_global_cards.sql`.
- Язык бэкенда: Kotlin (не Java)
- Админка: отдельное React веб-приложение (не TMA)
- Турнир: `TournamentKind` (`STANDALONE` | `POLEMICA_COMPETITION`); при втором — `polemica_competition_id` на турнире, серии задают диапазон `num`; см. [`DESIGN.md`](../DESIGN.md) §2, §7.
- Sync игр: ветвление в `DefaultGameSyncService` по `kind`; админка: выбор Competition и полей серии под тип турнира.
- Карточки выдаются только через админа (вручную или через паки)
- Скоринг запускается вручную через админку
- S3: AWS SDK Java v2, MinIO в dev с path-style и автосозданием bucket
- Образ backend: CI пушит в **GHCR** (`ghcr.io/<owner>/polemica-fantasy-backend`); на VPS prod-сборка чаще из **`docker-compose.prod.yml`** (`build:` локально на сервере после `git pull`). Секреты: только **`~/polemica-fantasy/.env`** (не в git)

## Следующие шаги
1. По желанию: **A2** доп. методы в polemica-library (оптимизация списка игр игрока) — не блокер
2. Опционально: admin read API для текущего состава серии (сейчас assign через `POST` без GET списка участников серии)
3. Опционально: серверный агрегат лидерборда по турниру (сейчас TMA суммирует по сериям на клиенте)

## Открытые вопросы
- Точный список достижений (achievement types) — будет определён позже
- Формула базовых очков игрока (award vs GamePointsService) — нужно уточнить

## Недавно сделано (TMA UX)
- **Карточки в TMA:** изображение карточки — `playerPhotoUrl` с запасным вариантом `imageUrl` (`lib/cardImage.ts`); обводка по редкости на коллекции/сборке команды и в истории фэнтези (в т.ч. модалка деталей).
- **Webapp:** тёмная тема (DM Sans + Fraunces), хаб турнира, выбор серии («дни»), лидерборд турнира (вкладки «Общий» + серии, агрегация на клиенте), правила, история фэнтези (`GET /me/fantasy-teams`), коллекция с фильтрами и табами редкости, обновлённые Team / Series / Leaderboard страниц.
- **User API:** `GET /api/v1/tournaments/{id}/participants` — ростер турнира для страницы участников (`UserTournamentService` + `TournamentController`).

## Блокеры
- Нет критичных блокеров для A5/A6
