# Profile Cosmetics

> Status: accepted discovery, ready for delivery planning.
> Related: `DESIGN-ACHIEVEMENTS.md`, `DESIGN-ACHIEVEMENTS-REWARD-REWORK.md`.

## Context

Achievement rewards already support cosmetic reward types:

- `PROFILE_FRAME`
- `BADGE_STYLE`
- `COSMETIC_UNLOCK`

`PROFILE_FRAME` works end to end. A claimed achievement writes an unlock to
`user_cosmetic_unlock`, `/api/v1/me/profile-customization` exposes unlocked
frames, users can select a frame, and public profiles plus leaderboard names
render the selected frame.

`BADGE_STYLE` is currently not a separate apply-flow. The user-facing showcase
already lets users select claimed achievements as featured profile badges. For
V1 this is enough.

`COSMETIC_UNLOCK` is the missing piece. Claims write rows to
`user_cosmetic_unlock`, and achievement reward seeds already contain title and
accent-like codes such as `title_marathon_manager`, `budget_top_accent`, and
`scout_title`. However, there is no catalog, DTO, selected field, or TMA UI that
lets a user apply these unlocks to the public profile.

## Decision

Complete the `COSMETIC_UNLOCK` flow as profile cosmetics:

```text
achievement claim -> user_cosmetic_unlock -> profile customization options
-> selected profile cosmetic -> public profile display
```

This is a visual-only feature. It must not affect scoring, leaderboard order,
card value, card uses, team limits, marketplace rules, or rewards.

## Product Scope

V1 supports three profile cosmetic kinds:

| Kind | Purpose | Initial source |
|------|---------|----------------|
| `TITLE` | Short public title under the user's display name | `*_title`, `title_*` reward codes |
| `ACCENT` | Visual accent for the public profile showcase block | `*_accent`, selected background-like reward codes |
| `BACKGROUND` | Reserved visual background family for the showcase | optional seed only if concrete codes exist |

The public profile remains readable if no cosmetics are selected. Cosmetics are
opt-in: unlocking a cosmetic does not automatically apply it.

## Non-Goals

- No new achievement condition types.
- No changes to existing achievement progression or claim rules.
- No score, economy, card, or marketplace advantages.
- No separate card skin unlock flow; card skins remain tied to `user_card`.
- No broad redesign of the public profile.
- No global leaderboard row decorations beyond the existing profile frame name
  treatment.

## User Flow

1. User claims an achievement that grants `COSMETIC_UNLOCK`.
2. Claim result includes the unlock and achievements UI points to profile
   customization.
3. User opens **Витрина профиля**.
4. User selects a title and/or showcase accent from unlocked options.
5. User saves.
6. Own and public profile render the selected title/accent.

If a user has no unlocked cosmetics for a kind, that section is hidden.

## Data Model

Add a catalog table:

```sql
CREATE TABLE profile_cosmetic (
    code VARCHAR(96) PRIMARY KEY,
    kind VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    style_token VARCHAR(96),
    display_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_profile_cosmetic_kind
        CHECK (kind IN ('TITLE', 'ACCENT', 'BACKGROUND'))
);
```

Extend `user_profile_customization`:

```sql
ALTER TABLE user_profile_customization
    ADD COLUMN profile_title_code VARCHAR(96) REFERENCES profile_cosmetic(code) ON DELETE SET NULL,
    ADD COLUMN profile_accent_code VARCHAR(96) REFERENCES profile_cosmetic(code) ON DELETE SET NULL,
    ADD COLUMN profile_background_code VARCHAR(96) REFERENCES profile_cosmetic(code) ON DELETE SET NULL;
```

Seed `profile_cosmetic` from an explicit reviewed mapping. Do not rely only on
substring heuristics in production migrations.

Initial `TITLE` rows:

| Code | Name | Style token |
|------|------|-------------|
| `budget_champion_title` | Бюджетный чемпион | `budget_champion` |
| `budget_marathon_title` | Бюджетный марафонец | `budget_marathon` |
| `budget_winner_title` | Победитель бюджета | `budget_winner` |
| `collection_title` | Коллекционер | `collection` |
| `crafter_title` | Мастер крафта | `crafter` |
| `dynasty_title` | Династия | `dynasty` |
| `market_network_title` | Рыночный связной | `market_network` |
| `market_seller_title` | Продавец рынка | `market_seller` |
| `podium_title` | На пьедестале | `podium` |
| `scout_title` | Скаут | `scout` |
| `series_winner_title` | Победитель серии | `series_winner` |
| `title_elite_manager` | Элитный менеджер | `elite_manager` |
| `title_marathon_manager` | Марафонец составов | `marathon_manager` |

Initial `ACCENT` rows:

| Code | Name | Style token |
|------|------|-------------|
| `budget_top_accent` | Бюджетный топ | `budget_top` |
| `dual_strategy_accent` | Две стратегии | `dual_strategy` |
| `top10_accent` | Верхняя группа | `top10` |

The migration should also preserve compatibility with already claimed or
admin-created unlock rows:

- seed all reviewed current reward codes above;
- insert fallback catalog rows for any existing
  `user_cosmetic_unlock.cosmetic_type = 'COSMETIC_UNLOCK'` code that is not in
  the reviewed list;
- classify fallback rows conservatively as `TITLE` when the code contains
  `title`, as `ACCENT` when it contains `accent` or `background`, and otherwise
  as `TITLE` with a humanized fallback name.

This fallback is a compatibility bridge, not the main product catalog. New
admin-created `COSMETIC_UNLOCK` codes should be added to `profile_cosmetic`.

