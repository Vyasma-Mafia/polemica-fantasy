# FantasyMetricsMissing

This alert means MSP has not received `process_uptime_seconds` from the Fantasy
backend for at least five minutes.

1. Check the public product and the local backend health independently:

   ```bash
   curl -fsS https://fantasy.maftourbot.ru/api/v1/health
   ssh mafia@51.250.18.236 \
     'curl -fsS http://127.0.0.1:18081/actuator/health'
   ```

2. On the VM, check `systemctl status unified-agent`, recent unit logs, and
   `curl -fsS http://localhost:16241/status`.
3. Check that the backend container still exposes `127.0.0.1:18081` and that
   `/actuator/prometheus` contains `process_uptime_seconds`.
4. Check Remote Write errors and IAM authorization. The VM must still have
   `logger-robot` attached with `monitoring.editor` in the production folder.
5. If the agent configuration is invalid or cardinality exceeds 500, stop the
   agent, restore the previous config, and keep the application running.

Do not restart or modify either existing Prometheus instance or Grafana while
handling this alert.
