# Achievement Reward Rework MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or equivalent agent-by-agent execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** implement the practical backend/admin/TMA foundation for the achievement reward rework in `docs/features/DESIGN-ACHIEVEMENTS-REWARD-REWORK.md`.

**Architecture:** Keep existing `achievement_reward` rows as the reward catalog and use `metadata` for card reward parameters. Add a pending card-choice state for `CARD_CHOICE_ROLL`; `RANDOM_CARD` claims immediately. Keep already claimed `user_achievement.reward_snapshot` stable and append new migrations instead of editing historical migrations only.

**Tech Stack:** Kotlin/Spring Boot/JPA/JdbcTemplate/Flyway, PostgreSQL JSONB, React 19 + TypeScript + TanStack Query, Ant Design admin.

---

## Scope Decisions

- Implement reward types `RANDOM_CARD` and `CARD_CHOICE_ROLL`.
- Do not implement full `CARD_CHOICE_CATALOG` picker in this MVP; keep it documented for a later phase.
- Remove `CARD_SKIN_UNLOCK` from active backend/admin/TMA reward types. Card skins are only applied to generated achievement-edition cards through `user_card.card_skin_id`.
- Implement `CARD_CHOICE_ROLL` as a two-step pending choice flow, not auto-pick. Auto-pick would contradict `2 из 5` and make later UX migration messy.
- Add migration seed updates only for achievement codes whose condition types already exist. New complex challenge achievements from the draft stay documented until their condition evaluators are implemented.

## Reward Metadata Contract

`achievement_reward.metadata` is a JSON object:

```json
{
  "rarity": "RARE",
  "count": 2,
  "options": 5,
  "skinCode": "common_challenge_edition",
  "source": "ACTIVE_PACKS"
}
```

- `rarity`: required for card rewards, one of `COMMON | RARE | EPIC | LEGENDARY`.
- `count`: number of cards granted or selected. Required, positive.
- `options`: number of roll options for `CARD_CHOICE_ROLL`. Required, `options >= count`.
- `skinCode`: optional `card_skin.code` to assign to generated `user_card`.
- `source`: MVP supports `ACTIVE_PACKS` only. `LEGENDARY` with `ACTIVE_PACKS` is rejected because packs cannot generate LEGENDARY.

## Task 1: Backend Reward Types And DTOs

**Files:**
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/user/response/AchievementDtos.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/admin/request/AchievementAdminRequests.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/admin/response/AchievementAdminDtos.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/AchievementAdminService.kt`
- Modify: `polemica-fantasy-admin/src/api/types.ts`
- Modify: `polemica-fantasy-admin/src/pages/AchievementsPage.tsx`
- Modify: `polemica-fantasy-webapp/src/api/types.ts`
- Modify: `polemica-fantasy-webapp/src/pages/AchievementsPage.tsx`

- [ ] Add `metadata: String?` to user `AchievementRewardDto` and TS `AchievementReward`.
- [ ] Add card reward result DTOs to `AchievementClaimResultDto`: generated cards and pending choices.
- [ ] Add admin/TMA reward type labels for `RANDOM_CARD` and `CARD_CHOICE_ROLL`.
- [ ] Remove `CARD_SKIN_UNLOCK` from backend `cosmeticRewardTypes`, admin `AchievementRewardType`, admin reward type options, and TMA reward formatting.
- [ ] Update admin validation so card reward types require JSON object metadata with valid `rarity`, `count`, and, for choice roll, `options`.

## Task 2: Backend Card Reward Service

**Files:**
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/AchievementCardRewardService.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/CardPackService.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/entity/CardAcquisitionType.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/CardSkinRepository.kt`

- [ ] Expose pack-eligible player pool generation from `CardPackService` through a public method that returns eligible `FantasyPlayer` values for active auto-generated packs.
- [ ] Create `AchievementCardRewardService` that can generate `UserCard` instances for `RANDOM_CARD` using active pack eligibility and `findOrCreateCardTemplateForPerks`.
- [ ] For `CARD_CHOICE_ROLL`, generate stable option cards as lightweight DTO options, not `user_card` rows, until the user chooses.
- [ ] Add `CardAcquisitionType.ACHIEVEMENT_REWARD`.
- [ ] If `skinCode` is present, resolve `CardSkin` by code and attach it to generated `UserCard`.
- [ ] Reject `LEGENDARY` rewards with `source = ACTIVE_PACKS`; reserve LEGENDARY choice for a later catalog/manual source.

## Task 3: Pending Choice Persistence And Claim API

