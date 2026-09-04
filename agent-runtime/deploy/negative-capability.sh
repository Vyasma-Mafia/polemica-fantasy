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
compute_worker="$root/deploy/systemd/polemica-agent-compute-worker.service"
compute_gateway="$root/deploy/systemd/polemica-agent-compute-mcp.service"
grep -q '^User=polemica-agent-compute$' "$compute_worker"
grep -q '^PrivateNetwork=true$' "$compute_worker"
grep -q '^RestrictAddressFamilies=AF_UNIX$' "$compute_worker"
grep -q '^MemoryMax=256M$' "$compute_worker"
grep -q '^MemorySwapMax=0$' "$compute_worker"
grep -q '^TasksMax=4$' "$compute_worker"
grep -q '^LimitNOFILE=64$' "$compute_worker"
grep -q '^MemoryDenyWriteExecute=true$' "$compute_worker"
if grep -q '^EnvironmentFile=' "$compute_worker" || grep -q 'POLEMICA_AGENT_DATABASE' "$compute_worker"; then
  printf '%s\n' 'isolated compute worker received an environment file or broker state' >&2
  exit 1
fi
grep -q '^User=polemica-agent-broker$' "$compute_gateway"
grep -q '^Environment=MCP_BIND_HOST=127.0.0.1 MCP_BIND_PORT=8814 ' "$compute_gateway"
grep -q '^ReadOnlyPaths=/var/lib/polemica-ai-agent/research-cache$' "$compute_gateway"
grep -q '^IPAddressDeny=any$' "$compute_gateway"
grep -q '^IPAddressAllow=localhost$' "$compute_gateway"
grep -q 'POLEMICA_COMPUTE_WORKER_SOCKET=/run/polemica-agent-compute/worker.sock' "$compute_gateway"
if grep -E -q 'FANTASY_BEARER_TOKEN|POLEMICA_(USERNAME|PASSWORD)|POLEMICA_API_BASE_URL' "$compute_gateway"; then
  printf '%s\n' 'compute gateway received an upstream credential' >&2
  exit 1
fi
grep -q -- '--ignore-user-config' "$root/src/polemica_agent/runner/codex.py"
grep -q -- 'read-only' "$root/src/polemica_agent/runner/codex.py"
grep -q -- '"shell_tool"' "$root/src/polemica_agent/runner/codex.py"
grep -q -- 'agents.enabled=false' "$root/src/polemica_agent/runner/codex.py"
grep -q -- 'web_search="disabled"' "$root/src/polemica_agent/runner/codex.py"
printf '%s\n' 'negative capability checks ok'
