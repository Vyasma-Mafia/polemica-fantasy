#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.prod.yml}"
BACKUP_DIR="${DB_BACKUP_DIR:-${HOME}/polemica-fantasy-backups/postgres}"
RETENTION_DAYS="${DB_BACKUP_RETENTION_DAYS:-14}"
TIMESTAMP="$(date -u +"%Y%m%dT%H%M%SZ")"
BACKUP_FILE="${BACKUP_DIR}/fantasy-${TIMESTAMP}.dump"
TMP_FILE="${BACKUP_FILE}.tmp"

mkdir -p "${BACKUP_DIR}"

cleanup() {
  rm -f "${TMP_FILE}"
}
trap cleanup EXIT

docker compose --project-directory "${ROOT_DIR}" -f "${COMPOSE_FILE}" exec -T fantasy-db \
  sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null'

docker compose --project-directory "${ROOT_DIR}" -f "${COMPOSE_FILE}" exec -T fantasy-db \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl' \
  > "${TMP_FILE}"

mv "${TMP_FILE}" "${BACKUP_FILE}"
chmod 0600 "${BACKUP_FILE}"

find "${BACKUP_DIR}" -type f -name 'fantasy-*.dump' -mtime "+${RETENTION_DAYS}" -delete

echo "Created PostgreSQL backup: ${BACKUP_FILE}"
