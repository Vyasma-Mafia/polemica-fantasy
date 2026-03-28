/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  /** Raw initData string for local dev when not inside Telegram (must match TELEGRAM_BOT_TOKEN on backend) */
  readonly VITE_DEV_INIT_DATA: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
