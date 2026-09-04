# AI Fantasy Agent MVP — Implementation Plan

**Date:** 2026-09-04
**Source design:** `docs/features/DESIGN-AI-FANTASY-AGENT.md`
**Runtime host:** `codex@51.250.97.185`
**Status:** implemented and verified; production activation pending

## 1. Goal

Implement a hidden Codex-managed Polemica Fantasy user that:

- receives exactly the same starting resources and obeys the same domain rules as a human user;
- uses the existing user API through a narrow Fantasy MCP server;
- can read all historically available Polemica data through a separate read-only Research MCP server;
- keeps durable statistics, decisions, operation intents, outcomes, and strategy notes across independent runs;
- runs once per hour on `codex@51.250.97.185`;
- is not publicly marked as an agent during the initial experiment;
- can use Store, card lifecycle, upgrades, and marketplace without special economic privileges.

Production activation is a separate release gate. This plan includes code, tests, and installation of a disabled runtime on the VM; it does not authorize starting the hidden production experiment before the final production checklist is signed off.

## 2. Accepted constraints

- Other users' teams remain visible through the ordinary user API by product design. Fantasy MCP does not expose a tool for them.
- The hourly cadence is part of the experiment. A series that opens and closes between runs may be missed.
- No public AI badge, TMA change, webhook, fine-tuning, OAuth server, or universal account migration in MVP.
- Bearer auth produces the same `TelegramUser` principal and uses the same controllers/services as TMA auth.
- Full generic idempotency is deferred. `buy_pack` receives backend `Idempotency-Key` support because it is repeatable and cannot be reconciled unambiguously after a timeout.
- Every other write uses a durable local `operation_id`, one upstream attempt, exact read-back, and no blind retry.
- Marketplace is included and enabled only after team-only and read-only stages pass.

## 3. Architecture and trust boundary

```text
hourly launcher
  -> codex exec --ignore-user-config
       -> Fantasy MCP (loopback)
       -> Research MCP (loopback)
       -> Memory MCP (loopback)

one hardened MCP broker Unix user owns all three logical MCP processes,
their upstream secrets, and the shared durable SQLite/evidence store

Fantasy MCP -> https://fantasy.maftourbot.ru/api/v1/**
Research MCP -> Polemica read endpoints only
```

The Codex run receives only typed tools. It does not receive upstream secrets in its prompt, environment, tool output, or memory. A single isolated broker account is used because Fantasy intents, Research evidence, and Memory decisions must share one atomic journal; Codex remains a separate account without access to the broker state or env files. The runtime uses a dedicated directory and does not use the dirty checkout at `/home/codex/work/polemica-fantasy`. Global Codex configuration is ignored so the already configured Telegram MCP/plugin is not loaded. ChatGPT login remains supplied by the host Codex installation.

Known host facts from the read-only preflight:

- Ubuntu 22.04, x86_64, host `torrent`;
- `codex-cli 0.150.1`, already logged in with ChatGPT;
- Python 3.10.12, Node 22.23.2, `uv 0.12.7`;
- global Codex configuration currently enables a Telegram MCP, so isolation is mandatory;
- user systemd is running but linger is disabled; persistent scheduling therefore needs either a system-level service/timer or a cron launcher with `flock`;
- the existing repo checkout on the VM is not a runtime directory and must not be modified by installation.

## 4. Frozen MVP contracts

### 4.1 Bearer credential

- opaque token: `pfa_<32 random bytes encoded base64url>`;
- database stores only SHA-256 of the full high-entropy token plus a display prefix;
- credential has owner, label, creation/expiry/last-use/revocation timestamps, and admin actor metadata;
- invalid, malformed, expired, and revoked credentials all return the same generic `401`;
- Bearer is accepted only by the user security chain, never admin endpoints;
- agent marker is internal and absent from public DTOs.

Admin-only lifecycle API, without admin UI:

```http
POST   /api/v1/admin/agent-users
POST   /api/v1/admin/users/{telegramId}/api-credentials
GET    /api/v1/admin/users/{telegramId}/api-credentials
DELETE /api/v1/admin/users/{telegramId}/api-credentials/{credentialId}
```

The plaintext credential is returned once on creation and never by list/read calls.

