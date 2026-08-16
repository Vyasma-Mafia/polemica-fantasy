#!/usr/bin/env bash

set -euo pipefail

SCRAPE_URL="${1:-http://127.0.0.1:18081/actuator/prometheus}"
MAX_SAMPLES="${MAX_SAMPLES:-450}"
MAX_DOMAIN_SAMPLES="${MAX_DOMAIN_SAMPLES:-140}"
EXPECTED_LINUX_SAMPLES="${EXPECTED_LINUX_SAMPLES:-33}"
SCRAPE_FILE="$(mktemp)"
trap 'rm -f "$SCRAPE_FILE"' EXIT

curl --fail --silent --show-error --max-time 15 "$SCRAPE_URL" > "$SCRAPE_FILE"

ALLOWLIST='^(application_ready_time_seconds|application_started_time_seconds|process_uptime_seconds|process_cpu_usage|system_cpu_usage|disk_free_bytes|disk_total_bytes|jvm_memory_used_bytes|jvm_memory_max_bytes|jvm_threads_live_threads|jvm_gc_pause_seconds_count|jvm_gc_pause_seconds_sum|jvm_gc_pause_seconds_max|hikaricp_connections_active|hikaricp_connections_idle|hikaricp_connections_pending|hikaricp_connections_max|hikaricp_connections_timeout_total|http_server_requests_seconds_count|logback_events_total|fantasy_series_sync_duration_seconds_(count|sum|max)|fantasy_series_sync_games_total|fantasy_series_sync_empty_total|fantasy_scoring_duration_seconds_(count|sum|max)|fantasy_scoring_games_processed_total|fantasy_series_finalizations_total|fantasy_series_finalization_rewarded_users_total|fantasy_series_finalization_card_uses_decremented_total|fantasy_series_finalization_last_success_timestamp_seconds|fantasy_notification_deliveries_total|fantasy_scheduler_duration_seconds_(count|sum|max)|fantasy_scheduler_runs_total|fantasy_scheduler_items_total|fantasy_scheduler_last_completion_timestamp_seconds|fantasy_scheduler_last_success_timestamp_seconds|fantasy_active_series_count)$'

RAW_SAMPLES="$(awk '!/^#/ && NF >= 2 { count++ } END { print count + 0 }' "$SCRAPE_FILE")"
ALLOWED_SAMPLES="$(awk -v allowlist="$ALLOWLIST" '
  !/^#/ && NF >= 2 {
    metric = $1
    sub(/\{.*/, "", metric)
    if (metric ~ allowlist) count++
  }
  END { print count + 0 }
' "$SCRAPE_FILE")"

DOMAIN_SAMPLES="$(awk '
  !/^#/ && NF >= 2 {
    metric = $1
    sub(/\{.*/, "", metric)
    if (metric ~ /^fantasy_/) count++
  }
  END { print count + 0 }
' "$SCRAPE_FILE")"

FORBIDDEN_SERIES="$(awk '
  !/^#/ && NF >= 2 {
    metric = $1
    sub(/\{.*/, "", metric)
    if (metric ~ /^(http_client_|spring_data_|spring_security_|repository_)/) count++
  }
  END { print count + 0 }
' "$SCRAPE_FILE")"

printf 'raw_samples=%s\n' "$RAW_SAMPLES"
printf 'allowed_application_samples=%s\n' "$ALLOWED_SAMPLES"
printf 'allowed_domain_samples=%s\n' "$DOMAIN_SAMPLES"
printf 'expected_linux_samples=%s\n' "$EXPECTED_LINUX_SAMPLES"
printf 'forbidden_raw_samples=%s\n' "$FORBIDDEN_SERIES"
printf 'configured_steady_state_series_limit=%s\n' "$MAX_SAMPLES"
printf 'configured_domain_series_limit=%s\n' "$MAX_DOMAIN_SAMPLES"

if (( ALLOWED_SAMPLES == 0 )); then
  printf 'error=allowlist matched no metrics\n' >&2
  exit 1
fi

if (( DOMAIN_SAMPLES > MAX_DOMAIN_SAMPLES )); then
  printf 'error=domain metrics exceed series limit\n' >&2
  exit 1
fi

if (( ALLOWED_SAMPLES + EXPECTED_LINUX_SAMPLES > MAX_SAMPLES )); then
  printf 'error=application plus expected Linux metrics exceed steady-state series limit\n' >&2
  exit 1
fi
