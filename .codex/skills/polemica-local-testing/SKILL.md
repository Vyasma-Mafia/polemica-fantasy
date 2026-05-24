---
name: polemica-local-testing
description: Launch, test, and troubleshoot the Polemica Fantasy local development stack. Use when the user asks to run or test the Telegram Mini App webapp, admin frontend, Vite dev servers, local browser checks, local stack startup, or to generate a fresh VITE_DEV_INIT_DATA / Telegram initData token for local TMA testing.
---

# Polemica Local Testing

## Overview

Use this skill in the `polemica-fantasy` repository when local UI verification is needed. The repo already provides the reusable scripts; prefer those scripts over retyping auth or launch logic.

## Quick Workflow

1. Confirm you are in the repo root: `/Users/chulkov-alex/personal/mafia/polemica-fantasy`.
2. Check `git status --short` before changing files.
3. For quick build verification, run `./scripts/codex-check.sh quick`.
4. For interactive local testing in Codex, prefer long-running foreground tool sessions. Do not use `nohup`, `screen`, `tmux`, shell backgrounding (`&`), or detached subshell tricks; in this environment those often exit silently or lose `VITE_DEV_INIT_DATA`.

## Fast Local Launch In Codex

If the backend is already healthy, skip `local-up.sh` and start only the two Vite dev servers. This is the fastest path when checking frontend changes or when you need a specific fresh local user profile.

1. Check backend health:

```bash
curl -fsS http://localhost:8081/actuator/health
```

2. If healthy, start admin in one long-running `exec_command` session:

```bash
cd polemica-fantasy-admin && npm run dev -- --host 0.0.0.0 --port 5174
```

3. Start TMA in a second long-running `exec_command` session with fresh initData:

```bash
cd polemica-fantasy-webapp && VITE_DEV_INIT_DATA="$(../scripts/generate-tma-init-data.py --format raw)" npm run dev -- --host 0.0.0.0 --port 5175
```

For a clean/new local profile, pass an explicit Telegram id:

```bash
cd polemica-fantasy-webapp && VITE_DEV_INIT_DATA="$(../scripts/generate-tma-init-data.py --format raw --telegram-id 889524 --username onboard_new_889524 --first-name Onboard --last-name New)" npm run dev -- --host 0.0.0.0 --port 5175
```

4. Verify HTTP from a separate command:

```bash
curl -sS -o /dev/null -w '%{http_code}' http://localhost:5175
curl -sS -o /dev/null -w '%{http_code}' http://localhost:5174
```

Leave the two `exec_command` sessions running until the user is done. Stop them by sending `Ctrl+C` to each session.

## Full Stack Launch

If backend is not healthy, or if you want one command that starts Docker Compose plus both frontends, run this in one foreground `exec_command` session:

```bash
./scripts/local-up.sh --generate-init-data
```

This starts `fantasy-backend` through Docker Compose, waits for backend health, then starts:

- Admin UI: `http://localhost:5174`
- TMA UI: `http://localhost:5175`
- Backend API: `http://localhost:8080`
- Backend health: `http://localhost:8081/actuator/health`

Frontend dev-server logs go to `.local-dev-logs/admin.log` and `.local-dev-logs/tma.log`.

For a specific fresh user profile through `local-up.sh`, generate initData first and pass it via env in the same foreground command:

```bash
VITE_DEV_INIT_DATA="$(./scripts/generate-tma-init-data.py --format raw --telegram-id 889524 --username onboard_new_889524 --first-name Onboard --last-name New)" ./scripts/local-up.sh
```

## Generating TMA InitData

The TMA requires `Authorization: tma <initData>` and local Vite reads `VITE_DEV_INIT_DATA`. The backend rejects stale initData after 24 hours, so generate a fresh value for each local session.

Use:

```bash
./scripts/generate-tma-init-data.py
```

The script reads `TELEGRAM_BOT_TOKEN` from the environment or the root `.env`. Useful variants:

```bash
./scripts/generate-tma-init-data.py --format raw
./scripts/generate-tma-init-data.py --telegram-id 888001 --username localdev --first-name LocalDev
VITE_DEV_INIT_DATA="$(./scripts/generate-tma-init-data.py --format raw)" ./scripts/local-up.sh
```

Do not paste real bot tokens into the conversation. Use the repo `.env` or environment variables.

## Browser Verification

After frontend changes, use the Browser plugin for local pages when available.

- Open admin at `http://localhost:5174`.
- Open TMA at `http://localhost:5175`.
- Check that the TMA does not show `MissingInitDataNotice`.
- For admin, log in with local Basic Auth credentials configured in `.env`.
- Inspect `.local-dev-logs/*.log` if the page is blank or Vite failed.

## Targeted Checks

- Backend compile: `./scripts/codex-check.sh backend`
- Full backend tests: `./scripts/codex-check.sh backend-test` (requires Docker/Testcontainers)
- TMA build: `./scripts/codex-check.sh webapp`
- Admin build: `./scripts/codex-check.sh admin`
- Both frontend builds: `./scripts/codex-check.sh frontend`
- Frontend lint: `./scripts/codex-check.sh lint`

## Common Failure Modes

- `Operation not permitted` under `~/.gradle` or npm cache: rerun the same command with the required sandbox escalation.
- `TELEGRAM_BOT_TOKEN is required`: export it or add it to the repo root `.env`.
- TMA shows missing initData: regenerate with `./scripts/local-up.sh --generate-init-data`; old tokens expire after 24 hours.
- Backend health does not become ready: check Docker Compose status and backend logs.
- Dev servers do not start when using `nohup`, `screen`, or `&`: restart them as foreground `exec_command` sessions. This is the expected Codex workflow for local interactive testing.