### 4.2 Pack purchase idempotency

`POST /api/v1/store/packs/{packId}/buy` accepts `Idempotency-Key`.

- key scope: authenticated user + operation type + key;
- the canonical request hash includes at least `packId`;
- the first committed response and associated economic references are persisted;
- the same key and same pack returns the original result without a second charge/open;
- the same key with a different pack returns `409`;
- the idempotency record and replayable response commit atomically with the balance deduction, pack-open event, generated cards, and pending choice;
- both `INSTANT` and `CHOOSE` pack modes are covered;
- concurrent duplicate requests produce one purchase and one replayed result;
- the header remains optional for the existing TMA but is mandatory in Fantasy MCP;
- records are retained for at least the complete hidden experiment;
- raw keys do not appear in logs or DTOs.

`select_pack_choice` needs no separate backend idempotency record: the existing choice identity is convergent for the same option and conflicts for a different option.

### 4.3 MCP write envelope

Every write accepts an `operation_id` and returns:

```json
{
  "operationId": "uuid",
  "outcome": "SUCCEEDED | FAILED | UNKNOWN",
  "observedAt": "instant",
  "response": {},
  "verification": {
    "source": "user-api endpoint",
    "matchesExpectedState": true
  }
}
```

Before the HTTP write the Memory service atomically records `PLANNED`, then `SENT`. A terminal response plus read-back records `SUCCEEDED` or `FAILED`. A timeout/ambiguous transport result records `UNKNOWN`; repeated `operation_id` with the same canonical payload reconciles but never repeats the upstream write, while reuse with a different payload is rejected. New economic writes stop while an unresolved economic intent exists.

One absolute `flock` path is shared by the timer and manual `run-once`. The second launcher exits before Codex or a write-capable MCP call. A hard timeout is shorter than one hour. Every run reconciles `SENT`/`UNKNOWN` first. SQLite migration, transaction, fsync, disk-full, or read-only failures close the write gate. A memory backup may be restored only together with the corresponding complete operation journal.

### 4.4 Research snapshot

Each decision follows `COLLECT -> SEAL -> DECIDE -> ACT`. A sealed snapshot contains:

- `snapshot_id`, `as_of`, `fetched_at`, source and source object ids;
- raw payload hash/blob reference and parser/schema version;
- sample size, completeness, partial errors, and deduplication metadata;
- versioned derived features.

No fetch newer than the sealed snapshot can be attached to that decision. `seal(run_id, snapshot_id, as_of)` makes the snapshot immutable; every decision must reference it, and the runner rejects a Fantasy write without that link. Further Research calls either remain outside the sealed snapshot or create a new decision revision and a new seal. Corrected source data creates a new immutable payload version with `first_seen_at`, `fetched_at`, source id, payload hash, parser version, and completeness rather than overwriting evidence.

## 5. Delivery phases

### Phase A — vertical slice

Deliver locally before fan-out:

1. Backend creates an agent-managed user and one Bearer credential.
2. Bearer `GET /api/v1/me` resolves exactly that existing user.
3. Minimal Memory service persists a run, decision, and operation intent.
4. Minimal Fantasy MCP exposes profile, open series, own cards/team, and team create/update.
5. A local E2E run discovers a fixture series, submits one team, reads it back exactly, restarts, and retrieves the decision.

Gate: freeze token format, MCP result envelope, SQLite schema v1, tool naming, and package boundaries.

### Phase B — parallel feature completion

Backend:

- credential lifecycle, auth audit, expiry/revocation, internal agent marker;
- `buy_pack` idempotency and concurrency tests;
- TMA/admin regression and Bearer parity tests.

Fantasy MCP:

- remaining reads;
- Store, pack choice, marketplace, renew/recycle, merge, and legendary upgrade;
- typed allowlist, redaction, exact read-back, timeout classification;
- achievement catalog, claim, and reward choice;
- periodic-rating current state, own result/rewards, reward draft, and reward submit.

Research MCP:

- read-only Polemica client and bounded pagination;
- immutable raw cache, sealed snapshots, provenance, partial-result semantics;
- profile/competition/game reads;
- deterministic player/form/role/perk aggregates and comparisons;
- golden fixtures for parity with relevant Kotlin perk detectors.

