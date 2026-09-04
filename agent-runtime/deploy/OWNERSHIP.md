# Reviewed disabled-runtime ownership contract

This repository and installer contain no production secrets and activate nothing.

Fantasy, Research, Compute gateway, and Memory MCP run as separate hardened system services under one dedicated,
non-login `polemica-agent-broker` Unix user. That user alone owns `/var/lib/polemica-ai-agent`, the
SQLite journal, research cache, immutable blobs, and the four root-supplied MCP EnvironmentFiles.
The shared identity is deliberate: a single local trust boundary is required for atomic evidence
seal, decision validation, operation intent journaling, and read-back reconciliation. MCP services
bind only to loopback. The broker must have no shell, sudo, SSH keys, Telegram config, Codex auth,
or access to application and administrator credentials beyond the two scoped upstream credentials.

The Compute gateway receives only its empty root-owned EnvironmentFile, the broker database path,
and `/run/polemica-agent-compute/worker.sock`; it receives no Fantasy or Polemica upstream
credential. The restricted operation engine runs as the separate non-login
`polemica-agent-compute` user in a private network namespace. It has no access to broker state,
Research cache, Codex state, homes, or service EnvironmentFiles. The broker is a supplementary
member of the compute group solely to connect to the ephemeral group-owned AF_UNIX socket. The
worker's systemd `RuntimeDirectory` is the only shared filesystem boundary and is removed with the
unit lifecycle. Before recovering an interrupted computation, the gateway must hold the exclusive
`compute-gateway.lock` lease in broker state; a duplicate process fails before it can mutate audit state.

The runner uses the existing `codex` Unix user, its isolated
`/var/lib/polemica-ai-agent-runner` workspace/log/lock directory, and loopback MCP endpoints. It must
not have filesystem access to broker state or EnvironmentFiles. Codex receives no upstream secret.
The systemd units hide `.ssh`, restrict `/proc`, protect the host filesystem, and keep writes off.

`install-system-disabled.sh` may create the broker and compute users/groups, install code and units,
create empty root-owned `0600` EnvironmentFiles, and migrate the local SQLite schema. It never inserts
credentials, starts services, sets `WRITE_ENABLED=true`, starts a manual run, or enables the timer.
Those activation steps require the separate production activation review.
