# Production activation runbook

This runbook deliberately separates system installation, credential creation,
read-only smoke testing, and write activation. Never paste a Bearer token or
Polemica password into a prompt, repository file, shell history, or service log.

## 1. Install the disabled system runtime

Run interactively on `codex@51.250.97.185`:

```bash
sudo /home/codex/.local/share/polemica-agent-runtime/deploy/install-system-disabled.sh \
  /home/codex/.local/share/polemica-agent-runtime
```

The installer creates `polemica-agent-broker`, the isolated
`polemica-agent-compute` worker identity and group, `/opt/polemica-ai-agent`, state
directories, empty root-owned environment files, and installed systemd units. It
migrates the local SQLite schema but does not start or enable anything. The
Compute socket directory is ephemeral and is created only when its worker unit is
explicitly started.

## 2. Prepare the visible Fantasy user

For a normal-looking profile, create a separate Telegram account, configure its
public name/avatar, and open the Fantasy TMA exactly once. Record its numeric
Telegram user ID. Do not use that account for subsequent manual play.

Issuing a credential for the existing user marks it internally as automated and
blocks later TMA authentication for that account. Alternatively, an operator may
create a synthetic user with `POST /api/v1/admin/agent-users`.

## 3. Issue the one-time Bearer credential

After the disabled system installer is present, run the root-only provisioner:

```bash
sudo /opt/polemica-ai-agent/deploy/provision_fantasy_credential.py <telegramId>
```

It prompts for Admin Basic Auth without echoing the password, calls:

```text
POST /api/v1/admin/users/{telegramId}/api-credentials
Content-Type: application/json

{"label":"codex-runtime","expiresAt":"<future ISO-8601 instant>"}
```

The response contains `token` exactly once. The provisioner atomically stores it
directly in `/etc/polemica-ai-agent/fantasy-mcp.env` as `FANTASY_BEARER_TOKEN`
without printing it; only its SHA-256 hash and short hint remain in PostgreSQL.
The credential can be listed or revoked through the matching admin endpoints,
but the full token cannot be recovered.

## 4. Configure root-owned service environments

Install and validate the Polemica Research credential interactively:

```bash
sudo /opt/polemica-ai-agent/deploy/provision_research_environment.py
```

The helper writes `research-mcp.env` and the non-writing `runner.env` atomically
without printing the password. The effective files are:

The five files under `/etc/polemica-ai-agent` remain mode `0600`, owner `root`:

```text
# fantasy-mcp.env
FANTASY_API_BASE_URL=https://fantasy.maftourbot.ru
FANTASY_BEARER_TOKEN=<one-time token>
WRITE_ENABLED=false
FANTASY_WRITE_ALLOWLIST=

# research-mcp.env
POLEMICA_API_BASE_URL=https://app.polemicagame.com
POLEMICA_PROFILE_BASE_URL=https://polemicagame.com
POLEMICA_USERNAME=<read-only account>
POLEMICA_PASSWORD=<read-only password>

# memory-mcp.env
# intentionally empty

# compute-mcp.env
# intentionally empty; the gateway receives no upstream credential

# runner.env
FANTASY_MCP_URL=http://127.0.0.1:8811/mcp
RESEARCH_MCP_URL=http://127.0.0.1:8812/mcp
COMPUTE_MCP_URL=http://127.0.0.1:8814/mcp
MEMORY_MCP_URL=http://127.0.0.1:8813/mcp
WRITE_ENABLED=false
POLEMICA_AGENT_MODEL=gpt-5.6-sol
POLEMICA_AGENT_STRATEGY_VERSION=hourly-compute-v1
```

## 5. Activate in stages

1. Start the Compute worker, then the four MCP services, and verify loopback
   health/tool registries. The Compute gateway requires its worker and connects
   only through `/run/polemica-agent-compute/worker.sock`.
2. Run one manual Codex turn with both write flags false.
3. Verify the run journal, sealed evidence, logs, and absence of secrets.
4. For the team-only canary, set `WRITE_ENABLED=true` and
   `POLEMICA_PRODUCTION_ACTIVATION_APPROVED=true` in `runner.env`. In
   `fantasy-mcp.env`, set `WRITE_ENABLED=true` and
   `FANTASY_WRITE_ALLOWLIST=fantasy_create_team,fantasy_update_team`.
5. Run a manual team-only canary and verify exact read-back.
6. Expand `FANTASY_WRITE_ALLOWLIST` explicitly for each later economy or
   marketplace stage. Unknown names fail MCP startup.
7. Only then enable `polemica-agent-run.timer`.

Do not activate Store, economy, or marketplace behavior until the earlier stage
has a clean audit trail. Revoke the Bearer credential and disable the timer for
an immediate logical stop.
