#!/usr/bin/env bash
set -euo pipefail

repo_dir="${TELEGRAM_IMPORT_REPO_DIR:-$HOME/polemica-fantasy}"
compose_file="$repo_dir/docker-compose.prod.yml"
network_name="polemica-telegram-import-vpn"
required_subnet="${TELEGRAM_IMPORT_REQUIRED_SOURCE_CIDR:-172.24.0.0/28}"
route_probe_source="${TELEGRAM_IMPORT_ROUTE_PROBE_SOURCE:-172.24.0.2}"
env_file="${TELEGRAM_IMPORT_ENV_FILE:-$HOME/.config/polemica-fantasy/telegram-import.env}"
session_dir="${TELEGRAM_IMPORT_SESSION_DIR:-$HOME/.local/share/polemica-fantasy/telegram-import}"

if [[ ! -t 0 || ! -t 1 ]]; then
  echo "Interactive TTY required. Connect with ssh -t before running this script." >&2
  exit 1
fi
if [[ ! -f "$compose_file" ]]; then
  echo "Compose file not found: $compose_file" >&2
  exit 1
fi
if [[ ! -f "$env_file" ]]; then
  echo "Telegram import env file not found: $env_file" >&2
  exit 1
fi
if [[ ! -d "$session_dir" ]]; then
  echo "Telegram import session directory not found: $session_dir" >&2
  exit 1
fi
if [[ "$(stat -c '%a' "$env_file")" != "600" ]]; then
  echo "Telegram import env file must have mode 600: $env_file" >&2
  exit 1
fi
if [[ "$(stat -c '%a' "$session_dir")" != "700" ]]; then
  echo "Telegram import session directory must have mode 700: $session_dir" >&2
  exit 1
fi
if ! systemctl is-active --quiet wg-quick@wg-tg; then
  echo "Refusing Telegram login: wg-quick@wg-tg is not active" >&2
  exit 1
fi
if ! systemctl is-active --quiet polemica-telegram-egress-guard.timer; then
  echo "Refusing Telegram login: polemica-telegram-egress-guard.timer is not active" >&2
  echo "Install the fail-closed guard described in scripts/telegram-league-import/README.md" >&2
  exit 1
fi

sudo /usr/local/sbin/polemica-telegram-egress-guard apply

network_subnet="$(docker network inspect "$network_name" --format '{{range .IPAM.Config}}{{.Subnet}}{{end}}')"
if [[ "$network_subnet" != "$required_subnet" ]]; then
  echo "Refusing Telegram login: Docker network $network_name uses $network_subnet, expected $required_subnet" >&2
  exit 1
fi

network_id="$(docker network inspect "$network_name" --format '{{.Id}}')"
bridge="br-${network_id:0:12}"

for destination_ip in 149.154.167.51 91.108.56.130 1.1.1.1; do
  route="$(ip route get "$destination_ip" from "$route_probe_source" iif "$bridge")"
  if [[ "$route" != *"dev wg-tg table 201"* ]]; then
    echo "Refusing Telegram login: $destination_ip is not routed through wg-tg/table 201" >&2
    echo "$route" >&2
    exit 1
  fi
done

export TELEGRAM_IMPORT_ENV_FILE="$env_file"
export TELEGRAM_IMPORT_SESSION_DIR="$session_dir"
export TELEGRAM_IMPORT_REQUIRED_SOURCE_CIDR="$required_subnet"
export TELEGRAM_IMPORT_UID="$(id -u)"
export TELEGRAM_IMPORT_GID="$(id -g)"

before_transfer="unavailable (no passwordless sudo)"
if sudo -n true 2>/dev/null; then
  before_transfer="$(sudo -n wg show wg-tg transfer)"
fi
set +e
docker compose -f "$compose_file" --profile telegram-import run \
  --rm --build telegram-import-auth auth --hold-seconds 5
status=$?
set -e
after_transfer="unavailable (no passwordless sudo)"
if sudo -n true 2>/dev/null; then
  after_transfer="$(sudo -n wg show wg-tg transfer)"
fi

echo "wg-tg transfer before: $before_transfer"
echo "wg-tg transfer after:  $after_transfer"
exit "$status"
