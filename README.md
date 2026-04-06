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
