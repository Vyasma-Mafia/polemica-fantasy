#!/usr/bin/env bash

set -euo pipefail

usage() {
  printf 'Usage: %s --workspace-id <mon...> --iam-token-file <path> [--rules <yaml>] [--alertmanager <yaml>] [--dry-run]\n' "$0"
}

WORKSPACE_ID=""
IAM_TOKEN_FILE=""
RULES_FILE=""
ALERTMANAGER_FILE=""
DRY_RUN=false

while (( $# > 0 )); do
  case "$1" in
    --workspace-id)
      WORKSPACE_ID="$2"
      shift 2
      ;;
    --iam-token-file)
      IAM_TOKEN_FILE="$2"
      shift 2
      ;;
    --rules)
      RULES_FILE="$2"
      shift 2
      ;;
    --alertmanager)
      ALERTMANAGER_FILE="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! "$WORKSPACE_ID" =~ ^mon[a-z0-9]+$ ]] || [[ -z "$IAM_TOKEN_FILE" ]]; then
  usage >&2
  exit 2
fi

if [[ -z "$RULES_FILE" && -z "$ALERTMANAGER_FILE" ]]; then
  usage >&2
  exit 2
fi

if [[ ! -f "$IAM_TOKEN_FILE" ]]; then
  printf 'IAM token file does not exist\n' >&2
  exit 1
fi

IAM_TOKEN="$(<"$IAM_TOKEN_FILE")"
if [[ -z "$IAM_TOKEN" ]]; then
  printf 'IAM token file is empty\n' >&2
  exit 1
fi

upload() {
  local kind="$1"
  local source_file="$2"
  local endpoint="$3"
  local payload_file
  local response_file
  local http_status

  if [[ ! -f "$source_file" ]]; then
    printf '%s file does not exist: %s\n' "$kind" "$source_file" >&2
    exit 1
  fi

  if grep -q '__[A-Z_]*__' "$source_file"; then
    printf '%s contains unresolved placeholders: %s\n' "$kind" "$source_file" >&2
    exit 1
  fi

  if [[ "$DRY_RUN" == true ]]; then
    printf 'dry_run kind=%s file=%s endpoint=%s\n' "$kind" "$source_file" "$endpoint"
    return
  fi

  payload_file="$(mktemp)"
  response_file="$(mktemp)"
  if [[ "$kind" == rules ]]; then
    jq -n --arg name "$(basename "$source_file")" --arg content "$(base64 < "$source_file" | tr -d '\n')" \
      '{name: $name, content: $content}' > "$payload_file"
  else
    jq -n --arg content "$(base64 < "$source_file" | tr -d '\n')" \
      '{content: $content}' > "$payload_file"
  fi

  http_status="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
    --request PUT \
    --header 'Content-Type: application/json' \
    --header "Authorization: Bearer ${IAM_TOKEN}" \
    --data "@${payload_file}" \
    "$endpoint")"

  if [[ "$http_status" != 204 ]]; then
    printf '%s upload failed with HTTP %s\n' "$kind" "$http_status" >&2
    sed -n '1,20p' "$response_file" >&2
    rm -f "$payload_file" "$response_file"
    exit 1
  fi

  rm -f "$payload_file" "$response_file"
  printf 'uploaded kind=%s status=204\n' "$kind"
}

BASE_URL="https://monitoring.api.cloud.yandex.net/prometheus/workspaces/${WORKSPACE_ID}/extensions/v1"

if [[ -n "$RULES_FILE" ]]; then
  upload rules "$RULES_FILE" "${BASE_URL}/rules"
fi

if [[ -n "$ALERTMANAGER_FILE" ]]; then
  upload alertmanager "$ALERTMANAGER_FILE" "${BASE_URL}/alertmanager"
fi
