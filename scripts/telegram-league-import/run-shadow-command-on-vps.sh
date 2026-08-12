#!/usr/bin/env bash
set -euo pipefail

command_name="${1:-}"
shift || true
command_args=()
case "$command_name" in
  poll-once|health|inspect|notify-test)
    [[ "$#" -eq 0 ]] || { echo "Unexpected arguments for $command_name" >&2; exit 2; }
    ;;
  reclassify-message)
    [[ "$#" -eq 2 && "$1" == "--message-id" && "$2" =~ ^[1-9][0-9]*$ ]] || {
      echo "Usage: $0 reclassify-message --message-id <positive-id>" >&2
      exit 2
    }
    command_args=("$1" "$2")
    ;;
  *) echo "Usage: $0 {poll-once|health|inspect|notify-test|reclassify-message}" >&2; exit 2 ;;
esac

repo_dir="${TELEGRAM_IMPORT_REPO_DIR:-$HOME/polemica-fantasy}"
compose_file="$repo_dir/docker-compose.prod.yml"
network_name="polemica-telegram-import-vpn"
required_subnet="${TELEGRAM_IMPORT_REQUIRED_SOURCE_CIDR:-172.24.0.0/28}"
env_file="${TELEGRAM_IMPORT_ENV_FILE:-$HOME/.config/polemica-fantasy/telegram-import.env}"
session_dir="${TELEGRAM_IMPORT_SESSION_DIR:-$HOME/.local/share/polemica-fantasy/telegram-import}"

[[ -f "$compose_file" && -f "$env_file" && -d "$session_dir" ]] || { echo "Worker compose/env/session prerequisites are missing" >&2; exit 1; }
[[ "$(stat -c '%a' "$env_file")" == "600" ]] || { echo "Env file must have mode 600" >&2; exit 1; }
[[ "$(stat -c '%a' "$session_dir")" == "700" ]] || { echo "Session directory must have mode 700" >&2; exit 1; }
delivery_mode="$(sed -n 's/^TELEGRAM_IMPORT_DELIVERY_MODE=//p' "$env_file" | tail -n 1)"
delivery_mode="${delivery_mode:-DIRECT}"
[[ "$delivery_mode" == "DIRECT" || "$delivery_mode" == "BACKEND" ]] || { echo "Invalid TELEGRAM_IMPORT_DELIVERY_MODE in $env_file" >&2; exit 1; }
systemctl is-active --quiet wg-quick@wg-tg || { echo "wg-tg is not active" >&2; exit 1; }
systemctl is-active --quiet polemica-telegram-egress-guard.timer || { echo "VPN egress guard timer is not active" >&2; exit 1; }
sudo /usr/local/sbin/polemica-telegram-egress-guard apply
network_subnet="$(docker network inspect "$network_name" --format '{{range .IPAM.Config}}{{.Subnet}}{{end}}')"
[[ "$network_subnet" == "$required_subnet" ]] || { echo "Unexpected worker network subnet: $network_subnet" >&2; exit 1; }
network_id="$(docker network inspect "$network_name" --format '{{.Id}}')"
bridge="br-${network_id:0:12}"
for destination_ip in 149.154.167.51 91.108.56.130 1.1.1.1; do
  route="$(ip route get "$destination_ip" from 172.24.0.2 iif "$bridge")"
  [[ "$route" == *"dev wg-tg table 201"* ]] || { echo "Route probe $destination_ip is not through wg-tg/table 201" >&2; exit 1; }
done

export TELEGRAM_IMPORT_ENV_FILE="$env_file"
export TELEGRAM_IMPORT_SESSION_DIR="$session_dir"
export TELEGRAM_IMPORT_REQUIRED_SOURCE_CIDR="$required_subnet"
export TELEGRAM_IMPORT_UID="$(id -u)"
export TELEGRAM_IMPORT_GID="$(id -g)"
export TELEGRAM_IMPORT_DELIVERY_MODE="$delivery_mode"

docker compose -f "$compose_file" --profile telegram-import run --rm --build --no-deps telegram-import-worker "$command_name" "${command_args[@]}"
