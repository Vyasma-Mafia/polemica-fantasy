#!/bin/sh
set -eu

codex_binary=${CODEX_BINARY:-codex}
command -v "$codex_binary" >/dev/null
"$codex_binary" exec --help 2>&1 | grep -q -- '--ignore-user-config'
"$codex_binary" exec --help 2>&1 | grep -q -- '--sandbox'

if [ "${1:-}" = "--runtime" ]; then
  command -v timedatectl >/dev/null
  test "$(timedatectl show --property=NTPSynchronized --value)" = "yes"
  test "${WRITE_ENABLED:-false}" = "false" || test "${POLEMICA_PRODUCTION_ACTIVATION_APPROVED:-false}" = "true"
  test -n "${FANTASY_MCP_URL:-}"
  test -n "${RESEARCH_MCP_URL:-}"
  test -n "${MEMORY_MCP_URL:-}"
  case "${POLEMICA_AGENT_RUN_LOCK:-}" in /*) ;; *) exit 1 ;; esac
fi

printf '%s\n' "preflight ok (read-only; secret values were not inspected or printed)"
