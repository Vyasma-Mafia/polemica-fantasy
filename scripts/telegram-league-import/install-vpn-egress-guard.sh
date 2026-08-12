#!/usr/bin/env bash
set -euo pipefail

if [[ "$EUID" -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
install -m 755 "$script_dir/polemica-telegram-egress-guard" \
  /usr/local/sbin/polemica-telegram-egress-guard
install -m 644 "$script_dir/polemica-telegram-egress-guard.service" \
  /etc/systemd/system/polemica-telegram-egress-guard.service
install -m 644 "$script_dir/polemica-telegram-egress-guard.timer" \
  /etc/systemd/system/polemica-telegram-egress-guard.timer

systemctl daemon-reload
systemctl start polemica-telegram-egress-guard.service
systemctl enable polemica-telegram-egress-guard.service
systemctl enable --now polemica-telegram-egress-guard.timer
/usr/local/sbin/polemica-telegram-egress-guard check

if ! docker network inspect polemica-telegram-import-vpn >/dev/null 2>&1; then
  docker network create --driver bridge --subnet 172.24.0.0/28 \
    polemica-telegram-import-vpn >/dev/null
fi
network_subnet="$(docker network inspect polemica-telegram-import-vpn \
  --format '{{range .IPAM.Config}}{{.Subnet}}{{end}}')"
if [[ "$network_subnet" != "172.24.0.0/28" ]]; then
  echo "Unexpected polemica-telegram-import-vpn subnet: $network_subnet" >&2
  exit 1
fi

echo "Installed fail-closed Telegram egress guard."
