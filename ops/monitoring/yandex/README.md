# Polemica Fantasy observability in Yandex Cloud

This directory contains the production configuration for Yandex Managed Service
for Prometheus (MSP). It deliberately does not manage Grafana or the two existing
Prometheus instances on the VM.

It also contains the independent direct Monium Logs pipeline under `logging/`.
Application logs do not pass through Cloud Logging, Grafana, or Unified Agent.

## Architecture

```text
Spring Actuator (127.0.0.1:18081)
              |
              | pull every 60s, strict metric allowlist
              v
Yandex Unified Agent on vyasma-mafia
              |
              | Remote Write, VM metadata IAM, bounded fs buffer
              v
MSP workspace polemica-fantasy-prod
              |
              +-- PromQL rules
              +-- Yandex Monitoring notification channels

Spring structured file (/var/log/polemica-fantasy/backend.json)
              |
              | tail, bounded filesystem queue
              v
Dedicated Fluent Bit 5.1.0 systemd service
              |
              | OTLP/HTTP, scoped API key
              v
Monium Logs project/cluster/service
```

The management endpoint must remain loopback-only. Unified Agent status is also
bound to `localhost:16241`. No static IAM key is stored on the VM or in Git.

## Current production state

As of 2026-08-05, Unified Agent `26.07.11` is enabled on `vyasma-mafia` and
writes to the dedicated MSP workspace through the VM metadata service account.
The accepted steady-state footprint is 262 application series plus 33 Linux
series. The production rule file contains three healthy rules for missing
metrics, backend 5xx rate, and HikariCP saturation. A synthetic critical alert
was observed as `FIRING`, then `OK`; Monium reported successful sends for both
the email channel and the existing Cloud Push channel before the synthetic file
was removed from MSP.

The domain slice is also live: the backend exports the exact `fantasy_*`
families documented in this directory, the production scrape currently has 36
domain / 127 allowed application / 33 Linux series, and the fixed tag contract
has a calculated worst case of 135 domain series. Three domain rules are `OK`.
Dashboard `polemica-fantasy-production` (ID `fbec6eh0cj6qn8u95p6r`) contains
eight Managed Prometheus widgets and is defined reproducibly in
`dashboard/polemica-fantasy-production.json`.

Direct backend logs are live as of 2026-08-06 in project
`folder__b1gdldjru7jl4ljql7ij`, cluster `production`, service
`polemica-fantasy-backend`. The dedicated forwarder and detailed rollout,
security and rollback instructions are in `logging/README.md`.

For `multiSourceChart`, automatic Prometheus step means that the target-level
`step` field is omitted. Do not serialize it as `"auto"`: DashboardService
accepts that string, but Monium's Prometheus Remote API rejects it at query
time. The chart-level `prometheusDataSource.step` remains the grid step in
milliseconds.

Telegram is intentionally not configured yet: the recipient account did not
offer Telegram as a notification method in Monium. Activate the Yandex Cloud
notification bot for the account, create the channel, and only then add its
exact channel name to `critical-receiver`.

## Production resources

- Cloud: `cloud-mralex18102003` (`b1gdmi8ihd8ftojh91oj`)
- Folder: `default` (`b1gdldjru7jl4ljql7ij`)
- VM: `vyasma-mafia` (`epdimu5cqj3nid5v3bmu`)
- VM service account: `logger-robot` (`ajegd619533o2q4sgq35`)
- MSP workspace: `mon9v9q5apml8dnnchl7` (created for Polemica Fantasy on
  2026-08-05; the current UI identifies workspaces by ID)

The VM supports one attached service account. Do not replace `logger-robot` with
`metric-robot`: logging and other metadata-token consumers may depend on the
current attachment. The required additional folder role is `monitoring.editor`.

## Cardinality gate

Never send the raw Actuator scrape. The production scrape observed on
2026-08-05 had 2,351 samples, including unbounded concrete match IDs in
`http_client_requests_*`. The initial allowlist in
`unified-agent/polemica-fantasy.yml.template` keeps approximately 262
application samples. Linux collection is separately restricted to approximately
33 host-level series, for an expected total near 295. Network metrics are
excluded because the shared VM has many Docker and WireGuard interfaces; their
per-interface labels would consume most of the 500-series budget.

