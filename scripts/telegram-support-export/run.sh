#!/usr/bin/env bash
# Выгрузка чата(ов) Telegram через popstas/telegram-download-chat (User API, не Bot API).
#
# Подготовка:
#   1) cp config.example.yml config.yml  — вписать api_id и api_hash
#   2) /opt/homebrew/bin/python3.11 -m venv .venv && .venv/bin/pip install -U pip telegram-download-chat
#   3) ./run.sh -o out/support.json CHAT   (CHAT: @username, -100…, или t.me/…)
#
# Примеры:
#   ./run.sh -o out/forum -1001234567890
#   ./run.sh -1001234567890 --subchat 42 --subchat-name topic-42
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export PATH="${ROOT}/.venv/bin:${PATH}"
CONFIG="${TELEGRAM_EXPORT_CONFIG:-${ROOT}/config.yml}"

if [[ ! -f "${CONFIG}" ]]; then
  echo "Нет ${CONFIG}" >&2
  echo "Скопируйте config.example.yml в config.yml и укажите api_id, api_hash." >&2
  exit 1
fi

if [[ ! -d "${ROOT}/.venv" ]]; then
  echo "Нет .venv. Создайте:  python3.11 -m venv .venv  &&  .venv/bin/pip install telegram-download-chat" >&2
  exit 1
fi

cd "${ROOT}"
exec telegram-download-chat --config "${CONFIG}" "$@"
