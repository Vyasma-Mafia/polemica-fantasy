#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  prod-db-readonly.sh --sql "select now();"
  prod-db-readonly.sh --file /private/tmp/query.sql

Environment overrides:
  POLEMICA_PROD_SSH_TARGET          default: mafia@51.250.18.236
  POLEMICA_PROD_SSH_KEY             default: ~/personal/mafia/id_rsa
  POLEMICA_PROD_REPO                default: ~/polemica-fantasy
  POLEMICA_PROD_STATEMENT_TIMEOUT   default: 30s
  POLEMICA_PROD_LOCK_TIMEOUT        default: 2s
USAGE
}

sql=""

if [[ $# -eq 0 ]]; then
  usage >&2
  exit 2
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --sql)
      [[ $# -ge 2 ]] || { echo "--sql requires an argument" >&2; exit 2; }
      sql="$2"
      shift 2
      ;;
    --file)
      [[ $# -ge 2 ]] || { echo "--file requires an argument" >&2; exit 2; }
      [[ -r "$2" ]] || { echo "SQL file is not readable: $2" >&2; exit 2; }
      sql="$(<"$2")"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${sql//[[:space:]]/}" ]]; then
  echo "SQL is empty" >&2
  exit 2
fi

if grep -Eq '^[[:space:]]*\\' <<<"$sql"; then
  echo "Refusing psql meta commands in production readonly helper" >&2
  exit 2
fi

blocked_regex='(^|[[:space:];])(insert|update|delete|merge|create|alter|drop|truncate|grant|revoke|vacuum|analyze|reindex|cluster|copy|call|do|execute|prepare|deallocate|listen|notify|begin|commit|rollback|savepoint|lock)([[:space:];(]|$)'
if grep -Eiq "$blocked_regex" <<<"$sql"; then
  echo "Refusing SQL that appears to contain a write, maintenance, transaction, or unsafe command" >&2
  exit 2
fi

if grep -Eiq '(^|[[:space:];])explain[[:space:]]*\([^)]*analyze|(^|[[:space:];])explain[[:space:]]+analyze' <<<"$sql"; then
  echo "Refusing EXPLAIN ANALYZE on production" >&2
  exit 2
fi

ssh_target="${POLEMICA_PROD_SSH_TARGET:-mafia@51.250.18.236}"
ssh_key="${POLEMICA_PROD_SSH_KEY:-$HOME/personal/mafia/id_rsa}"
remote_repo="${POLEMICA_PROD_REPO:-~/polemica-fantasy}"
statement_timeout="${POLEMICA_PROD_STATEMENT_TIMEOUT:-30s}"
lock_timeout="${POLEMICA_PROD_LOCK_TIMEOUT:-2s}"

if [[ ! -r "$ssh_key" ]]; then
  echo "SSH key is not readable: $ssh_key" >&2
  exit 2
fi

wrapped_sql=$(
  printf "BEGIN TRANSACTION READ ONLY;\n"
  printf "SET LOCAL statement_timeout = '%s';\n" "$statement_timeout"
  printf "SET LOCAL lock_timeout = '%s';\n" "$lock_timeout"
  printf "%s\n" "$sql"
  printf "ROLLBACK;\n"
)

remote_command=$(
  printf "cd %s && " "$remote_repo"
  printf "docker compose -f docker-compose.prod.yml exec -T fantasy-db sh -lc "
  printf "%q" 'psql -X -v ON_ERROR_STOP=1 -P pager=off -U "${POSTGRES_USER:-fantasy}" -d "${POSTGRES_DB:-fantasy}"'
)

printf "%s" "$wrapped_sql" | ssh -i "$ssh_key" "$ssh_target" "$remote_command"
