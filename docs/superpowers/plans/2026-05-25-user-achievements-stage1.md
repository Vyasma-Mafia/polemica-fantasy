# User Achievements Stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Stage 1 user-achievement backend slice and minimal TMA claim/list UI.

**Architecture:** Add Flyway-owned achievement definitions/rewards/user progress tables, then keep progress as recomputed cached aggregates updated by Spring events after commit. Claims run in a lock-protected transaction using internal `telegram_user.id`, recomputing completion before issuing fantiki/cosmetic rewards.

**Tech Stack:** Kotlin/Spring Boot/JPA/PostgreSQL/Flyway, MockMvc integration tests, React/Vite/TanStack Query.

---

### Task 1: Red Tests

**Files:**
- Create: `polemica-fantasy-backend/src/test/kotlin/io/github/mralex1810/fantasy/AchievementStage1IntegrationTest.kt`

- [ ] **Step 1: Write failing integration tests**

Cover seed counts and launch policies, authenticated catalog, unauthenticated 401, team/BUDGET progress, pack-open event progress, incomplete/complete/idempotent claim, and admin dry-run.

- [ ] **Step 2: Run targeted tests to verify red**

Run: `cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage1IntegrationTest"`

Expected: failure because Stage 1 tables/endpoints/services are not implemented yet.

### Task 2: Schema And Seed

**Files:**
- Create: `polemica-fantasy-backend/src/main/resources/db/migration/V46__user_achievements.sql`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/entity/FantasyTeam.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/entity/Series.kt`

- [ ] **Step 1: Add achievement tables and timestamp columns**

Create `achievement_definition`, `achievement_reward`, `user_achievement`, `user_cosmetic_unlock`, and `user_card_pack_open_event`; add `fantasy_team.created_at` and `series.finalized_at`.

- [ ] **Step 2: Seed 42 definitions**

Seed 30 enabled visible Stage 1 definitions and 12 disabled dormant definitions; all use `FROM_ACHIEVEMENTS_LAUNCH`, enabled rows get `tracking_started_at = now()`, disabled rows get `NULL`.

### Task 3: Backend Model And Services

**Files:**
- Create entity/repository/DTO/service/controller files under existing backend packages.
- Modify domain services to publish achievement events after successful transactions.

- [ ] **Step 1: Implement entities/repositories/DTOs/controllers**

Expose `GET /api/v1/achievements`, `POST /api/v1/achievements/{code}/claim`, and `POST /api/v1/admin/achievements/backfill/dry-run`.

- [ ] **Step 2: Implement evaluators and post-commit listener**

Support participation, BUDGET, results, collection, and packs. Treat disabled/null tracking start as zero progress and keep `completed_at` sticky.

- [ ] **Step 3: Implement lock-safe claim**

Upsert then pessimistically lock `(telegram_user_id, achievement_id)`, recompute progress, reject incomplete, and issue rewards once with `ACHIEVEMENT_REWARD`.

### Task 4: TMA Minimal UI

**Files:**
- Create: `polemica-fantasy-webapp/src/api/achievements.ts`
- Create: `polemica-fantasy-webapp/src/pages/AchievementsPage.tsx`
- Modify: `polemica-fantasy-webapp/src/api/types.ts`
- Modify: `polemica-fantasy-webapp/src/App.tsx`
- Modify: `polemica-fantasy-webapp/src/index.css`

- [ ] **Step 1: Add API client and types**

Use query key `['achievements']`; claim mutation invalidates `['achievements']` and existing `['me']`.

- [ ] **Step 2: Add route and minimal list/claim UI**

Render visible categories, progress/state, rewards, and claim buttons.

### Task 5: Verification And Memory

**Files:**
- Append to `memory-bank/activeContext.md`
- Update relevant achievement progress line in `memory-bank/progress.md`

- [ ] **Step 1: Run targeted tests and compile/build checks**

Run targeted achievement tests, `compileKotlin compileTestKotlin`, TMA `npm run build`, and `./scripts/codex-check.sh quick` if feasible.

- [ ] **Step 2: Append memory notes**

Only append/update the relevant achievement status, preserving pre-existing dirty doc content.
