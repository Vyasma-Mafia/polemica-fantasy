# Backend logs in Monium

Production backend logs are written as structured Logstash JSON to
`/var/log/polemica-fantasy/backend.json`. Dedicated Fluent Bit 5.1.0 tails only this
file and sends OTLP/HTTP records directly to Monium:

- project: `folder__b1gdldjru7jl4ljql7ij`;
- cluster: `production`;
- service: `polemica-fantasy-backend`;
- endpoint: `https://ingest.monium.yandex.cloud/otlp/v1/logs`.

The API key is intentionally absent from Git. It lives at
`/etc/polemica-fantasy/monium-logs.env`, is readable only by root, has only the
`yc.monium.logs.write` scope, and belongs to service account `logger-robot`.
That account additionally needs folder role `monium.logs.writer`.

## Install or update the forwarder

The VM must already contain the root-only environment file with exactly one
entry:

```text
MONIUM_LOGS_API_KEY=<secret>
```

Then copy this repository to the VM and run:

```bash
sudo ./scripts/monitoring/install-monium-logs.sh
```

The script installs the pinned, signed upstream Fluent Bit `5.1.0` package on Ubuntu, verifies
the YAML before activation, backs up an existing Polemica configuration, and
enables the dedicated `polemica-monium-logs.service`. It does not create or
print the API key. The package's generic `fluent-bit.service` and its existing
configuration are left disabled and unchanged.

The production Compose file binds `/var/log/polemica-fantasy` into the backend
and applies Spring Boot rotation: 10 MB per file, 100 MB total and seven days of
history. Docker console logging keeps its independent existing 10 MB x 3 cap.
The forwarder's filesystem queue is capped at 100 MB. If Monium remains
unavailable after that queue fills, Fluent Bit evicts the oldest queued chunks;
bounded loss is preferred to exhausting the shared VM disk.

For an out-of-band rollout before these repository changes reach `master`, use
the tracked `docker-compose.monium-logs.yml` as a second Compose file. Once the
base production Compose file includes the same settings, the override is no
longer required.

## Verification

```bash
sudo systemctl is-active polemica-monium-logs
sudo journalctl -u polemica-monium-logs --since '10 minutes ago' --no-pager
sudo test -s /var/log/polemica-fantasy/backend.json
sudo tail -n 1 /var/log/polemica-fantasy/backend.json | jq -e . >/dev/null
curl -fsS http://127.0.0.1:18081/actuator/health
```

In Monium Logs, select the production project/cluster/service or query:

```text
{project="folder__b1gdldjru7jl4ljql7ij", cluster="production", service="polemica-fantasy-backend"}
```

A rollout is not complete until a fresh non-personal backend startup record is
visible through that query and the forwarder journal contains no `401`, `403`,
TLS or retry errors. An active systemd process alone is not an acceptance gate.

Never add Telegram IDs, internal user IDs, nicknames, message bodies, tokens,
passwords, or raw URLs to application log messages.
The external Polemica library is held at `WARN`: its INFO auth-refresh message
contains the configured username and must never be forwarded.

## Rollback

1. Stop and disable `polemica-monium-logs.service`.
2. Remove only `/etc/fluent-bit/polemica-fantasy.yaml` and the dedicated unit.
3. Recreate `fantasy-backend` after removing the structured-file environment
   and bind mount from Compose if local JSON logging must also be disabled.
4. Revoke the dedicated API key and remove only the `monium.logs.writer` role.

Unified Agent, Managed Prometheus, rules, alerts and Grafana are independent of
this path and must not be stopped during logging rollout or rollback.