Future mapping rule:

- codes containing `title` -> normally `TITLE`;
- codes containing `accent` -> normally `ACCENT`;
- background-like codes -> `BACKGROUND` only when the visual treatment is
  implemented in TMA.

Existing rows in `user_cosmetic_unlock` must start working after migration
without requiring users to reclaim achievements.

## Backend Contract

Extend `ProfileCustomizationDto`:

```json
{
  "profileFrameCode": "dynasty",
  "unlockedFrames": [],
  "profileTitleCode": "series_winner_title",
  "profileAccentCode": "top10_accent",
  "profileBackgroundCode": null,
  "unlockedCosmetics": {
    "titles": [],
    "accents": [],
    "backgrounds": []
  },
  "featuredAchievementCodes": [],
  "availableFeaturedAchievements": [],
  "favoriteBadgeFantasyPlayerId": null,
  "favoriteBadgePlayerOptions": []
}
```

Add DTO shape:

```json
{
  "code": "series_winner_title",
  "kind": "TITLE",
  "name": "Серийный победитель",
  "description": null,
  "styleToken": "series_winner"
}
```

Extend `UpdateProfileCustomizationRequest`:

```json
{
  "profileFrameCode": "dynasty",
  "profileTitleCode": "series_winner_title",
  "profileAccentCode": "top10_accent",
  "profileBackgroundCode": null,
  "featuredAchievementCodes": [],
  "favoriteBadgeFantasyPlayerId": null
}
```

Validation:

- selected frame must still be an unlocked `PROFILE_FRAME`;
- selected title/accent/background must exist in `profile_cosmetic`;
- selected cosmetic must be `enabled = true`;
- selected cosmetic kind must match the target field;
- selected cosmetic must be unlocked in `user_cosmetic_unlock` with
  `cosmetic_type = 'COSMETIC_UNLOCK'` and the same code.

Extend public profile DTO with selected cosmetics:

```json
{
  "profileFrame": null,
  "profileTitle": null,
  "profileAccent": null,
  "profileBackground": null
}
```

Disabled or missing selected cosmetics should not break public profile reads.
The response must expose them as `null`; cleanup can be deferred to a later
update. `/api/v1/me/profile-customization` should also return `null` selected
codes when a saved code is missing, disabled, wrong-kind, or no longer unlocked.

Request fields for title/accent/background should be nullable with defaults, so
older clients that omit them do not break save requests. The frontend should
continue to send all fields it knows about in one `PUT`.

## TMA UX

Update `/profile-customization`:

- keep the existing frame picker;
- add a title picker if unlocked titles exist;
- add an accent picker if unlocked accents exist;
- add a background picker only if seeded and styled;
- keep featured achievement selection and favorite player badge selection;
- save all selected values in one `PUT`.

Public profile:

- render selected title under the display name;
- apply selected accent/background only to the showcase block;
- keep readable defaults if no cosmetics are selected or if a style token is not
  supported by the frontend allowlist.

Achievements page:

- after claim with `COSMETIC_UNLOCK`, invalidate `profile-customization` cache
  and provide a path to **Настроить витрину**;
- enriched reward labels based on `profile_cosmetic` catalog are deferred unless
  they fall out naturally from the backend slice.

## Admin UX

Minimum V1:

- keep the current achievement reward editor;
- add backend validation that `COSMETIC_UNLOCK` reward codes exist in
  `profile_cosmetic`, or explicitly document/admin-warn that unregistered codes
  unlock but cannot be applied;
- expose catalog values enough that admins do not need to guess codes.

Preferred follow-up:

- add a small admin catalog view for `profile_cosmetic`;
- allow editing `name`, `description`, `display_order`, and `enabled`;
- keep `code` and `kind` stable after creation.

Admin catalog CRUD is not required for the first backend/TMA vertical slice.

## Acceptance Criteria

- A user who already has `COSMETIC_UNLOCK` rows can select those cosmetics after
  the migration.
- A user cannot select another user's cosmetic.
- A user cannot select a cosmetic of the wrong kind in a field.
- Disabled or unknown selected cosmetics do not crash profile reads.
- Selected title is visible on public profile.
- Selected accent changes the showcase visual without harming readability.
- Existing `PROFILE_FRAME` and featured achievement flows keep working.
- Frontend types match backend DTOs.

## Tests

Backend:

- extend profile showcase integration tests for unlocked cosmetics;
- validate reject cases: not unlocked, wrong kind, disabled/unknown;
- verify public profile returns selected cosmetics;
- verify existing frame and featured achievements still work.

TMA:

- `npm run build`;
- smoke `/profile-customization` with no cosmetics, one title, one accent;
- smoke public profile with selected cosmetics.

Cross-module:

- run `./scripts/codex-check.sh quick` before handoff if dependencies are
  installed.

## Delivery Plan

Recommended vertical slice:

1. `V67` migration with catalog, selected fields, and seed from existing
   `COSMETIC_UNLOCK` reward codes.
2. Backend DTO/service/repository support for title/accent selection.
3. Public profile DTO support for selected title/accent.
4. TMA customization UI for title/accent.
5. Public profile title/accent rendering.
6. Admin catalog polish only after the user path is stable.

Worker split after the vertical slice:

- backend: entities, repositories, DTOs, service validation, migration, tests;
- TMA: API types/client usage, customization page, public profile rendering,
  CSS allowlist;
- admin: optional catalog/read-only validation view;
- QA: local smoke and regression around existing frame/featured flows.
