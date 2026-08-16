#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_SOURCE="${ROOT_DIR}/ops/monitoring/yandex/logging/fluent-bit.yaml"
UNIT_SOURCE="${ROOT_DIR}/ops/monitoring/yandex/logging/polemica-monium-logs.service"
SECRET_FILE="/etc/polemica-fantasy/monium-logs.env"
CONFIG_TARGET="/etc/fluent-bit/polemica-fantasy.yaml"
UNIT_TARGET="/etc/systemd/system/polemica-monium-logs.service"
FLUENT_BIT_VERSION="5.1.0"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

if [[ ! -f "${SECRET_FILE}" ]]; then
  echo "Missing root-only ${SECRET_FILE}" >&2
  exit 1
fi
if [[ "$(stat -c '%a' "${SECRET_FILE}")" != "600" ]]; then
  echo "${SECRET_FILE} must have mode 600" >&2
  exit 1
fi
if ! grep -q '^MONIUM_LOGS_API_KEY=.' "${SECRET_FILE}"; then
  echo "${SECRET_FILE} must contain MONIUM_LOGS_API_KEY" >&2
  exit 1
fi

install -d -m 0755 /usr/share/keyrings
curl -fsSL https://packages.fluentbit.io/fluentbit.key \
  | gpg --dearmor --yes -o /usr/share/keyrings/fluentbit-keyring.gpg
fingerprint="$(gpg --show-keys --with-colons /usr/share/keyrings/fluentbit-keyring.gpg | awk -F: '$1 == "fpr" { print $10; exit }')"
if [[ "${fingerprint}" != "C3C0A28534B9293EAF51FABD9F9DDC083888C1CD" ]]; then
  echo "Unexpected Fluent Bit signing-key fingerprint: ${fingerprint}" >&2
  exit 1
fi

. /etc/os-release
if [[ "${ID}" != "ubuntu" || -z "${VERSION_CODENAME:-}" ]]; then
  echo "This installer supports Ubuntu with VERSION_CODENAME" >&2
  exit 1
fi
printf 'deb [signed-by=/usr/share/keyrings/fluentbit-keyring.gpg] https://packages.fluentbit.io/ubuntu/%s %s main\n' \
  "${VERSION_CODENAME}" "${VERSION_CODENAME}" \
  > /etc/apt/sources.list.d/fluent-bit.list
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get \
  -o Dpkg::Options::=--force-confold \
  install -y "fluent-bit=${FLUENT_BIT_VERSION}"

install -d -m 0755 /etc/polemica-fantasy /etc/fluent-bit
install -d -m 0750 /var/lib/fluent-bit /var/lib/fluent-bit/storage
install -d -m 0755 /var/log/polemica-fantasy

set -a
. "${SECRET_FILE}"
set +a
# Validate the source before replacing either live target. A syntax error must
# leave the currently installed config and unit untouched across future boots.
/opt/fluent-bit/bin/fluent-bit --dry-run --config="${CONFIG_SOURCE}"
unset MONIUM_LOGS_API_KEY

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
config_existed=false
unit_existed=false
service_was_active=false
service_was_enabled=false
[[ -f "${CONFIG_TARGET}" ]] && config_existed=true
[[ -f "${UNIT_TARGET}" ]] && unit_existed=true
systemctl is-active --quiet polemica-monium-logs.service && service_was_active=true
systemctl is-enabled --quiet polemica-monium-logs.service && service_was_enabled=true
for target in "${CONFIG_TARGET}" "${UNIT_TARGET}"; do
  if [[ -f "${target}" ]]; then
    cp -a "${target}" "${target}.${timestamp}.bak"
  fi
done
install -m 0644 "${CONFIG_SOURCE}" "${CONFIG_TARGET}"
install -m 0644 "${UNIT_SOURCE}" "${UNIT_TARGET}"

systemctl daemon-reload
if ! systemctl enable polemica-monium-logs.service || \
   ! systemctl restart polemica-monium-logs.service || \
   ! systemctl is-active --quiet polemica-monium-logs.service; then
  systemctl stop polemica-monium-logs.service || true
  if [[ "${config_existed}" == true ]]; then
    cp -a "${CONFIG_TARGET}.${timestamp}.bak" "${CONFIG_TARGET}"
  else
    rm -f "${CONFIG_TARGET}"
  fi
  if [[ "${unit_existed}" == true ]]; then
    cp -a "${UNIT_TARGET}.${timestamp}.bak" "${UNIT_TARGET}"
  else
    rm -f "${UNIT_TARGET}"
  fi
  systemctl daemon-reload
  if [[ "${service_was_enabled}" == true ]]; then
    systemctl enable polemica-monium-logs.service
  else
    systemctl disable polemica-monium-logs.service || true
  fi
  if [[ "${service_was_active}" == true ]]; then
    systemctl restart polemica-monium-logs.service
  fi
  echo "Failed to activate new config; previous service files were restored" >&2
  exit 1
fi
systemctl show polemica-monium-logs.service \
  --property=ActiveState \
  --property=ExecMainStartTimestamp \
  --no-pager
echo "Polemica Monium log forwarder installed and active; API key was not printed."
