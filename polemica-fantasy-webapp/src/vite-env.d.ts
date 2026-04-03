/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  /** Raw initData string for local dev when not inside Telegram (must match TELEGRAM_BOT_TOKEN on backend) */
  readonly VITE_DEV_INIT_DATA: string
  /** Bot username without @ — link t.me/{username} on Help page (optional) */
  readonly VITE_TELEGRAM_BOT_USERNAME?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
