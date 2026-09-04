#!/bin/sh
set -eu

destination="${1:-./staged-polemica-ai-agent}"
case "$destination" in
  /*) ;;
  *) destination="$(pwd)/$destination" ;;
esac

source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
umask 077
install -d -m 0700 "$destination/systemd" "$destination/deploy" "$destination/prompts"
install -m 0600 "$source_dir"/systemd/*.service "$destination/systemd/"
install -m 0600 "$source_dir"/systemd/*.timer "$destination/systemd/"
install -m 0700 "$source_dir"/preflight.sh "$destination/deploy/preflight.sh"
install -m 0700 "$source_dir"/healthcheck-fixture.sh "$destination/deploy/healthcheck-fixture.sh"
install -m 0700 "$source_dir"/negative-capability.sh "$destination/deploy/negative-capability.sh"
install -m 0600 "$source_dir"/../prompts/*.md "$destination/prompts/"
install -m 0600 "$source_dir"/OWNERSHIP.md "$destination/deploy/OWNERSHIP.md"

printf '%s\n' "Staged disabled artifacts at $destination"
printf '%s\n' "No users, secrets, system units, timers, cron entries, services, or network calls were created."
printf '%s\n' "Production activation requires a separate reviewed gate."
