#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  scripts/codex-check.sh [target...]

Targets:
  quick         Backend compile + both frontend builds (default)
  backend      Gradle compileKotlin compileTestKotlin
  backend-test Gradle test (requires Docker/Testcontainers for integration tests)
  webapp       TMA npm run build
  admin        Admin npm run build
  frontend     webapp + admin
  lint         npm run lint in both frontend apps

Examples:
  ./scripts/codex-check.sh
  ./scripts/codex-check.sh backend admin
  ./scripts/codex-check.sh backend-test
EOF
}

require_node_modules() {
  local app_dir="$1"
  if [[ ! -d "${app_dir}/node_modules" ]]; then
    echo "Missing node_modules in ${app_dir}. Run npm ci in that directory first." >&2
    exit 1
  fi
}

run_backend() {
  echo "==> Backend compile"
  (cd "${ROOT_DIR}/polemica-fantasy-backend" && ./gradlew compileKotlin compileTestKotlin)
}

run_backend_test() {
  echo "==> Backend tests"
  (cd "${ROOT_DIR}/polemica-fantasy-backend" && ./gradlew test)
}

run_webapp() {
  echo "==> TMA build"
  require_node_modules "${ROOT_DIR}/polemica-fantasy-webapp"
  (cd "${ROOT_DIR}/polemica-fantasy-webapp" && npm run build)
}

run_admin() {
  echo "==> Admin build"
  require_node_modules "${ROOT_DIR}/polemica-fantasy-admin"
  (cd "${ROOT_DIR}/polemica-fantasy-admin" && npm run build)
}

run_lint() {
  echo "==> TMA lint"
  require_node_modules "${ROOT_DIR}/polemica-fantasy-webapp"
  (cd "${ROOT_DIR}/polemica-fantasy-webapp" && npm run lint)

  echo "==> Admin lint"
  require_node_modules "${ROOT_DIR}/polemica-fantasy-admin"
  (cd "${ROOT_DIR}/polemica-fantasy-admin" && npm run lint)
}

if [[ $# -eq 0 ]]; then
  set -- quick
fi

for target in "$@"; do
  case "${target}" in
    quick)
      run_backend
      run_webapp
      run_admin
      ;;
    backend)
      run_backend
      ;;
    backend-test)
      run_backend_test
      ;;
    webapp)
      run_webapp
      ;;
    admin)
      run_admin
      ;;
    frontend)
      run_webapp
      run_admin
      ;;
    lint)
      run_lint
      ;;
    -h|--help|help)
      usage
      ;;
    *)
      echo "Unknown target: ${target}" >&2
      usage >&2
      exit 1
      ;;
  esac
done
