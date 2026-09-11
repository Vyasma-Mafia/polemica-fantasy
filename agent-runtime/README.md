# Polemica AI agent runtime

## Token efficiency (v25)

- `fantasy_collect_evidence(detail="compact")` is the default model view. Full canonical
  observations, source hashes and ACT authorization are unchanged; `detail="full"`
  returns full presentation. All card/price/perk/eligibility fields remain available.
- Memory defaults to eight bounded decision summaries (`compact=False` for full records).
  Mailbox reads default to head/latest 6,000 characters, with hash-based unchanged checks
  and `compact=False, offset=..., limit=...` pagination. Omitted text is not assumed resolved.
  SEAL defaults to counts instead of repeated manifests, keeping every error and snapshot ID;
  `compact=False` can read the existing immutable full seal.
- COMPLETE research aggregates use a bounded process-local cache keyed by exact sources,
  players, typed game versions, points and perks. Fresh reads/attachments still occur:
  this cache saves calculation, not upstream requests. Restart simply empties it.
- `POLEMICA_AGENT_REASONING_EFFORT=medium` explicitly configures Codex; supported overrides
  are low/medium/high/xhigh. The deployed model remains Astra, not a cheaper substitute.
  Stable instructions precede dynamic runtime context. No claim of guaranteed cross-run
  prompt caching is made; inspect actual counters.
- Opt-in `POLEMICA_AGENT_CHANGE_GATE_ENABLED=true` keeps hourly wake checks but avoids
  model invocation only for unchanged successfully validated state after an explicitly idle
  successful run. Preflight uses fixed read-only MCP tools (including inventory, teams,
  rewards, listings, packs, economy, series rosters/leagues and a bounded 100-listing market
  watch); it supplies no action evidence. Missing/invalid data, errors, pending intents,
  changed config/prompts, or a deadline within two hours force model invocation. No market
  page is treated as exhaustive. Exploration runs at least every four hours, configurable
  with `POLEMICA_AGENT_EXPLORATION_INTERVAL_SECONDS=3600..14400`. This can delay discovery
  of opportunities outside the watched market page until exploration.
- Final model JSON includes `idleSafe`; absent/false, technical blockers, deferred chains
  or exhausted action/time bounds prevent skipping. A pre-run baseline is saved only after
  success, so actions that changed state cause a fresh assessment next hour. Wake skips are
  logged separately in `wake-checks.jsonl`, not as fictitious successful game runs.
- `polemica-agent-run --force` bypasses only the wake filter. All action safety checks remain.
  Existing systemd environment/isolation must be used for manual production execution.
- Exact nonnegative integer usage counters survive redaction only under top-level CLI
  `turn.completed.usage`. Secrets elsewhere remain redacted. Run
  `sudo python3 /opt/polemica-ai-agent/deploy/usage_report.py --hours 24` for measured usage.
  Historical redacted counts remain unknown. Input/cached and output/reasoning counters
  overlap; never add them as independent quantities or infer ChatGPT subscription cost.

The 12-action / 20-minute session, deadline margin, sealed evidence, read-back, unresolved
intent stop rules and existing 17-operation allowlist are unchanged. Rollout requires full
runtime tests, an inactive runner, backup, installed MCP/read-only canary and model smoke.

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

`fantasy_collect_evidence` accepts `marketplace_searches` (max5 unique objects with required
`fantasy_player_id`, `rarity` and optional `page` 0..100). Broker-owned reads capture the full
public listing page (size100, price ascending), including listing IDs, exact price, card/perks,
buyability and pagination. Only returned entries support a purchase decision. Empty pages are
valid observations, not proof of global absence. Invalid/failed responses leave the collection
PARTIAL without attaching a subset of the batch. Evidence does not reserve a lot or lock its price.

Strategy v21 requires a paid-pack versus concrete marketplace-card assessment before an
economic no-op (or an explicit missing-data/deadline/affordability blocker). It treats the
operator's pack-profit observation as a testable prior, accounts for reserves and unsold
inventory, and permits a bounded learning purchase: one unresolved speculative pack across
runs, with cost at most 25% of liquid balance and within unreserved funds. These are prompt
instructions, not broker-enforced spending limits; normal evidenced purchases and all existing
technical gates remain unchanged. Results must be tracked without treating listing asks as
realized proceeds. Prompt-presence tests do not establish actual agent behavior.

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

### Recovery contracts

