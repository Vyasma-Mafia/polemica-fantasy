# Polemica Fantasy — Telegram Mini App (user)

Vite + React + TypeScript. API: `Authorization: tma <initData>`.

## Локальная разработка

1. Запустите бэкенд (например `docker compose up` в корне репозитория или Spring Boot на `http://localhost:8080`).
2. В корне webapp: `npm install` и `npm run dev` — Vite проксирует `/api` на порт 8080 (см. `vite.config.ts`).
3. Вне Telegram initData недоступен. Для тестов задайте в `.env.local`:

```bash
# Сырой query-string initData, подписанный тем же TELEGRAM_BOT_TOKEN, что и у бэкенда
VITE_DEV_INIT_DATA=auth_date=...&user=...&hash=...
```

Сгенерировать свежую строку можно из корня репозитория:

```bash
./scripts/generate-tma-init-data.py
```

Скрипт берёт `TELEGRAM_BOT_TOKEN` из окружения или корневого `.env`. Для прямого запуска всего локального стека используйте `./scripts/local-up.sh --generate-init-data`.

Опционально для страницы «Справка» → блок «Поддержка»: `VITE_TELEGRAM_BOT_USERNAME` — username бота без `@` (ссылка `https://t.me/…`).

## Сборка

`npm run build` — вывод в `dist/`.
