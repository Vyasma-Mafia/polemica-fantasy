# Telegram League Import — shadow reader and backend review delivery

The worker reads `@polemica_closed_league` through a dedicated Telethon user
session and stores an auditable local inbox. The worker remains structurally
`SHADOW`-only: it has no Fantasy admin/database credentials and cannot create or
finalize a series. In `BACKEND` delivery mode it sends immutable source evidence
to one HMAC-protected backend ingest endpoint. Only the Fantasy backend can offer
the two-step operator confirmation and create a production series.

## Where information goes

Every observed message and revision is stored in
`~/.local/share/polemica-fantasy/telegram-import/shadow-inbox.sqlite`. Candidate
delivery has two explicit, fail-fast modes:

- `DIRECT` preserves the legacy `[SHADOW][NO PRODUCTION WRITE]` bot alert;
- `BACKEND` suppresses direct worker alerts and posts source identity, local
  revision, source version/timestamps, SHA-256 content hash, and exact raw text
  to the backend. The existing Fantasy bot then owns admin-chat notifications.

There is no fallback destination. A worker event is marked delivered only after
a backend 2xx response. Network errors, 429 and 5xx are retried with the same
delivery ID; other 4xx responses are terminal. Delivery ID, key ID, timestamp,
and exact JSON bytes are all bound by the HMAC signature.

Legacy `DIRECT` configuration:

Notifications are disabled when both variables are absent:

```dotenv
TELEGRAM_IMPORT_OPERATOR_NOTIFICATIONS_ENABLED=true
TELEGRAM_IMPORT_OPERATOR_BOT_TOKEN=123456:replace_me
TELEGRAM_IMPORT_OPERATOR_CHAT_ID=-1001234567890
# TELEGRAM_IMPORT_OPERATOR_THREAD_ID=123
```

Token and chat ID must be configured together. A test can be sent only to that configured
destination with `notify-test`; Bot API errors never log the token or request
URL. Delivery is at-least-once: a crash after Telegram accepts a notification
can produce a duplicate. HTTP 429 honors `retry_after` plus jitter, 5xx/network
errors back off, and 403 is terminal.

`BACKEND` worker configuration in the restricted import env file:

```dotenv
TELEGRAM_IMPORT_DELIVERY_MODE=BACKEND
TELEGRAM_IMPORT_BACKEND_INGEST_URL=https://fantasy.maftourbot.ru/api/v1/internal/telegram-league-import/events
TELEGRAM_IMPORT_BACKEND_INGEST_KEY_ID=tg-import-2026-08
TELEGRAM_IMPORT_BACKEND_INGEST_SECRET=replace_with_random_secret
```

Before switching to `BACKEND`, remove the legacy
`TELEGRAM_IMPORT_OPERATOR_BOT_TOKEN`, chat/thread IDs and notification-enable
flag from the worker env. Backend mode refuses to start while those Bot API
credentials remain. A clean SQLite bootstrap is silent in both delivery modes;
use the explicit `reclassify-message --message-id ...` command to hand off one
chosen existing candidate after ingest-only canary checks.

The backend has independent, default-off gates for ingest, operator
notifications, callbacks, result processing and all production writes. Each
LP/ZL policy separately selects `create-mode` and `finalize-mode` from
`DISABLED | MANUAL | AUTOMATIC` (default `DISABLED`). Its `.env` pins the exact numeric source
channel and admin chat plus ACTIVE/STANDALONE LP/ZL tournament policies. The
worker and backend current ingest key ID/secret must match; a previous backend
key is optional during rotation. Callback signing uses a separate backend-only
secret and is never provided to the worker.

Automatic modes are deliberately fail-safe. They may remain armed while the
independent global gates are off; execution of `AUTOMATIC` creation requires
production writes and operator notifications, and finalization additionally
requires result processing. Both modes require a non-disabled `policy-generation`
and an explicit `automation-cutover-at`; changing mode or
generation cancels outstanding jobs and manual buttons rather than reviving them
later. The admin chat receives `AUTO_CREATE_PENDING` / `AUTO_FINALIZE_PENDING`
with the hold, mode and generation. An automatic write is not leaseable until
Telegram has successfully accepted that pending notification; only then does
the backend persist `delivered_at` and start a fresh 120-second cancellation
hold. Bot API outage therefore blocks the production write instead of merely
delaying its alert. After the result has passed its 15-minute/three-stable-poll
readiness gates, finalization still goes through this separate delivered-alert
hold.

