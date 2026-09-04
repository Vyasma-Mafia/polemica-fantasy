#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if find "$root" -type f \( -name '*.env' -o -name 'auth.json' \) | grep -q .; then
  printf '%s\n' 'forbidden secret-shaped file found' >&2
  exit 1
fi
if grep -R -E 'server\.tool\([^)]*(shell|http|request|sql|filesystem)' "$root/src/polemica_agent/mcp_runtime"; then
  printf '%s\n' 'generic capability found' >&2
  exit 1
fi
grep -R -q 'MCP_BIND_HOST=127.0.0.1' "$root/deploy/systemd"
grep -q -- '--ignore-user-config' "$root/src/polemica_agent/runner/codex.py"
grep -q -- 'read-only' "$root/src/polemica_agent/runner/codex.py"
grep -q -- '"shell_tool"' "$root/src/polemica_agent/runner/codex.py"
grep -q -- 'agents.enabled=false' "$root/src/polemica_agent/runner/codex.py"
grep -q -- 'web_search="disabled"' "$root/src/polemica_agent/runner/codex.py"
printf '%s\n' 'negative capability checks ok'
