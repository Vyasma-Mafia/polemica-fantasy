# Polemica Fantasy production dashboard

Data source: Managed Prometheus workspace `mon9v9q5apml8dnnchl7`.

The live Monium dashboard is `Polemica Fantasy — Production`, name
`polemica-fantasy-production`, ID `fbec6eh0cj6qn8u95p6r`. The reproducible API
payload is `polemica-fantasy-production.json`; keep the PromQL below aligned
with it. Domain panels were published only after the backend exposing these
exact names produced real MSP scrapes.

## Availability and HTTP

### Backend uptime

```promql
process_uptime_seconds{job="polemica-fantasy-backend"}
```

### Request rate by HTTP status

```promql
sum by (status) (rate(http_server_requests_seconds_count{job="polemica-fantasy-backend"}[5m]))
```

### 5xx percentage

```promql
100 * sum(rate(http_server_requests_seconds_count{job="polemica-fantasy-backend",status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{job="polemica-fantasy-backend"}[5m]))
```

## Series pipeline

### Sync executions by result and kind

```promql
sum by (result, kind) (increase(fantasy_series_sync_duration_seconds_count{job="polemica-fantasy-backend"}[1h]))
```

### Games created and refreshed by sync

```promql
sum by (change, kind) (increase(fantasy_series_sync_games_total{job="polemica-fantasy-backend"}[1h]))
```

### Scoring executions and average duration

```promql
sum by (result) (increase(fantasy_scoring_duration_seconds_count{job="polemica-fantasy-backend"}[1h]))
```

```promql
sum(rate(fantasy_scoring_duration_seconds_sum{job="polemica-fantasy-backend",result="success"}[15m]))
/
sum(rate(fantasy_scoring_duration_seconds_count{job="polemica-fantasy-backend",result="success"}[15m]))
```

### Active series

```promql
fantasy_active_series_count{job="polemica-fantasy-backend"}
```

### Scheduler completion and success age

```promql
time() - fantasy_scheduler_last_completion_timestamp_seconds{job="polemica-fantasy-backend"}
```

```promql
time() - fantasy_scheduler_last_success_timestamp_seconds{job="polemica-fantasy-backend"}
```

## Finalization and notifications

### Finalizations and effects

```promql
increase(fantasy_series_finalizations_total{job="polemica-fantasy-backend"}[24h])
```

```promql
increase(fantasy_series_finalization_rewarded_users_total{job="polemica-fantasy-backend"}[24h])
```

```promql
increase(fantasy_series_finalization_card_uses_decremented_total{job="polemica-fantasy-backend"}[24h])
```

### Notification outcomes

```promql
sum by (category, outcome) (increase(fantasy_notification_deliveries_total{job="polemica-fantasy-backend"}[1h]))
```

## Runtime and host

### JVM memory utilization

```promql
sum by (area) (jvm_memory_used_bytes{job="polemica-fantasy-backend"})
/
sum by (area) (jvm_memory_max_bytes{job="polemica-fantasy-backend"})
```

### HikariCP connections

```promql
hikaricp_connections_active{job="polemica-fantasy-backend"}
```

```promql
hikaricp_connections_pending{job="polemica-fantasy-backend"}
```

### Host load and free filesystem space

```promql
proc_LoadAverage1min{job="vyasma-mafia-linux"}
```

```promql
filesystem_FreeB{job="vyasma-mafia-linux"}
```

## Acceptance checks

- Every query executes without an MSP parser error.
- Domain series use only documented fixed labels; no IDs, usernames, URLs,
  Telegram identifiers, or exception messages appear.
- Custom domain series remain at or below 140 and the combined steady-state
  workspace footprint remains at or below 450.
- The fixed metric/tag contract has a calculated worst case of 135 domain
  series; `user_missing` is intentionally counted as notification `error`.
- Dashboard panels use the MSP workspace directly and do not depend on Grafana.
- Automatic Prometheus target step is represented by an absent `step` field;
  the literal string `"auto"` is not a valid Remote API duration.