Rollout order is intentionally one-way and canaried: enable ingest only, then
operator notifications, then callbacks for manual preview/confirm, then the
global production-write gate with policies still `MANUAL`. For automation, set
a new generation and future cutover before changing one league policy to
`AUTOMATIC`. Turning off the feature, a write/result gate, or changing
mode/generation terminally cancels queued work; switching it back on with the
same generation does not revive that work. A safe manual preview remains usable
while the production-write gate alone is off, but its confirmation cannot write.

Admin finalization is also two-step: the UI first calls
`GET /api/v1/admin/series/{id}/completion-preview`, displays the readiness reason
or checksum, and then posts that exact checksum to
`POST /api/v1/admin/series/{id}/finalize`. Any intervening result, game, scoring,
roster, selector/alias, card, reward or deadline change makes the checksum stale
and the write is rejected. Successful sync and scoring each record the exact
game-selector fingerprint; completion requires both records to match the current
tournament selector, roster and aliases.

## Authorization (one time)

Use a dedicated Telegram account subscribed to the channel with 2FA enabled.
Create user API credentials at <https://my.telegram.org/apps>. The MTProto
session has the full authority of that account even though this code invokes
only read methods.

Create separate, restricted storage on the VPS:

```bash
install -d -m 700 "$HOME/.config/polemica-fantasy"
install -d -m 700 "$HOME/.local/share/polemica-fantasy/telegram-import"
touch "$HOME/.config/polemica-fantasy/telegram-import.env"
chmod 600 "$HOME/.config/polemica-fantasy/telegram-import.env"
```

Required env file content:

```dotenv
TELEGRAM_IMPORT_API_ID=123456
TELEGRAM_IMPORT_API_HASH=replace_me
TELEGRAM_IMPORT_PHONE=+79990000000
TELEGRAM_IMPORT_CHANNEL=@polemica_closed_league
```

Do not put these values in the repository `.env` because it is injected into
the Fantasy backend. Authorize from an interactive shell:

```bash
ssh -t mafia@51.250.18.236
cd ~/polemica-fantasy
./scripts/telegram-league-import/auth-on-vps.sh
```

The code/2FA password is entered directly in that terminal. The reusable
session and `authorized-channel.json` are mode `0600`; the latter pins both the
numeric account and numeric channel identities. Auth remains a one-shot TTY
container with restart disabled.

## VPN and worker startup

Install the existing fail-closed guard once if it is not already installed:

```bash
sudo ./scripts/telegram-league-import/install-vpn-egress-guard.sh
sudo /usr/local/sbin/polemica-telegram-egress-guard check
```

The worker uses only the external `172.24.0.0/28` Docker network. Policy table
201 routes that source through `wg-tg`; the nftables guard rejects fall-through
to any other interface. The same guard discovers the backend Compose subnet and
routes only Telegram Bot API traffic (`149.154.160.0/20`) through `wg-tg`, with
the same fail-closed protection. Other backend traffic keeps its ordinary
route. Both auth and daemon launchers refuse to proceed unless the VPN, timer,
network and guard are present.

Start or upgrade only the worker service:

```bash
./scripts/telegram-league-import/start-worker-on-vps.sh
docker compose -f docker-compose.prod.yml --profile telegram-import ps telegram-import-worker
docker compose -f docker-compose.prod.yml --profile telegram-import logs --tail=100 telegram-import-worker
```

The daemon has no TTY, uses a bounded `on-failure:5` restart policy (so a bad
account/channel identity cannot loop forever), holds `.session.lock` for its
entire lifetime, validates account/channel/source identity, uses IPv4 only,
honors Telegram FloodWait, applies bounded network backoff and exits cleanly on
SIGTERM.

## Inbox and classification semantics

SQLite uses WAL, `synchronous=FULL`, foreign keys, a busy timeout and mode
`0600` for DB/WAL/SHM. Schema-version, messages, immutable revisions, outbox and
state/watermarks are stored locally. Each new revision, notification candidate
and cursor advancement is one transaction. Exact duplicates are no-ops;
newer pending revisions supersede older pending notifications. A delivered
candidate that later changes emits `REVISED`; one that stops matching emits
`RETRACTED`.

