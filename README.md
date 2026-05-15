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

## Локальный запуск

Шаблон переменных окружения: [`.env.example`](.env.example). Dev-стек: PostgreSQL + MinIO + бэкенд — [`docker-compose.yml`](docker-compose.yml).

### Быстрый старт всего стека (backend + admin + TMA)

```bash
./scripts/local-up.sh --init-data "auth_date=...&user=...&hash=..."
```

Скрипт:
- поднимает `fantasy-backend` через `docker compose` (вместе с БД и MinIO),
- запускает dev-серверы админки и TMA,
- пишет логи в `.local-dev-logs/admin.log` и `.local-dev-logs/tma.log`.

Порты и host можно переопределить:

```bash
ADMIN_PORT=5174 TMA_PORT=5175 DEV_HOST=0.0.0.0 ./scripts/local-up.sh
```
