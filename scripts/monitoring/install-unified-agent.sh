#!/usr/bin/env bash

set -euo pipefail

AGENT_VERSION="26.07.11"
AGENT_SHA256="4ac7427a1e6a158867320736f3c30d35297537ca6af66e80448b45eb5338a2dd"
AGENT_URL="https://storage.yandexcloud.net/yc-unified-agent/releases/${AGENT_VERSION}/unified_agent"
EXPECTED_HOSTNAME="vyasma-mafia"
ACTIVATE=false

if [[ "${1:-}" == "--activate" ]]; then
  ACTIVATE=true
elif (( $# > 0 )); then
  printf 'Usage: %s [--activate]\n' "$0" >&2
  exit 2
fi

if (( EUID != 0 )); then
  printf 'Run this script as root on %s\n' "$EXPECTED_HOSTNAME" >&2
  exit 1
fi

if [[ "$(hostname -s)" != "$EXPECTED_HOSTNAME" ]]; then
  printf 'Refusing to install on unexpected host: %s\n' "$(hostname -s)" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SOURCE_CONFIG="${REPO_ROOT}/ops/monitoring/yandex/unified-agent/polemica-fantasy.yml.template"
INSTALL_DIR="/etc/yc/unified_agent"
INSTALL_CONFIG="${INSTALL_DIR}/config.yml"
BUFFER_DIR="/var/lib/yc/unified_agent/polemica_prometheus_buffer"
UNIT_FILE="/etc/systemd/system/unified-agent.service"
AGENT_BINARY="/usr/local/bin/unified_agent"
DOWNLOAD_FILE="$(mktemp)"
BACKUP_ROOT="/var/backups/polemica-unified-agent"
BACKUP_DIR="${BACKUP_ROOT}/$(date -u +%Y%m%dT%H%M%SZ)"

trap 'rm -f "$DOWNLOAD_FILE"' EXIT

if [[ ! -f "$SOURCE_CONFIG" ]]; then
  printf 'Missing versioned config: %s\n' "$SOURCE_CONFIG" >&2
  exit 1
fi

if grep -q '__[A-Z_]*__' "$SOURCE_CONFIG"; then
  printf 'Unified Agent config contains unresolved placeholders\n' >&2
  exit 1
fi

if systemctl is-active --quiet unified-agent.service; then
  printf 'Refusing to replace an active Unified Agent; stop it explicitly first\n' >&2
  exit 1
fi

curl --fail --location --silent --show-error --output "$DOWNLOAD_FILE" "$AGENT_URL"
printf '%s  %s\n' "$AGENT_SHA256" "$DOWNLOAD_FILE" | sha256sum --check --status

mkdir -p "$BACKUP_DIR"
if [[ -d "$INSTALL_DIR" ]]; then
  cp -a "$INSTALL_DIR" "${BACKUP_DIR}/etc-unified-agent"
fi
if [[ -f "$UNIT_FILE" ]]; then
  cp -a "$UNIT_FILE" "${BACKUP_DIR}/unified-agent.service"
fi
if [[ -f "$AGENT_BINARY" ]]; then
  cp -a "$AGENT_BINARY" "${BACKUP_DIR}/unified_agent"
fi

install -D -m 0755 "$DOWNLOAD_FILE" "$AGENT_BINARY"
install -d -m 0755 "$INSTALL_DIR" "$BUFFER_DIR"
install -m 0644 "$SOURCE_CONFIG" "$INSTALL_CONFIG"

install -D -m 0644 /dev/stdin "$UNIT_FILE" <<'UNIT'
[Unit]
Description=Yandex Unified Agent for Polemica Fantasy
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/local/bin/unified_agent --config /etc/yc/unified_agent/config.yml
Restart=on-failure
RestartSec=5s
MemoryMax=500M
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=full
ReadWritePaths=/var/lib/yc/unified_agent

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
"$AGENT_BINARY" --config "$INSTALL_CONFIG" check-config >/dev/null

printf 'installed_version=%s\n' "$AGENT_VERSION"
printf 'config=%s\n' "$INSTALL_CONFIG"
printf 'backup=%s\n' "$BACKUP_DIR"

if [[ "$ACTIVATE" == true ]]; then
  systemctl enable --now unified-agent.service
  systemctl is-active --quiet unified-agent.service
  systemctl is-enabled --quiet unified-agent.service
  printf 'service=active-enabled\n'
else
  printf 'service=installed-disabled\n'
fi
