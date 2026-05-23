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
4. For interactive local testing, start the stack with a fresh TMA initData token:

```bash
./scripts/local-up.sh --generate-init-data
```

This starts `fantasy-backend` through Docker Compose, waits for backend health, then starts:

- Admin UI: `http://localhost:5174`
- TMA UI: `http://localhost:5175`
- Backend API: `http://localhost:8080`
- Backend health: `http://localhost:8081/actuator/health`

Frontend dev-server logs go to `.local-dev-logs/admin.log` and `.local-dev-logs/tma.log`.

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
