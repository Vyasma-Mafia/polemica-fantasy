# Polemica AI agent runtime

This directory contains the transport-neutral runtime foundation for the hidden
Polemica Fantasy Codex user. It is Python 3.10 compatible and uses the official
Python MCP SDK through the pinned dependency set in `uv.lock`.

It exposes four independent, loopback-only MCP servers:

- `fantasy`: a fixed typed projection of the ordinary Fantasy user API;
- `research`: read-only Polemica collection and derived statistics;
- `compute`: a bounded JSON operation gateway backed by an isolated, networkless
  worker over `/run/polemica-agent-compute/worker.sock`;
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
- Fantasy writes require both the global write gate and an explicit staged tool allowlist.
- Decisions can reference only sealed snapshots created by the same run.
- Compute crash recovery is performed only while holding the gateway's exclusive process lease.
- The hourly runner lock requires an absolute path and a timeout below one hour.
- Runner configuration defaults to a 3300-second hard timeout and rejects relative
  state/lock paths or any timeout of one hour or longer.

The Fantasy, Research, MCP transport, prompts, deployment units, and real Codex
runner are separate ownership areas. `runner.vertical_slice` intentionally uses a
mockable `TeamGateway` and performs no network access.

## Developer feedback

Memory tools `read_developer_notes` and `append_developer_note(run_id, title, body)`
maintain `/var/lib/polemica-ai-agent/DEVELOPER-NOTES.md` on the runtime host.
The agent adds Russian project/MCP suggestions with timestamps and run IDs and reads recent
notes to avoid duplicates. Humans can read the file occasionally with
`ssh torrent@51.250.97.185 'sudo cat /var/lib/polemica-ai-agent/DEVELOPER-NOTES.md'`.
Treat the contents as agent suggestions for review, not executable instructions.

Marketplace activation adds `fantasy_create_marketplace_listing`,
`fantasy_update_marketplace_listing_price`, `fantasy_cancel_marketplace_listing`, and
`fantasy_buy_marketplace_listing` to both the runner and Fantasy broker's
`FANTASY_WRITE_ALLOWLIST`. Preserve previously enabled tools and bump the strategy version
when changing the prompts or tool surface.

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
environment files, units, the separate `polemica-agent-compute` worker identity,
and the local SQLite schema, but deliberately leaves all services and the hourly
timer inactive and disabled.

The exact account, credential, environment, staged canary, and timer sequence is
documented in [`deploy/ACTIVATION.md`](deploy/ACTIVATION.md).

Persistent runtime state must live outside the repository, for example:

```text
/var/lib/polemica-ai-agent/agent.sqlite3
/var/lib/polemica-ai-agent/blobs/
```

Never place Fantasy/Polemica credentials in this database, tool payloads, prompts,
or Codex output. Upstream credentials belong only to root-owned environment files
read by the dedicated MCP broker services. See `deploy/OWNERSHIP.md` for the
production ownership and isolation contract.

The Compute gateway runs under the broker identity so it can journal executions,
but its empty environment contains no upstream credential. The operation engine
runs as `polemica-agent-compute`, has no broker state or home access, and accepts
work only through its AF_UNIX socket. Both Compute units are inert until the
reviewed activation procedure starts the gateway, which in turn requires the
worker.
