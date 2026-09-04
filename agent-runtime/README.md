# Polemica AI agent runtime

This directory contains the transport-neutral runtime foundation for the hidden
Polemica Fantasy Codex user. It is Python 3.10 compatible and uses the official
Python MCP SDK through the pinned dependency set in `uv.lock`.

It exposes three independent, loopback-only MCP servers:

- `fantasy`: a fixed typed projection of the ordinary Fantasy user API;
- `research`: read-only Polemica collection and derived statistics;
- `memory`: sealed evidence, decisions, operation intents, outcomes, and strategy notes.

The Codex process receives only these allowlisted tools. It does not receive the
Fantasy Bearer token, Polemica credentials, generic HTTP, shell, filesystem, SQL,
or admin API capabilities.

## Guarantees

- Canonical JSON and SHA-256 hashes for reproducible audit records.
- Recursive secret redaction before data is stored.
- SQLite WAL with `synchronous=FULL`, explicit immediate transactions, and
  content-addressed, fsynced payload blobs.
- Write intents transition `PLANNED -> SENT -> SUCCEEDED | FAILED | UNKNOWN`.
- One `operation_id` is sent at most once; a repeated call reconciles state and
  never repeats the upstream write.
- A request payload mismatch for an existing operation is rejected.
- Any unresolved economic `SENT`/`UNKNOWN` intent blocks another economic write.
- Decisions can reference only sealed snapshots created by the same run.
- The hourly runner lock requires an absolute path and a timeout below one hour.
- Runner configuration defaults to a 3300-second hard timeout and rejects relative
  state/lock paths or any timeout of one hour or longer.

The Fantasy, Research, MCP transport, prompts, deployment units, and real Codex
runner are separate ownership areas. `runner.vertical_slice` intentionally uses a
mockable `TeamGateway` and performs no network access.

## Local verification

```bash
uv sync --extra dev
uv run pytest
```

Additional deployment checks:

```bash
./deploy/preflight.sh
./deploy/negative-capability.sh
./deploy/healthcheck-fixture.sh
```

`deploy/install-disabled.sh` creates an unprivileged staging copy only.
`deploy/install-system-disabled.sh` is the reviewed root-only installer for the
production paths and dedicated broker user. It installs code, empty root-owned
environment files, units, and the local SQLite schema, but deliberately leaves
all services and the hourly timer inactive and disabled.

Persistent runtime state must live outside the repository, for example:

```text
/var/lib/polemica-ai-agent/agent.sqlite3
/var/lib/polemica-ai-agent/blobs/
```

Never place Fantasy/Polemica credentials in this database, tool payloads, prompts,
or Codex output. Upstream credentials belong only to root-owned environment files
read by the dedicated MCP broker services. See `deploy/OWNERSHIP.md` for the
production ownership and isolation contract.