For Fantasy-only actions, BEGIN a collection and call `fantasy_collect_evidence`
with its `run_id` and `collection_id` before SEAL. The broker fetches a fixed bounded
bundle (optional achievement codes, series IDs, up to 20 `fantasy_player_ids`, and
up to 10 `marketplace_analytics` pairs of `fantasy_player_id` and `rarity`), validates the full response
batch and attaches immutable, redacted `fantasy-user-api` records atomically.
It accepts no caller-provided evidence or URLs. Ordinary Fantasy reads remain
non-collecting. Failed batches attach no subset and mark the collection PARTIAL;
use a fresh collection after resolving the required source failure.

`fantasy_get_player(fantasy_player_id)` resolves any real player's internal Fantasy ID
to `polemicaUserId`, `playerNickname` and `playerPhotoUrl`, including choose-pack options.
Use the Polemica ID for Research. This ordinary authenticated API exposes no user accounts.
`fantasy_get_marketplace_analytics(fantasy_player_id=..., rarity=...)` includes `asOf`
and `salesWindows` for 7/30-day `[from,to)` completed sales, counts, gross min/max/median
prices, median time-to-sale in seconds and its sample size. Existing recent10 sales and
their average remain available; these are separate from full-window aggregates.
Sanctioned trades are included. Time-to-sale includes only sold listings since creation,
not sell-through probability or cancelled/relisted history; invalid negative durations
are excluded from the duration sample. Legacy sales without a saved template use current
rarity. Empty windows have zero count and null price/duration statistics. Collect relevant
identity and analytics selectors before SEAL to persist the observations used for a decision.

The historical `TRUSTED_RESEARCH` marker identifies the sealed broker collection;
its manifest explicitly distinguishes Fantasy observations from Polemica research.
Fantasy-only evidence does not provide form/perk statistics, and Compute continues
to require `profile-games-page` records. Empty collections remain rejected with
actionable `EMPTY_EVIDENCE` guidance. No trust/auth gates or gameplay permissions
are bypassed, and no database migration is required.

All 17 Fantasy write tools have read-only reconciliation dispatch. The journal
persists pre-write context and the acknowledged response separately before
read-back, so retries and process restarts never resend the write. Migration 005
adds these immutable evidence references; existing response blobs remain readable.

`fantasy_get_achievement_claim_state` exposes the ordinary user's existing pending
options and selected-card receipts without generating rewards. A claim with pending
options is successful preparation; each choice is a separate operation. Merge
preview is also preparation, not card consumption. Selection checks exact options
and issued cards, not merely that a pending choice disappeared.

Missing evidence is not success: lost responses for recycling, merge confirmation,
or pack selection can still remain `UNKNOWN` and need developer investigation.
Never clear these intents or repeat their writes blindly. Runs with unresolved
intents are reported as failed, not successful. Invalid Research locator/perk
arguments are rejected before modifying the run's snapshot.

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
# Research window and PARTIAL diagnostics (v23)

`get_player_games(limit=20, page_size=20, max_pages=1)` requests a bounded
window in upstream profile order. Omitting `limit` still requests full history;
`max_pages` alone is a safety bound, not a sample size. Window responses expose
`coverage=WINDOW` and `requestedLimit`; COMPLETE does not mean full-career coverage.
SEAL returns durable `errors` with operation/code/subject/message, also included
in the hashed manifest. `errorCount` counts distinct diagnostic causes (repeated
and derived observations are deduplicated), not failed request attempts.
Older collections may have `errorDetailsComplete=false`: lost historical details
cannot be recovered by migration. Existing sealed evidence is never rewritten.
PARTIAL evidence remains ineligible for decisions and game writes; start a new
collection and recollect required facts after resolving the cause.

## Research and operation blocker fixes (v24)

For `ninja`, collect profile games for the exact player in the same collection
before `get_player_perk_rates`. The broker joins hash-verified completed profile
rows to typed game locators, includes the points source in provenance, and rejects
missing, ambiguous or conflicting points. Competition points require an explicit
matching competition identity in the source. Unknown points are not zero events.
Perk results report excluded games and observed sample sizes; duplicate locators
are not double-counted. Prefer page_size=40/max_pages=2 for a limit=20 window to
leave room for duplicate rows; inspect completeness regardless of chosen bounds.

ACT denials expose fixed safe codes in both MCP errors and audit. In particular,
`ACT_DEADLINE_MARGIN` preserves the existing 300-second team cutoff. Plan complete
purchase-to-team chains before that cutoff; multiple complete same-run snapshots
remain supported. Terminal operation replays retain their existing safety rules.
Exact known listing-creation rejections expose businessErrorCode, including
`CARD_USES_EXHAUSTED`; minListingPrice is not a sellability guarantee. No backend
rules, funds, credentials, write allowlists or uncertainty/reconciliation gates change.
