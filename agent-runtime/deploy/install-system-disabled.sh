#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
  printf '%s\n' 'install-system-disabled.sh must run as root' >&2
  exit 1
fi

source_dir=${1:-}
if [ -z "$source_dir" ] || [ ! -f "$source_dir/pyproject.toml" ] || [ ! -f "$source_dir/uv.lock" ]; then
  printf '%s\n' 'usage: install-system-disabled.sh /absolute/path/to/agent-runtime' >&2
  exit 1
fi
case "$source_dir" in
  /*) ;;
  *) printf '%s\n' 'source path must be absolute' >&2; exit 1 ;;
esac

install_prefix=/opt/polemica-ai-agent
broker_state=/var/lib/polemica-ai-agent
runner_state=/var/lib/polemica-ai-agent-runner
config_dir=/etc/polemica-ai-agent
broker_user=polemica-agent-broker
uv_binary=${UV_BINARY:-/home/codex/.local/bin/uv}

test ! -L "$install_prefix"
test -x "$uv_binary"
command -v rsync >/dev/null
command -v systemctl >/dev/null
command -v runuser >/dev/null

for unit in polemica-agent-fantasy-mcp.service polemica-agent-research-mcp.service \
  polemica-agent-memory-mcp.service polemica-agent-run.timer; do
  if systemctl is-active --quiet "$unit" || systemctl is-enabled --quiet "$unit"; then
    printf '%s\n' "$unit must be inactive and disabled before installation" >&2
    exit 1
  fi
done

if ! getent group "$broker_user" >/dev/null; then
  groupadd --system "$broker_user"
fi
if ! id "$broker_user" >/dev/null 2>&1; then
  useradd --system --gid "$broker_user" --home-dir /nonexistent \
    --shell /usr/sbin/nologin "$broker_user"
fi

install -d -m 0755 -o root -g root "$install_prefix"
rsync -a --delete \
  --exclude '.venv/' --exclude '.pytest_cache/' --exclude '__pycache__/' \
  --exclude 'dist/' --exclude 'build/' --exclude 'staged-*/' \
  "$source_dir/" "$install_prefix/"

UV_PROJECT_ENVIRONMENT="$install_prefix/venv" "$uv_binary" sync \
  --project "$install_prefix" --frozen --no-dev --python /usr/bin/python3
chown -R root:root "$install_prefix"
find "$install_prefix" -type d -exec chmod 0755 {} +
chmod 0755 "$install_prefix/deploy/preflight.sh"

install -d -m 0700 -o "$broker_user" -g "$broker_user" "$broker_state"
install -d -m 0700 -o "$broker_user" -g "$broker_user" "$broker_state/blobs" "$broker_state/research-cache"
install -d -m 0700 -o codex -g codex "$runner_state" "$runner_state/workspace" "$runner_state/logs"
install -d -m 0700 -o root -g root "$config_dir"

for env_file in fantasy-mcp.env research-mcp.env memory-mcp.env runner.env; do
  if [ ! -e "$config_dir/$env_file" ]; then
    install -m 0600 -o root -g root /dev/null "$config_dir/$env_file"
  fi
done

install -m 0644 -o root -g root "$source_dir"/deploy/systemd/*.service /etc/systemd/system/
install -m 0644 -o root -g root "$source_dir"/deploy/systemd/*.timer /etc/systemd/system/
systemctl daemon-reload

runuser -u "$broker_user" -- env \
  POLEMICA_AGENT_DATABASE="$broker_state/agent.sqlite3" \
  "$install_prefix/venv/bin/polemica-agent-migrate"

for unit in polemica-agent-fantasy-mcp.service polemica-agent-research-mcp.service \
  polemica-agent-memory-mcp.service polemica-agent-run.timer; do
  if systemctl is-active --quiet "$unit" || systemctl is-enabled --quiet "$unit"; then
    printf '%s\n' "$unit unexpectedly became active or enabled" >&2
    exit 1
  fi
done

printf '%s\n' 'System runtime installed with migrated local state.'
printf '%s\n' 'All MCP services and the hourly timer remain inactive and disabled.'
printf '%s\n' 'Environment files are empty unless they existed before this run; no credentials were created.'
