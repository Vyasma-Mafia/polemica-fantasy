# Polemica Fantasy

Монорепозиторий: бэкенд (Kotlin / Spring Boot), Telegram Mini App и веб-админка (React + TypeScript).

## Документация

| Раздел | Назначение |
|--------|------------|
| [`docs/README.md`](docs/README.md) | Указатель файлов в `docs/` |
| [`docs/architecture/DESIGN.md`](docs/architecture/DESIGN.md) | System Design Document (домен, API, скоринг, деплой) |
| [`memory-bank/`](memory-bank/) | Оперативный контекст проекта для разработки и ассистентов |

Исторические планы (V2/V3) и спеки изменений лежат в [`docs/plans/archive/`](docs/plans/archive/).

## Модули

- **`polemica-fantasy-backend`** — REST API, Flyway, интеграция с Polemica
- **`polemica-fantasy-webapp`** — пользовательский TMA (Vite + React)
- **`polemica-fantasy-admin`** — админ-панель (Vite + React + Ant Design)
- **`agent-runtime`** — экспериментальный Codex-агент с типизированными Fantasy,
  Polemica Research и Memory MCP-серверами, долговременным журналом решений и
  изолированным почасовым runner

## Codex fantasy agent и MCP

Экспериментальный агент играет через обычный user API и подчиняется тем же
доменным ограничениям, что и пользователь. Fantasy MCP предоставляет закрытый
набор игровых операций, Research MCP собирает доступную историю Polemica, а
Memory MCP хранит запечатанные снимки данных, решения и результаты операций.
Произвольного HTTP, SQL, shell или доступа к admin API у модели нет.

Архитектура и границы эксперимента описаны в
[`docs/features/DESIGN-AI-FANTASY-AGENT.md`](docs/features/DESIGN-AI-FANTASY-AGENT.md),
инструкции runtime — в [`agent-runtime/README.md`](agent-runtime/README.md).

## Разработка через Codex

Основные инструкции для агента лежат в [`AGENTS.md`](AGENTS.md), долговременный контекст — в [`memory-bank/`](memory-bank/). После заметных фич, архитектурных изменений или обновления зависимостей стоит просить Codex обновить `memory-bank/activeContext.md` и `memory-bank/progress.md`.

Быстрая проверка перед сдачей изменений:

```bash
./scripts/codex-check.sh quick
```

Она компилирует backend и собирает оба фронтенда. Для точечных прогонов доступны цели `backend`, `backend-test`, `webapp`, `admin`, `frontend`, `lint`.

## Локальный запуск

Шаблон переменных окружения: [`.env.example`](.env.example). Dev-стек: PostgreSQL + MinIO + бэкенд — [`docker-compose.yml`](docker-compose.yml).

### Быстрый старт всего стека (backend + admin + TMA)

```bash
./scripts/local-up.sh --init-data "auth_date=...&user=...&hash=..."
```

Для TMA можно сгенерировать свежий `VITE_DEV_INIT_DATA` из `TELEGRAM_BOT_TOKEN` в `.env`:

```bash
./scripts/local-up.sh --generate-init-data
```

Скрипт:
- поднимает `fantasy-backend` через `docker compose` (вместе с БД и MinIO),
- запускает dev-серверы админки и TMA,
- пишет логи в `.local-dev-logs/admin.log` и `.local-dev-logs/tma.log`.

Порты и host можно переопределить:

```bash
ADMIN_PORT=5174 TMA_PORT=5175 DEV_HOST=0.0.0.0 ./scripts/local-up.sh
```