### Phase C — runner and VM packaging

- system and hourly prompts, tool policy, and experiment manifest;
- fresh `codex exec` invocation with `--ignore-user-config`, bounded timeout, JSONL output, and explicit MCP set;
- single-writer `flock` plus durable recovery of open intents;
- runtime healthcheck, redacted logs, backups, and independent kill switches;
- system service/timer templates and cron fallback;
- additive installation under a dedicated runtime path on `51.250.97.185`, disabled by default.

The current implementation scope ends at disabled installation with `WRITE_ENABLED=false`, inactive MCP/runner units, a disabled timer, fixture-only healthchecks, and no production env files. Backend production deploy/migration, account or credential provisioning, secret installation, production upstream startup, manual production `run-once`, timer enable/start, and any team/economy write require a separate explicit activation gate.

### Phase D — independent review and validation

- code review of backend auth/economy and runtime trust boundary;
- focused unit/integration/protocol/fault-injection tests;
- `./scripts/codex-check.sh quick`;
- disabled VM smoke tests and negative capability checks;
- update `memory-bank/activeContext.md` and `memory-bank/progress.md`.

### Phase E — production release gate

Not executed implicitly by implementation:

1. choose the real-looking Telegram identity and prohibit manual TMA control;
2. choose model/reasoning, credential lifetime, expert cohort, stop rule, retention, and alert channel;
3. deploy backend with no credential issued and verify TMA/Admin regressions;
4. run a separate test account through read-only, team-only, and economy fault tests;
5. create the experiment account through ordinary bootstrap and record its baseline;
6. install secrets without exposing them in Codex/terminal transcripts;
7. run shadow mode, then team writes, Store/lifecycle, and marketplace last;
8. enable the hourly schedule at a recorded time.

## 6. Worker ownership

### Wave 1 — parallel foundations after contract freeze

Backend worker owns:

- `polemica-fantasy-backend/**` only;
- credential/auth/provisioning, bounded auth audit, internal marker, and a single reserved Flyway migration;
- `buy_pack` idempotency for both pack modes and focused tests.

Runtime foundation worker owns:

- `agent-runtime/src/polemica_agent/common/**`, `memory_mcp/**`, SQLite migration v1, and their tests;
- operation journal/state machine, hashing/redaction, lock, and runner skeleton;
- a mock-based vertical slice through team write/read-back.

Research worker owns:

- `agent-runtime/src/polemica_agent/research_mcp/**` and its tests/fixtures;
- read-only client, cache/provenance, sealed snapshots, aggregates, and hostile-data tests.

Workers do not modify one another's scopes. Shared manifests/contracts are owned by the main integrator.

### Wave 2 — after integration gate 1

1. Fantasy MCP worker: every permitted read/write tool, including achievements and periodic-rating rewards, plus fake-server fault tests.
2. Runner/prompt worker: orchestration, decision revisions, fail-closed policy, retrieval, and post-mortem.
3. Ops worker: hardened service/timer templates, installer, preflight, healthcheck, and negative-capability tests.

Shared contracts, dependency lock, and migration numbering remain owned by the main integrator.

### Review wave

Independent reviewers inspect:

- security/auth/secret isolation;
- economy races and ambiguous writes;
- MCP registry and absence of generic HTTP/Polemica write tools;
- recovery, deadline, clock-skew, disk-full, and prompt-injection behavior.

## 7. Acceptance tests

Backend:

- valid Bearer maps to the intended existing user; it never bootstraps a user;
- unknown/malformed/expired/revoked Bearer returns `401` and updates no user state;
- TMA representative read/write still passes;
- Bearer fails on admin, Basic fails as user auth;
- token/hash/agent marker never appears in public DTOs or logs;
- create/list/revoke credential follows one-time-secret semantics;
- duplicate/concurrent pack requests with one key charge and open exactly once.
- persistent auth audit records successes and failures for a recognized credential; unknown/malformed attempts use bounded structured logs/metrics only;
- token length/format is rejected before hash/DB lookup, and activation requires an auth-failure rate limit.

Runtime/MCP:

