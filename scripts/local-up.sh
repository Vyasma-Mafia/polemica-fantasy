#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/.local-dev-logs"

HOST="${DEV_HOST:-0.0.0.0}"
ADMIN_PORT="${ADMIN_PORT:-5174}"
TMA_PORT="${TMA_PORT:-5175}"
TMA_INIT_DATA="${VITE_DEV_INIT_DATA:-}"

ADMIN_PID=""
TMA_PID=""

usage() {
  cat <<'EOF'
Usage:
  scripts/local-up.sh [options]

Options:
  --init-data "<telegram initData>"  Init data for TMA dev auth
  --host "<host>"                    Dev server host (default: 0.0.0.0)
  --admin-port <port>                Admin dev server port (default: 5174)
  --tma-port <port>                  TMA dev server port (default: 5175)
  -h, --help                         Show this help

Environment alternatives:
  VITE_DEV_INIT_DATA, DEV_HOST, ADMIN_PORT, TMA_PORT
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --init-data)
      TMA_INIT_DATA="${2:-}"
      shift 2
      ;;
    --host)
      HOST="${2:-}"
      shift 2
      ;;
    --admin-port)
      ADMIN_PORT="${2:-}"
      shift 2
      ;;
    --tma-port)
      TMA_PORT="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

for cmd in docker npm curl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Required command not found: $cmd" >&2
    exit 1
  fi
done

if [[ -z "${TMA_INIT_DATA}" ]]; then
  echo "Warning: VITE_DEV_INIT_DATA is empty. TMA may fail authentication in local mode." >&2
fi

mkdir -p "${LOG_DIR}"

ensure_node_modules() {
  local app_dir="$1"
  if [[ ! -d "${app_dir}/node_modules" ]]; then
    echo "Installing dependencies in ${app_dir}..."
    (cd "${app_dir}" && npm ci)
  fi
}

cleanup() {
  echo
  echo "Stopping local frontend dev servers..."
  if [[ -n "${ADMIN_PID}" ]] && kill -0 "${ADMIN_PID}" >/dev/null 2>&1; then
    kill "${ADMIN_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TMA_PID}" ]] && kill -0 "${TMA_PID}" >/dev/null 2>&1; then
    kill "${TMA_PID}" >/dev/null 2>&1 || true
  fi
  echo "Backend containers are still running (docker compose)."
}

trap cleanup EXIT INT TERM

echo "Starting backend stack via docker compose..."
(cd "${ROOT_DIR}" && docker compose up -d fantasy-backend)

echo "Waiting for backend health on http://localhost:8081/actuator/health ..."
for _ in {1..30}; do
  if curl -fsS "http://localhost:8081/actuator/health" >/dev/null 2>&1; then
    echo "Backend is healthy."
    break
  fi
  sleep 1
done

ensure_node_modules "${ROOT_DIR}/polemica-fantasy-admin"
ensure_node_modules "${ROOT_DIR}/polemica-fantasy-webapp"

echo "Starting admin dev server on ${HOST}:${ADMIN_PORT} ..."
(
  cd "${ROOT_DIR}/polemica-fantasy-admin" && \
  npm run dev -- --host "${HOST}" --port "${ADMIN_PORT}"
) >"${LOG_DIR}/admin.log" 2>&1 &
ADMIN_PID="$!"

echo "Starting TMA dev server on ${HOST}:${TMA_PORT} ..."
(
  cd "${ROOT_DIR}/polemica-fantasy-webapp" && \
  VITE_DEV_INIT_DATA="${TMA_INIT_DATA}" \
  npm run dev -- --host "${HOST}" --port "${TMA_PORT}"
) >"${LOG_DIR}/tma.log" 2>&1 &
TMA_PID="$!"

echo
echo "Local stack is up:"
echo "  Backend API:  http://localhost:8080"
echo "  Backend health: http://localhost:8081/actuator/health"
echo "  Admin UI:     http://localhost:${ADMIN_PORT}"
echo "  TMA UI:       http://localhost:${TMA_PORT}"
echo
echo "Logs:"
echo "  ${LOG_DIR}/admin.log"
echo "  ${LOG_DIR}/tma.log"
echo
echo "Press Ctrl+C to stop frontend dev servers."

wait "${ADMIN_PID}" "${TMA_PID}"