Before every allowlist change:

```bash
./scripts/monitoring/validate-scrape.sh \
  http://127.0.0.1:18081/actuator/prometheus
```

Hard limits:

- at most 140 custom `fantasy_*` series;
- the current fixed label space has a strict worst case of 135 domain series;
- at most 450 steady-state active series after application and Linux metrics,
  preserving operational headroom below the 500-series emergency stop;
- scrape interval is 60 seconds;
- no IDs, nicknames, raw URLs, query strings, Telegram identifiers, exception
  messages, or bot tokens in labels;
- `http_client_*`, `spring_data_*`, repository and security metrics are excluded;
- no `fantasy_*` wildcard: domain metrics are exported by exact family name even
  after the facade and tag tests exist.

## Deployment order

1. Use the dedicated MSP workspace `mon9v9q5apml8dnnchl7` in the production
   folder.
2. Verify VM metadata can mint a token without printing it, then add
   `monitoring.editor` to the already attached `logger-robot` service account.
3. Deploy the versioned Unified Agent configuration. Its Remote Write endpoint
   is pinned to the dedicated workspace ID.
4. Run `sudo ./scripts/monitoring/install-unified-agent.sh` on the VM. The
   script downloads pinned Unified Agent `26.07.11`, verifies SHA-256
   `4ac7427a...5338a2dd`, backs up previous files, installs the unit disabled,
   and runs `check-config`. Re-run with `--activate` only after IAM is ready.
5. Verify `localhost:16241` is local-only, Remote Write has no errors, and two or
   more scrapes reach MSP.
6. Run these PromQL checks:

```promql
process_uptime_seconds{job="polemica-fantasy-backend"}
time() - timestamp(process_uptime_seconds{job="polemica-fantasy-backend"})
count({job="polemica-fantasy-backend"})
count({job=~"polemica-fantasy-backend|vyasma-mafia-linux|vyasma-mafia-agent"})
```

Freshness must be below 120 seconds and total series must not exceed 500. Stop
the agent and narrow the allowlist if the limit is exceeded. Unified Agent's
native `agent_metrics` input is not sent to MSP: version `26.07.11` exposes it
in Yandex Monitoring's internal format and rejects it at the MSP `metrics`
output because the Prometheus project metadata is absent. Agent health is
therefore covered by `FantasyMetricsMissing`, systemd state, the localhost
status endpoint, and Remote Write service metrics.

7. Upload `rules/availability.yml`, `rules/domain.yml`, and `alertmanager.yml`. The current safe
   routing uses the existing Cloud Push channel plus the dedicated email
   channel; add Telegram only after the recipient activates it in Yandex Cloud.
8. Test notifications by uploading `rules/synthetic-test.yml`. After it reaches
   `FIRING` and the notification arrives, replace the same remote file with
   `expr: vector(0) == 1`, wait for `OK` and the resolved notification, then
   delete the remote rule file. Keep the repository fixture firing for the next
   acceptance test. Do not stop the production agent to test alert delivery.

## Rollback

1. Stop and disable Unified Agent.
2. Restore the backed-up configuration or leave the package disabled.
3. Remove only the newly added `monitoring.editor` binding from
   `logger-robot`.
4. Delete the synthetic rule and disable the new production rules.
5. Preserve the MSP workspace and data for diagnosis; workspace deletion is a
   separate destructive action.

Do not stop, reconfigure, or delete the existing Prometheus/Grafana containers
during rollout or rollback.

Build the backend JAR locally and create a lightweight runtime-image overlay on
the VM. Do not run the multi-stage Gradle Docker build on this shared 8 GB VM:
the first domain-metrics rollout demonstrated that it can starve SSH and HTTPS.

## Official references

- [Unified Agent for MSP](https://yandex.cloud/en/docs/monitoring/operations/prometheus/ingestion/prometheus-agent)
- [Unified Agent filters](https://yandex.cloud/en/docs/monitoring/concepts/data-collection/unified-agent/filters)
- [MSP alerting rules](https://yandex.cloud/en/docs/monitoring/operations/prometheus/alerting-rules)
- [MSP quotas and limitations](https://yandex.cloud/en/docs/monitoring/operations/prometheus/)