On first start the worker records high-watermark `H`, backfills at most
`TELEGRAM_IMPORT_INITIAL_BACKFILL` (default 100) messages `<= H` silently, then
immediately captures a new upper bound and processes `(H, upper]` in ascending
order. Every later catch-up is likewise upper-bounded. Each cycle scans recent
messages for edits and advances cyclic pages over saved message IDs (not assumed
contiguous numeric ranges) for older edits.

Classifier v1 is deliberately conservative. Text is NFKC-normalized and
case-folded. Only `ЗЛ` and `ЛП` are supported:

- announcement candidate: exact `#анонс_зл` / `#анонс_лп` hashtag plus explicit
  date and time; dates may be numeric (`11.08`) or use a Russian month name
  (`11 августа`);
- result candidate: exact `#результаты_зл` / `#результаты_лп` hashtag plus a
  numbered `Игра N` block and winner structural marker;
- everything else: `IGNORE`.

`grouped_id` and media type are retained. Media download and OCR are deferred.
An album whose meaningful text is spread across multiple messages can therefore
remain ignored until album aggregation/OCR is implemented.

## Operations

Staged backend rollout keeps every production write off until the preceding
stage is observed:

1. Enable backend feature + ingest only; leave backend operator notifications,
   callbacks, result processing, production writes and all policy modes disabled. Switch the worker to `BACKEND` and inspect
   persisted ingest/audit state.
2. Set one league to `create-mode=MANUAL`, enable operator notifications and
   callbacks, but keep production writes off. Reprocess one announcement: its
   `Проверить создание` preview is safe to inspect, while any confirm/write is
   terminally cancelled by the closed write gate.
3. After the preview canary, enable production writes and explicitly reprocess
   the announcement. Preview performs fresh source/tournament/deadline/duplicate
   checks and sends `Создать без состава`, bound to the same human actor and
   exact delivered chat/message. Success explicitly reports roster size 0.
4. Enable result processing with `finalize-mode=MANUAL`. RESULT posts must link
   through an ANNOUNCEMENT source and pass sync/score/readiness before the
   two-step same-actor finalize confirmation is offered.
5. `AUTOMATIC` is a separate opt-in requiring a new policy generation and UTC
   cutover timestamp. Modes may remain armed while global write/result gates are
   off. Auto-create waits at least 120 seconds after its pending alert is
   delivered; auto-finalize additionally waits 15 minutes and three identical
   polls, then at least 120 seconds after its pending alert is delivered. Closing
   a global gate or changing mode/generation terminally cancels outstanding work.

Turning the worker back to `DIRECT` is a delivery rollback only. Disable backend
flags independently in reverse order. `BACKEND` suppresses direct outbox
creation, so stale direct alerts cannot accumulate and replay after rollback.

```bash
# One polling cycle (worker must be stopped because both hold the session lock)
./scripts/telegram-league-import/run-shadow-command-on-vps.sh poll-once

# Safe local DB/status inspection; does not acquire the Telethon session lock
./scripts/telegram-league-import/run-shadow-command-on-vps.sh inspect

# Health: SQLite quick_check/schema, heartbeat/recent poll, FloodWait degraded
# allowance, and pending outbox age
./scripts/telegram-league-import/run-shadow-command-on-vps.sh health

# Explicit operator-destination smoke test
./scripts/telegram-league-import/run-shadow-command-on-vps.sh notify-test

# Idempotently replay one stored post after a classifier-rule correction.
# Stop the daemon first because replay acquires the Telethon session lock.
./scripts/telegram-league-import/run-shadow-command-on-vps.sh reclassify-message --message-id 2234
```

The wrapper exports the restricted env/session paths and UID/GID, reapplies the
VPN guard and repeats all three route probes before running the command.

To revoke access, stop the worker, terminate the dedicated account session in
Telegram Settings → Devices, then securely remove the local session material.
For rollback without revoking authorization, stop only `telegram-import-worker`;
the session and shadow inbox remain mounted on the host for audit/retry.