**Files:**
- Create migration: `polemica-fantasy-backend/src/main/resources/db/migration/V49__achievement_card_rewards.sql`
- Create entity/repository if using JPA, or use JdbcTemplate only:
  - `user_achievement_card_choice`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/AchievementClaimService.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/controller/user/AchievementController.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserAchievementRepository.kt`

- [ ] Add table for pending choices keyed by `(telegram_user_id, achievement_id, reward_id)` with JSONB `options`, `required_count`, `selected_user_card_ids`, `created_at`, `claimed_at`.
- [ ] `POST /api/v1/achievements/{code}/claim`:
  - keeps old immediate behavior for FANTIKI/cosmetics/RANDOM_CARD;
  - generates or returns pending choices for `CARD_CHOICE_ROLL`;
  - does not set `user_achievement.claimed_at` until all choice rewards are selected;
  - remains idempotent on repeat calls.
- [ ] Add `POST /api/v1/achievements/{code}/choices/{rewardId}/select` with selected option ids.
- [ ] Selection endpoint creates selected `UserCard` rows, writes ownership history, marks choice row claimed, and completes `user_achievement.claimed_at` when all rewards are resolved.
- [ ] `reward_snapshot` must include reward type, metadata, generated `user_card_id`s, selected option ids, and cosmetic unlocks.

## Task 4: Migration Seed Rework For Existing Conditions

**Files:**
- Modify/create in `V49__achievement_card_rewards.sql`
- Do not edit already-run migration semantics except where fresh DB seed must match the same data.

- [ ] Update `achievement_reward` rows for existing codes from the rework table where condition types already exist. Do not write any `LEGENDARY` card reward with `source = ACTIVE_PACKS`; keep those as non-card cosmetics/fantiki or defer them until `CARD_CHOICE_CATALOG`/manual source exists.
- [ ] Add new threshold definitions for existing condition types. For thresholds whose draft reward is `CARD_CHOICE_ROLL` LEGENDARY, seed the achievement definition but use a deferred non-card reward or leave it disabled until a non-pack LEGENDARY source is implemented.
  - `team_submit_50`, `team_submit_100`, `team_submit_150`
  - `dual_league_25`, `dual_league_50`
  - `budget_team_50`, `budget_team_100`, `budget_team_150`
  - `budget_win_3`, `budget_win_10`, `budget_win_25`
  - `budget_top10_15`, `budget_top10_30`, `budget_top10_50`
  - `series_win_25`, `series_win_50`
  - `top3_15`, `top3_30`, `top3_50`
  - `top10_25`, `top10_50`, `top10_100`
  - `top_quarter_25`, `top_quarter_50`, `top_quarter_100`
  - `cards_total_200`, `cards_total_350`
  - `pack_open_50`, `pack_open_100`, `pack_open_150`
  - `legendary_upgrade_10`
  - marketplace buy/sell/counterparty 15/30 and public profile views 25
- [ ] Do not add `LEGENDARY from pack` achievement.
- [ ] Do not add complex secret/challenge rows until their condition types are implemented.

## Task 5: TMA Claim UX

**Files:**
- Modify: `polemica-fantasy-webapp/src/api/achievements.ts`
- Modify: `polemica-fantasy-webapp/src/api/types.ts`
- Modify: `polemica-fantasy-webapp/src/pages/AchievementsPage.tsx`
- Modify: `polemica-fantasy-webapp/src/index.css`

- [ ] Show card reward summaries: `карта RARE`, `выбор 2 из 5 RARE`, `рамка`, `бейдж`, `косметика`.
- [ ] After claim returns pending choices, render option cards inline in the achievement row or a compact modal.
- [ ] Let the user select exactly `requiredCount` options and submit the choice endpoint.
- [ ] Invalidate `['achievements']`, `['me']`, and cards collection queries after claim/selection.

## Task 6: Tests And Verification

**Files:**
- Modify: `polemica-fantasy-backend/src/test/kotlin/io/github/mralex1810/fantasy/AchievementStage1IntegrationTest.kt`
- Modify: `polemica-fantasy-backend/src/test/kotlin/io/github/mralex1810/fantasy/AdminApiIntegrationTest.kt` if admin validation is covered there.

- [ ] Test `RANDOM_CARD` claim creates one `user_card`, records ownership, and repeat claim does not create another.
- [ ] Test `CARD_CHOICE_ROLL` claim creates stable pending options, selection creates selected cards, repeat selection fails or is idempotent without duplicates.
- [ ] Test admin rejects `CARD_SKIN_UNLOCK` and accepts card reward metadata.
- [ ] Run backend targeted tests:
  - `cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage1IntegrationTest"`
  - admin validation targeted test if added.
- [ ] Run frontend builds:
  - `cd polemica-fantasy-webapp && npm run build`
  - `cd polemica-fantasy-admin && npm run build`
- [ ] Run `./scripts/codex-check.sh quick` before handoff if local dependencies are available.

## Risks

- Choice rewards must be idempotent under repeated claim/select calls.
- Active pack eligibility may be empty; claim should return a clear 400 rather than partial rewards.
- Already claimed achievements must keep their old `reward_snapshot`; migration must not try to reissue rewards.
- `LEGENDARY` card rewards need a non-pack source; do not implement them through active pack generation.
