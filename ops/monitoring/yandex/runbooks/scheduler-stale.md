# Active-series scheduler success is stale

1. Confirm `process_uptime_seconds{job="polemica-fantasy-backend"}` is fresh.
2. Check `fantasy_active_series_count`, both scheduler timestamp gauges, and
   the latest `fantasy_scheduler_runs_total{scheduler="active_series_sync"}`
   result. Repeated `partial` or `failure` runs keep the success timestamp stale.
3. On the VM, inspect backend logs for scheduled sync/scoring failures without
   restarting the existing Prometheus or Grafana containers.
4. Run manual sync only for an identified series through the normal admin flow;
   do not finalize a series as a monitoring recovery action.
5. If the scheduler thread is stalled while the backend remains healthy,
   capture logs and restart only `fantasy-backend` using the normal deployment
   procedure.
