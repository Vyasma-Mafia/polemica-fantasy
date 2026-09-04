#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -x "$root/venv/bin/python" ]; then
  runtime_python="$root/venv/bin/python"
elif [ -x "$root/.venv/bin/python" ]; then
  runtime_python="$root/.venv/bin/python"
else
  printf '%s\n' 'fixture runtime Python is unavailable' >&2
  exit 1
fi
PYTHONPATH="$root/src" "$runtime_python" - <<'PY'
from polemica_agent.mcp_runtime.registry import MCP_SERVERS
from polemica_agent.runner.settings import RuntimeSettings
assert set(MCP_SERVERS) == {"fantasy", "research", "compute", "memory"}
assert all(MCP_SERVERS.values())
print("fixture registry health ok")
PY