- registry contains no foreign-team, generic HTTP, shell, SQL, admin, Telegram, or Polemica write tool;
- all tool arguments are ownership/type/range validated;
- adversarial nicknames/descriptions remain bounded data, not instructions;
- two simultaneous runs execute only one writer;
- the loser never reaches Codex or an MCP write, and the hard timeout is below one hour;
- crash before/after HTTP commit recovers without a blind retry;
- intent is durable before the network write; same `operation_id` with a different payload is rejected;
- journal unavailable, disk full, version mismatch, unresolved economic intent, incomplete required state, or excessive clock skew fails closed for writes;
- backup restore without the matching complete operation journal is rejected;
- secrets do not occur in tool output, prompt, JSONL, SQLite, or blobs;
- sealed snapshots exclude subsequently fetched data and preserve corrected historical payload versions;
- every write decision references a sealed snapshot; a later fetch requires a new decision revision/seal;
- a restart restores decisions and open intents.

VM disabled installation:

- `codex exec --ignore-user-config` is present and verified;
- workspace is separate from all repository checkouts and unrelated `AGENTS.md` files;
- MCP services run as one isolated broker Unix user, bind only loopback/Unix sockets, and alone receive upstream secret env files and the shared journal;
- the Codex unit has no Fantasy/Polemica secrets and cannot read MCP env files, `/proc/<mcp>/environ`, or `~/.ssh`;
- no user/global MCP overrides are loaded;
- installed units and timer report disabled/inactive, `WRITE_ENABLED=false`, production env files are absent, and fixture healthchecks pass;
- if equivalent isolation cannot be demonstrated on this Codex/host version, installation stops rather than weakening the contract.

Confidentiality:

- repository and CI artifact visibility are checked before commit/push;
- these docs are never included in public frontend artifacts;
- runtime unit/log names and monitoring metadata are operator-only;
- if repository privacy is unsuitable, the design/runtime code stays unpushed or moves to a private repository before activation.

End to end:

```text
provision test user -> Bearer profile -> discover series -> seal research snapshot
-> choose cards -> submit team -> exact read-back -> restart runtime
-> record result -> post-mortem
```

## 8. Rollback

- revoke Fantasy credential;
- stop/mask schedule and stop MCP services;
- disable MCP write mode;
- disable backend Bearer feature flag if needed;
- leave additive schema in place;
- preserve SQLite, raw snapshots, and audit evidence;
- do not reverse marketplace/economic actions automatically because they may involve other users.

## 9. Immediate execution order

1. Independent plan-review gate.
2. Recheck git status and reserve the next Flyway number.
3. Assign Wave 1 and implement the vertical slice.
4. Review and freeze shared contracts.
5. Assign Wave 2 in parallel.
6. Integrate and run component/E2E tests.
7. Install only the disabled, fixture-configured VM runtime and prove inactive/no-secrets state.
8. Independent final code/QA review and fixes.
9. Run broad validation and prepare the separate production activation checklist.

## 10. Implementation result

Implemented on 2026-09-04:

- backend Bearer credentials for automated users, admin provisioning/revocation, auth audit, rate limiting, TMA-login exclusion, notification exclusion, and `buy_pack` idempotency;
- separate typed Fantasy, Research, and Memory MCP services with fixed capability registries;
- durable sealed research evidence, decision lineage, operation journal, reconciliation, and persistent ACT authorization;
- hourly Codex runner with ignored user configuration, required MCPs, read-only sandbox, scrubbed environment, and fail-closed write controls;
- hardened systemd deployment assets and operator runbooks;
- disabled runtime staged at `/home/codex/.local/share/polemica-agent-runtime` on `codex@51.250.97.185` without production secrets, enabled units, timers, or writes.

Verification completed:

- 104 runtime tests passed on Python 3.10;
- backend focused unit tests and Kotlin compilation passed;
- Bearer/idempotency integration and concurrent duplicate-purchase tests passed against PostgreSQL 16 via Testcontainers on the runtime VM;
- negative-capability, fixture-health, compile, preflight, and disabled-install checks passed locally/remotely;
- independent backend and runtime reviews found no remaining P0/P1 issues after correction waves.

Activation remains intentionally blocked on private delivery of the currently uncommitted implementation, backend deployment/migration, creation of the real agent identity and credentials, privileged installation of the broker system user and system units, operator policy choices, and an explicit production go-live decision.
