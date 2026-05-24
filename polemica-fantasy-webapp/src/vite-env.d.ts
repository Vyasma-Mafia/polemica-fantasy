/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  /** Raw initData string for local dev when not inside Telegram (must match TELEGRAM_BOT_TOKEN on backend) */
  readonly VITE_DEV_INIT_DATA: string
  /** Bot username without @ — link t.me/{username} on Help page (optional) */
  readonly VITE_TELEGRAM_BOT_USERNAME?: string
  /** Bot username without @ for Mini App direct share links. Falls back to VITE_TELEGRAM_BOT_USERNAME. */
  readonly VITE_TMA_BOT_USERNAME?: string
  /** Optional Mini App short name for direct links: t.me/{bot}/{shortName}?startapp=... */
  readonly VITE_TMA_APP_SHORT_NAME?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
