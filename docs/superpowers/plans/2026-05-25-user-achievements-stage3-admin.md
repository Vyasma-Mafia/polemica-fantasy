# User Achievements Stage 3 Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Stage 3 of `docs/features/DESIGN-ACHIEVEMENTS.md`: admin achievement catalog management, reward editing, aggregate analytics, and dry-run visibility.

**Architecture:** Extend the existing Stage 1 admin achievement slice instead of adding new achievement evaluators. The backend exposes read/update DTOs for definitions, rewards, and persisted aggregate stats; the admin frontend adds a dedicated Ant Design page that consumes those contracts through a new admin API client.

**Tech Stack:** Kotlin/Spring Boot/JPA/PostgreSQL, MockMvc integration tests with Testcontainers, React 19/Vite/TanStack Query/React Router/Ant Design 6 in `polemica-fantasy-admin`.

---

## Scope Decisions

- Stage 3 is **Admin only**. Do not add marketplace/social/legendary achievement evaluators, product-event sources, schedulers, listeners, or new progress triggers in this stage.
- Dormant marketplace/social/legendary definitions seeded by Stage 1 must be visible and editable in admin, but remain disabled unless an admin explicitly enables them through the edit form.
- Use `PATCH /api/v1/admin/achievements/{code}` for Stage 3 updates, matching the user request. The design doc table says `PUT`, but this stage uses `PATCH`.
- Admin update must not accept or mutate `code`, `conditionType`, `historyPolicy`, `targetValue`, `category`, `chainGroup`, or `chainLevel`. These are system/product-contract fields and changing them through V1 admin can corrupt evaluator semantics.
- Reward editing is full replacement inside the `PATCH` request. This keeps Stage 3 small and transactionally clear. Editing rewards affects future claims only; existing `user_achievement.reward_snapshot` rows remain untouched.
- Reward replacement uses the bulk-delete path safely: update loads the definition without fetching its `rewards` collection, validates all replacement rewards first, bulk-deletes existing rewards, flushes, inserts replacement rows, clears the persistence context, then reloads with rewards for the response. Do not bulk-delete rewards after loading `AchievementDefinition.rewards` in the same persistence context.
- Analytics in the list endpoint come from persisted `user_achievement` rows. Do not recompute every user's live progress for the table endpoint. Live economic evaluation remains the existing dry-run endpoint.
- Memory-bank updates are not part of this planning task. The implementer updates `memory-bank/activeContext.md` and `memory-bank/progress.md` only after code implementation and verification.

## Backend API Contract

### `GET /api/v1/admin/achievements`

Response DTO:

```kotlin
data class AchievementAdminListResponseDto(
    val achievements: List<AchievementAdminDefinitionDto>,
)

data class AchievementAdminDefinitionDto(
    val code: String,
    val category: String,
    val conditionType: String,
    val historyPolicy: String,
    val targetValue: Long,
    val chainGroup: String?,
    val chainLevel: Int?,
    val title: String,
    val description: String?,
    val iconUrl: String?,
    val accentColor: String?,
    val rarity: String,
    val visibility: String,
    val enabled: Boolean,
    val trackingStartedAt: Instant?,
    val displayOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val rewards: List<AchievementAdminRewardDto>,
    val stats: AchievementAdminStatsDto,
)

data class AchievementAdminRewardDto(
    val type: String,
    val amount: Long?,
    val code: String?,
    val metadata: String?,
    val displayOrder: Int,
)

data class AchievementAdminStatsDto(
    val completedUsers: Long,
    val claimedUsers: Long,
    val unclaimedUsers: Long,
    val totalProgress: Long,
    val averageProgress: Double,
    val nearCompletionUsers: Long,
    val lastCompletedAt: Instant?,
)
```

Ordering: `achievement_definition.display_order ASC, id ASC`; rewards sorted by `display_order ASC, id ASC`.

### `PATCH /api/v1/admin/achievements/{code}`

Request DTO:

```kotlin
data class UpdateAchievementAdminRequest(
    val title: String,
    val description: String?,
    val iconUrl: String?,
    val accentColor: String?,
    val rarity: String,
    val visibility: String,
    val enabled: Boolean,
    val displayOrder: Int,
    val rewards: List<UpdateAchievementAdminRewardRequest>,
)

data class UpdateAchievementAdminRewardRequest(
    val type: String,
    val amount: Long?,
    val code: String?,
    val metadata: String?,
    val displayOrder: Int,
)
```

Response: `AchievementAdminDefinitionDto`.

Validation rules:

- `title`: trim, non-empty, max 255.
- `description`: trim to nullable, max 4096 when present.
- `iconUrl`: trim to nullable, max 2048 when present.
- `accentColor`: nullable; when present, match `#[0-9A-Fa-f]{6}` and max 32.
- `rarity`: one of `COMMON`, `RARE`, `EPIC`, `LEGENDARY`.
- `visibility`: one of `PUBLIC`, `HIDDEN`, `SECRET`, `PRIVATE`.
- `displayOrder`: `>= 0`.
- `rewards`: max 10 rows; each row `displayOrder >= 0`; duplicate `displayOrder` rejected to keep admin ordering deterministic.
- Reward `type = FANTIKI`: `amount` required and `> 0`; `code` must be null/blank.
- Reward `type in PROFILE_FRAME, CARD_SKIN_UNLOCK, COSMETIC_UNLOCK, BADGE_STYLE`: `code` required, trim non-empty, max 96; `amount` must be null.
- Unsupported reward types rejected with `400 BAD_REQUEST`.
- `metadata`: nullable; when present, trim and validate as JSON object with Jackson `ObjectMapper.readTree(metadata).isObject`.

`tracking_started_at` invariant:

- If a definition transitions from `enabled = false` to `enabled = true` and `trackingStartedAt == null`, set `trackingStartedAt = Instant.now()`.
- If a definition transitions from `enabled = true` to `enabled = false`, preserve the existing `trackingStartedAt`.
- If a definition is already disabled and still has `trackingStartedAt = null`, keep it null.
- Never clear a non-null `trackingStartedAt` through this endpoint.

### Existing Dry-Run

Keep the existing `POST /api/v1/admin/achievements/backfill/dry-run` contract:

```kotlin
data class AchievementDryRunResponseDto(
    val instantCompleted: Long,
    val instantFantikiLiability: Long,
    val rows: List<AchievementDryRunRowDto>,
)

data class AchievementDryRunRowDto(
    val code: String,
    val enabled: Boolean,
    val instantCompleted: Long,
    val instantFantikiLiability: Long,
)
```

The admin page should call and display this endpoint. Do not implement `POST /backfill`, job status, or user repair endpoints in Stage 3.

## Files Map

Backend create:

- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/admin/request/AchievementAdminRequests.kt` - update request DTOs for metadata and reward replacement.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/AchievementRewardRepository.kt` - bulk delete and save support for full reward replacement.
- `polemica-fantasy-backend/src/test/kotlin/io/github/mralex1810/fantasy/AchievementStage3AdminIntegrationTest.kt` - red-first Stage 3 admin API tests.

Backend modify:

- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/controller/admin/AchievementAdminController.kt` - add `GET ""` and `PATCH "/{code}"`.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/AchievementAdminService.kt` - list, map, validate, update, reward replacement, tracking invariant.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/admin/response/AchievementAdminDtos.kt` - add list/detail/reward/stats DTOs next to dry-run DTOs.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/AchievementDefinitionRepository.kt` - add admin stats projection query if using repository projections.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/AchievementAdminService.kt` - inject `EntityManager` for safe persistence-context clearing after reward bulk delete.

Admin frontend create:

- `polemica-fantasy-admin/src/api/achievements.ts` - admin achievements API client.
- `polemica-fantasy-admin/src/pages/AchievementsPage.tsx` - table, filters, edit drawer, dry-run card.

Admin frontend modify:

- `polemica-fantasy-admin/src/api/types.ts` - Stage 3 admin achievement DTOs and request types.
- `polemica-fantasy-admin/src/App.tsx` - route `/achievements`.
- `polemica-fantasy-admin/src/layout/AdminLayout.tsx` - menu entry.

Do not modify TMA files in Stage 3 unless a backend DTO break is discovered during verification. Stage 3 should not change user-facing achievement APIs.

---

### Task 1: Backend Red Tests

**Files:**

- Create: `polemica-fantasy-backend/src/test/kotlin/io/github/mralex1810/fantasy/AchievementStage3AdminIntegrationTest.kt`

- [ ] **Step 1: Write failing integration tests**

Create the test class with the same annotations and helper style as `AchievementStage1IntegrationTest`:

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AchievementStage3AdminIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Order(1)
    fun `admin dry run remains available and launch baseline remains zero in untouched seed`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(post("/api/v1/admin/achievements/backfill/dry-run").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.instantCompleted").value(0))
            .andExpect(jsonPath("$.instantFantikiLiability").value(0))
            .andExpect(jsonPath("$.rows", hasSize<Any>(30)))
    }

    @Test
    @Order(2)
    fun `GET admin achievements requires basic auth and returns all definitions rewards and stats`() {
        mockMvc.perform(get("/api/v1/admin/achievements"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(get("/api/v1/admin/achievements").header("Authorization", basicAuth("admin", "test-admin-secret")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.achievements", hasSize<Any>(42)))
            .andExpect(jsonPath("$.achievements[*].code", hasItem("team_submit_1")))
            .andExpect(jsonPath("$.achievements[*].code", hasItem("market_buy_1")))
            .andExpect(jsonPath("$.achievements[?(@.code == 'team_submit_1')][0].conditionType").value("TEAMS_SUBMITTED"))
            .andExpect(jsonPath("$.achievements[?(@.code == 'team_submit_1')][0].targetValue").value(1))
            .andExpect(jsonPath("$.achievements[?(@.code == 'team_submit_1')][0].rewards[0].type").value("FANTIKI"))
            .andExpect(jsonPath("$.achievements[?(@.code == 'market_buy_1')][0].enabled").value(false))
            .andExpect(jsonPath("$.achievements[?(@.code == 'market_buy_1')][0].trackingStartedAt").value(nullValue()))
            .andExpect(jsonPath("$.achievements[?(@.code == 'team_submit_1')][0].stats.completedUsers").isNumber)
            .andExpect(jsonPath("$.achievements[?(@.code == 'team_submit_1')][0].stats.claimedUsers").isNumber)
            .andExpect(jsonPath("$.achievements[?(@.code == 'team_submit_1')][0].stats.unclaimedUsers").isNumber)
    }

    @Test
    @Order(3)
    fun `PATCH admin achievement edits metadata and full reward list`() {
        val auth = basicAuth("admin", "test-admin-secret")

        mockMvc.perform(
            patch("/api/v1/admin/achievements/team_submit_5")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title":"First edited team",
                      "description":"Edited admin description",
                      "iconUrl":"https://example.com/icon.png",
                      "accentColor":"#12ABef",
                      "rarity":"RARE",
                      "visibility":"HIDDEN",
                      "enabled":true,
                      "displayOrder":12,
                      "rewards":[
                        {"type":"FANTIKI","amount":33,"code":null,"metadata":null,"displayOrder":10},
                        {"type":"PROFILE_FRAME","amount":null,"code":"admin_frame","metadata":"{\"source\":\"stage3\"}","displayOrder":20}
                      ]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("team_submit_5"))
            .andExpect(jsonPath("$.title").value("First edited team"))
            .andExpect(jsonPath("$.rarity").value("RARE"))
            .andExpect(jsonPath("$.visibility").value("HIDDEN"))
            .andExpect(jsonPath("$.rewards", hasSize<Any>(2)))
            .andExpect(jsonPath("$.rewards[0].type").value("FANTIKI"))
            .andExpect(jsonPath("$.rewards[0].amount").value(33))
            .andExpect(jsonPath("$.rewards[1].type").value("PROFILE_FRAME"))
            .andExpect(jsonPath("$.rewards[1].code").value("admin_frame"))

        assertSqlLong("SELECT COUNT(*) FROM achievement_reward ar JOIN achievement_definition d ON d.id = ar.achievement_id WHERE d.code = 'team_submit_5'", 2)
    }

    @Test
    @Order(4)
    fun `PATCH enabling dormant achievement sets trackingStartedAt once and disabling preserves it`() {
        val auth = basicAuth("admin", "test-admin-secret")
        assertSqlLong("SELECT COUNT(*) FROM achievement_definition WHERE code = 'market_buy_1' AND enabled = FALSE AND tracking_started_at IS NULL", 1)

        patchAchievement(auth, "market_buy_1", enabled = true)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.trackingStartedAt").isString)

        val firstTrackingStartedAt = jdbcTemplate.queryForObject(
            "SELECT tracking_started_at::text FROM achievement_definition WHERE code = 'market_buy_1'",
            String::class.java,
        )!!

        patchAchievement(auth, "market_buy_1", enabled = false)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.trackingStartedAt").isString)

        val secondTrackingStartedAt = jdbcTemplate.queryForObject(
            "SELECT tracking_started_at::text FROM achievement_definition WHERE code = 'market_buy_1'",
            String::class.java,
        )!!
        assertThat(secondTrackingStartedAt).isEqualTo(firstTrackingStartedAt)

        patchAchievement(auth, "market_buy_1", enabled = true)
            .andExpect(status().isOk)
        val thirdTrackingStartedAt = jdbcTemplate.queryForObject(
            "SELECT tracking_started_at::text FROM achievement_definition WHERE code = 'market_buy_1'",
            String::class.java,
        )!!
        assertThat(thirdTrackingStartedAt).isEqualTo(firstTrackingStartedAt)

        val enabledTrackingBefore = jdbcTemplate.queryForObject(
            "SELECT tracking_started_at::text FROM achievement_definition WHERE code = 'team_submit_1'",
            String::class.java,
        )!!
        patchAchievement(auth, "team_submit_1", enabled = true)
            .andExpect(status().isOk)
        val enabledTrackingAfter = jdbcTemplate.queryForObject(
            "SELECT tracking_started_at::text FROM achievement_definition WHERE code = 'team_submit_1'",
            String::class.java,
        )!!
        assertThat(enabledTrackingAfter).isEqualTo(enabledTrackingBefore)

        jdbcTemplate.update("UPDATE achievement_definition SET enabled = FALSE WHERE code = 'team_submit_5'")
        val disabledNonNullBefore = jdbcTemplate.queryForObject(
            "SELECT tracking_started_at::text FROM achievement_definition WHERE code = 'team_submit_5'",
            String::class.java,
        )!!
        patchAchievement(auth, "team_submit_5", enabled = false)
            .andExpect(status().isOk)
        val disabledNonNullAfter = jdbcTemplate.queryForObject(
            "SELECT tracking_started_at::text FROM achievement_definition WHERE code = 'team_submit_5'",
            String::class.java,
        )!!
        assertThat(disabledNonNullAfter).isEqualTo(disabledNonNullBefore)
    }

    @Test
    @Order(5)
    fun `PATCH validates system fields are not accepted and reward payloads are strict`() {
        val auth = basicAuth("admin", "test-admin-secret")

        listOf("code", "conditionType", "historyPolicy", "targetValue", "category", "chainGroup", "chainLevel").forEach { field ->
            patchAchievementWithExtraField(auth, "team_submit_15", """"$field":"corrupt"""")
                .andExpect(status().isBadRequest)
        }

        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"FANTIKI","amount":0,"code":null,"metadata":null,"displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"PROFILE_FRAME","amount":null,"code":"","metadata":null,"displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"XP","amount":1,"code":null,"metadata":null,"displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"FANTIKI","amount":10,"code":null,"metadata":"[]","displayOrder":10}]""")
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(6)
    fun `reward edit affects future claims only and preserves claimed reward snapshot`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val first = createTeamFixture(auth, "stage3-claimed-before")
        val firstTma = tmaAuth(first.telegramPlatformId, "Stage3Before")
        createFantasyTeam(firstTma, first.seriesId, "MAIN", first.userCardIds.take(1))

        mockMvc.perform(post("/api/v1/achievements/team_submit_1/claim").header("Authorization", firstTma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantikiDelta").value(10))

        patchAchievementWithRewards(auth, "team_submit_1", """[{"type":"FANTIKI","amount":99,"code":null,"metadata":null,"displayOrder":10}]""")
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/admin/achievements").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.achievements[?(@.code == 'team_submit_1')][0].rewards", hasSize<Any>(1)))
            .andExpect(jsonPath("$.achievements[?(@.code == 'team_submit_1')][0].rewards[0].amount").value(99))

        mockMvc.perform(post("/api/v1/achievements/team_submit_1/claim").header("Authorization", firstTma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantikiDelta").value(0))

        val second = createTeamFixture(auth, "stage3-claimed-after")
        val secondTma = tmaAuth(second.telegramPlatformId, "Stage3After")
        createFantasyTeam(secondTma, second.seriesId, "MAIN", second.userCardIds.take(1))
        mockMvc.perform(post("/api/v1/achievements/team_submit_1/claim").header("Authorization", secondTma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantikiDelta").value(99))

        val firstSnapshot = jdbcTemplate.queryForObject(
            """
            SELECT ua.reward_snapshot
            FROM user_achievement ua
            JOIN achievement_definition d ON d.id = ua.achievement_id
            JOIN telegram_user u ON u.id = ua.telegram_user_id
            WHERE d.code = 'team_submit_1' AND u.telegram_id = ?
            """.trimIndent(),
            String::class.java,
            first.telegramPlatformId,
        )!!
        assertThat(firstSnapshot).contains("10")
        assertThat(firstSnapshot).doesNotContain("99")
    }

}
```

Add local helper methods:

```kotlin
private fun patchAchievement(auth: String, code: String, enabled: Boolean): ResultActions =
    mockMvc.perform(
        patch("/api/v1/admin/achievements/$code")
            .header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "title":"Patched $code",
                  "description":"Patched from test",
                  "iconUrl":null,
                  "accentColor":null,
                  "rarity":"COMMON",
                  "visibility":"PUBLIC",
                  "enabled":$enabled,
                  "displayOrder":999,
                  "rewards":[{"type":"FANTIKI","amount":10,"code":null,"metadata":null,"displayOrder":10}]
                }
                """.trimIndent(),
            ),
    )

private fun patchAchievementWithRewards(auth: String, code: String, rewardsJson: String): ResultActions =
    mockMvc.perform(
        patch("/api/v1/admin/achievements/$code")
            .header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "title":"Patched rewards $code",
                  "description":null,
                  "iconUrl":null,
                  "accentColor":null,
                  "rarity":"COMMON",
                  "visibility":"PUBLIC",
                  "enabled":true,
                  "displayOrder":999,
                  "rewards":$rewardsJson
                }
                """.trimIndent(),
            ),
    )

private fun patchAchievementWithExtraField(auth: String, code: String, extraJsonField: String): ResultActions =
    mockMvc.perform(
        patch("/api/v1/admin/achievements/$code")
            .header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  $extraJsonField,
                  "title":"Patched $code",
                  "description":null,
                  "iconUrl":null,
                  "accentColor":null,
                  "rarity":"COMMON",
                  "visibility":"PUBLIC",
                  "enabled":true,
                  "displayOrder":999,
                  "rewards":[{"type":"FANTIKI","amount":10,"code":null,"metadata":null,"displayOrder":10}]
                }
                """.trimIndent(),
            ),
    )
```

Required test scaffolding to copy locally from `AchievementStage1IntegrationTest`:
- imports for `JsonPath`, `hasItem`, `hasSize`, `nullValue`, `org.assertj.core.api.Assertions.assertThat`, `MockMvcRequestBuilders.get/patch/post/put`, `ResultActions`, `MediaType`, Testcontainers `@Container`, `PostgreSQLContainer`, `@ServiceConnection`, auth crypto helpers (`tmaAuth`, `basicAuth`), `assertSqlLong`, `createTeamFixture`, `createFantasyTeam`, `giveCards`, `createCardTemplate`, `createTournamentPlayer`, and any fixture data class those helpers require.
- the companion object with a PostgreSQL 16 container exactly like the existing achievement integration tests.

Do not refactor Stage 1/2 test files as part of Stage 3. The Stage 3 red test must compile after helpers are copied; the expected red failure is missing endpoint/DTO/service behavior, not missing imports or helper methods.

- [ ] **Step 2: Run targeted tests and verify red**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage3AdminIntegrationTest"
```

Expected: fail because `GET /api/v1/admin/achievements`, `PATCH /api/v1/admin/achievements/{code}`, request DTOs, response DTOs, and reward repository support do not exist yet. If the failure is a Kotlin import typo in the test, fix the test and rerun until it fails because the feature is absent.

---

### Task 2: Backend Admin List Endpoint

**Files:**

- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/admin/response/AchievementAdminDtos.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/AchievementDefinitionRepository.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/AchievementAdminService.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/controller/admin/AchievementAdminController.kt`

- [ ] **Step 1: Add response DTOs**

Append the DTOs from "Backend API Contract" to `AchievementAdminDtos.kt`. Keep existing `AchievementDryRunResponseDto` and `AchievementDryRunRowDto` unchanged for frontend compatibility.

- [ ] **Step 2: Add stats projection**

Add this projection and query to `AchievementDefinitionRepository.kt`:

```kotlin
interface AchievementAdminStatsProjection {
    fun getAchievementId(): Long
    fun getCompletedUsers(): Long
    fun getClaimedUsers(): Long
    fun getUnclaimedUsers(): Long
    fun getTotalProgress(): Long
    fun getAverageProgress(): Double
    fun getNearCompletionUsers(): Long
    fun getLastCompletedAt(): Instant?
}

@Query(
    value = """
    SELECT
        d.id::bigint AS "achievementId",
        COUNT(*) FILTER (WHERE ua.completed_at IS NOT NULL)::bigint AS "completedUsers",
        COUNT(*) FILTER (WHERE ua.claimed_at IS NOT NULL)::bigint AS "claimedUsers",
        COUNT(*) FILTER (WHERE ua.completed_at IS NOT NULL AND ua.claimed_at IS NULL)::bigint AS "unclaimedUsers",
        COALESCE(SUM(ua.progress_value), 0)::bigint AS "totalProgress",
        COALESCE(AVG(ua.progress_value)::double precision, 0.0)::double precision AS "averageProgress",
        COUNT(*) FILTER (
            WHERE ua.completed_at IS NULL
              AND ua.progress_value > 0
              AND ua.progress_value >= CEIL(d.target_value * 0.8)
              AND ua.progress_value < d.target_value
        )::bigint AS "nearCompletionUsers",
        MAX(ua.completed_at) AS "lastCompletedAt"
    FROM achievement_definition d
    LEFT JOIN user_achievement ua ON ua.achievement_id = d.id
    GROUP BY d.id
    """,
    nativeQuery = true,
)
fun aggregateAdminStats(): List<AchievementAdminStatsProjection>
```

- [ ] **Step 3: Implement service mapping**

In `AchievementAdminService`, add:

```kotlin
@Transactional(readOnly = true)
fun listAchievements(): AchievementAdminListResponseDto {
    val statsByAchievementId = achievementDefinitionRepository.aggregateAdminStats()
        .associateBy { it.getAchievementId() }
    val achievements = achievementDefinitionRepository.findAllWithRewards()
        .map { toAdminDto(it, statsByAchievementId[it.id!!]) }
    return AchievementAdminListResponseDto(achievements)
}
```

Add `toAdminDto(definition, stats)` and `toRewardDto(reward)` private helpers. For missing stats use zeros and `lastCompletedAt = null`.

- [ ] **Step 4: Wire controller**

Add:

```kotlin
@GetMapping
fun list(): AchievementAdminListResponseDto = achievementAdminService.listAchievements()
```

- [ ] **Step 5: Run targeted test and verify partial green**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage3AdminIntegrationTest.GET admin achievements requires basic auth and returns all definitions rewards and stats"
```

Expected: this test passes. Other Stage 3 tests still fail because `PATCH` is not implemented.

---

### Task 3: Backend Patch Endpoint And Reward Replacement

**Files:**

- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/admin/request/AchievementAdminRequests.kt`
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/AchievementRewardRepository.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/controller/admin/AchievementAdminController.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/AchievementAdminService.kt`

- [ ] **Step 1: Add request DTOs**

Create `AchievementAdminRequests.kt` with `UpdateAchievementAdminRequest` and `UpdateAchievementAdminRewardRequest` exactly as defined in "Backend API Contract".

Do not add `code`, `conditionType`, `historyPolicy`, `targetValue`, `category`, `chainGroup`, or `chainLevel` properties to the request DTO. To reject unknown JSON fields like `code`, add this unknown-field trap inside both request DTO bodies and import `com.fasterxml.jackson.annotation.JsonAnySetter`:

```kotlin
@JsonAnySetter
fun rejectUnknownField(name: String, value: Any?) {
    throw IllegalArgumentException("Unknown field: $name")
}
```

Spring translates the thrown `IllegalArgumentException` during request-body deserialization into `HttpMessageNotReadableException`, producing `400 BAD_REQUEST`. The integration test must exercise every protected system field listed above.

- [ ] **Step 2: Add reward repository**

Create:

```kotlin
interface AchievementRewardRepository : JpaRepository<AchievementReward, Long> {
    @Modifying
    @Query("DELETE FROM AchievementReward r WHERE r.achievement.id = :achievementId")
    fun deleteByAchievementId(@Param("achievementId") achievementId: Long): Int
}
```

Use repository bulk delete plus `achievementRewardRepository.flush()` before inserting replacement rewards. Inject `jakarta.persistence.EntityManager` into `AchievementAdminService`; update must load the definition through `achievementDefinitionRepository.findByCode(code)` without `JOIN FETCH`, so no stale `rewards` collection is initialized before the bulk delete. Do not rely on the current `AchievementDefinition.rewards` mapping for orphan removal.

- [ ] **Step 3: Implement validation helpers**

In `AchievementAdminService`, add constants:

```kotlin
private val allowedRarities = setOf("COMMON", "RARE", "EPIC", "LEGENDARY")
private val allowedVisibilities = setOf("PUBLIC", "HIDDEN", "SECRET", "PRIVATE")
private val cosmeticRewardTypes = setOf("PROFILE_FRAME", "CARD_SKIN_UNLOCK", "COSMETIC_UNLOCK", "BADGE_STYLE")
private val accentColorRegex = Regex("^#[0-9A-Fa-f]{6}$")
```

Implement validation by throwing `ResponseStatusException(HttpStatus.BAD_REQUEST, "...")` with specific messages:

```kotlin
private fun normalizedText(value: String?, field: String, required: Boolean, max: Int): String? {
    val trimmed = value?.trim()
    if (required && trimmed.isNullOrEmpty()) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field is required")
    }
    if (trimmed != null && trimmed.length > max) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field is too long")
    }
    return trimmed?.takeIf { it.isNotEmpty() }
}
```

Use `ObjectMapper.readTree(metadata)` for metadata validation and require `node.isObject`.

- [ ] **Step 4: Implement update transaction**

Add:

```kotlin
@Transactional
fun updateAchievement(code: String, request: UpdateAchievementAdminRequest): AchievementAdminDefinitionDto {
    val definition = achievementDefinitionRepository.findByCode(code)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement $code not found")
    val wasEnabled = definition.enabled
    val now = Instant.now()

    definition.title = normalizedText(request.title, "title", required = true, max = 255)!!
    definition.description = normalizedText(request.description, "description", required = false, max = 4096)
    definition.iconUrl = normalizedText(request.iconUrl, "iconUrl", required = false, max = 2048)
    definition.accentColor = normalizedAccentColor(request.accentColor)
    definition.rarity = normalizedEnum(request.rarity, "rarity", allowedRarities)
    definition.visibility = normalizedEnum(request.visibility, "visibility", allowedVisibilities)
    definition.enabled = request.enabled
    definition.displayOrder = nonNegative(request.displayOrder, "displayOrder")
    if (!wasEnabled && definition.enabled && definition.trackingStartedAt == null) {
        definition.trackingStartedAt = now
    }
    definition.updatedAt = now

    replaceRewards(definition, request.rewards)
    val saved = achievementDefinitionRepository.save(definition)
    achievementDefinitionRepository.flush()
    entityManager.clear()
    val stats = achievementDefinitionRepository.aggregateAdminStats()
        .associateBy { it.getAchievementId() }[saved.id!!]
    return toAdminDto(achievementDefinitionRepository.findByCodeWithRewards(saved.code)!!, stats)
}
```

`replaceRewards` should validate the full list first, delete existing rewards, flush, then save new `AchievementReward` rows with `achievement = definition`.

- [ ] **Step 5: Wire controller**

Add:

```kotlin
@PatchMapping("/{code}")
fun update(
    @PathVariable code: String,
    @RequestBody request: UpdateAchievementAdminRequest,
): AchievementAdminDefinitionDto =
    achievementAdminService.updateAchievement(code, request)
```

- [ ] **Step 6: Run Stage 3 backend tests**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage3AdminIntegrationTest"
```

Expected: all Stage 3 backend tests pass.

- [ ] **Step 7: Run Stage 1 and Stage 2 regression tests**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage1IntegrationTest" --tests "io.github.mralex1810.fantasy.AchievementStage2ProfileShowcaseIntegrationTest"
```

Expected: both existing achievement test classes pass. This verifies dry-run, claim snapshots, profile showcase, hidden/disabled display behavior, and launch baseline remain intact.

---

### Task 4: Admin Frontend Route, Client, Table, Edit Drawer, Dry-Run

**Files:**

- Create: `polemica-fantasy-admin/src/api/achievements.ts`
- Create: `polemica-fantasy-admin/src/pages/AchievementsPage.tsx`
- Modify: `polemica-fantasy-admin/src/api/types.ts`
- Modify: `polemica-fantasy-admin/src/App.tsx`
- Modify: `polemica-fantasy-admin/src/layout/AdminLayout.tsx`

- [ ] **Step 1: Create a frontend red build**

First add route/menu imports before creating the page file:

```tsx
// polemica-fantasy-admin/src/App.tsx
import { AchievementsPage } from './pages/AchievementsPage'
...
<Route path="achievements" element={<AchievementsPage />} />
```

```tsx
// polemica-fantasy-admin/src/layout/AdminLayout.tsx
{ key: '/achievements', label: <Link to="/achievements">Achievements</Link> },
```

Run:

```bash
cd polemica-fantasy-admin && npm run build
```

Expected: fail with missing `./pages/AchievementsPage`. This is the frontend red check.

- [ ] **Step 2: Add TypeScript contracts**

Append to `polemica-fantasy-admin/src/api/types.ts`:

```ts
export type AchievementVisibility = 'PUBLIC' | 'HIDDEN' | 'SECRET' | 'PRIVATE'
export type AchievementRewardType =
  | 'FANTIKI'
  | 'PROFILE_FRAME'
  | 'CARD_SKIN_UNLOCK'
  | 'COSMETIC_UNLOCK'
  | 'BADGE_STYLE'

export interface AchievementAdminStatsDto {
  completedUsers: number
  claimedUsers: number
  unclaimedUsers: number
  totalProgress: number
  averageProgress: number
  nearCompletionUsers: number
  lastCompletedAt: string | null
}

export interface AchievementAdminRewardDto {
  type: AchievementRewardType | string
  amount: number | null
  code: string | null
  metadata: string | null
  displayOrder: number
}

export interface AchievementAdminDefinitionDto {
  code: string
  category: string
  conditionType: string
  historyPolicy: string
  targetValue: number
  chainGroup: string | null
  chainLevel: number | null
  title: string
  description: string | null
  iconUrl: string | null
  accentColor: string | null
  rarity: Rarity
  visibility: AchievementVisibility | string
  enabled: boolean
  trackingStartedAt: string | null
  displayOrder: number
  createdAt: string
  updatedAt: string
  rewards: AchievementAdminRewardDto[]
  stats: AchievementAdminStatsDto
}

export interface AchievementAdminListResponseDto {
  achievements: AchievementAdminDefinitionDto[]
}

export interface UpdateAchievementAdminRewardRequest {
  type: AchievementRewardType | string
  amount: number | null
  code: string | null
  metadata: string | null
  displayOrder: number
}

export interface UpdateAchievementAdminRequest {
  title: string
  description: string | null
  iconUrl: string | null
  accentColor: string | null
  rarity: Rarity
  visibility: AchievementVisibility
  enabled: boolean
  displayOrder: number
  rewards: UpdateAchievementAdminRewardRequest[]
}

export interface AchievementDryRunRowDto {
  code: string
  enabled: boolean
  instantCompleted: number
  instantFantikiLiability: number
}

export interface AchievementDryRunResponseDto {
  instantCompleted: number
  instantFantikiLiability: number
  rows: AchievementDryRunRowDto[]
}
```

- [ ] **Step 3: Add API client**

Create `polemica-fantasy-admin/src/api/achievements.ts`:

```ts
import { apiJson } from './client'
import type {
  AchievementAdminDefinitionDto,
  AchievementAdminListResponseDto,
  AchievementDryRunResponseDto,
  UpdateAchievementAdminRequest,
} from './types'

export function listAchievements() {
  return apiJson<AchievementAdminListResponseDto>('/v1/admin/achievements')
}

export function updateAchievement(code: string, body: UpdateAchievementAdminRequest) {
  return apiJson<AchievementAdminDefinitionDto>(
    `/v1/admin/achievements/${encodeURIComponent(code)}`,
    {
      method: 'PATCH',
      body: JSON.stringify(body),
    },
  )
}

export function dryRunAchievementBackfill() {
  return apiJson<AchievementDryRunResponseDto>(
    '/v1/admin/achievements/backfill/dry-run',
    { method: 'POST', body: JSON.stringify({}) },
  )
}
```

- [ ] **Step 4: Implement `AchievementsPage`**

Create `polemica-fantasy-admin/src/pages/AchievementsPage.tsx` using Ant Design patterns from `PerksPage.tsx`, `EconomyPage.tsx`, and `ProductCommsPage.tsx`.

Required behavior:

- Query key: `['admin', 'achievements']`.
- Dry-run mutation key is local to the page; invalidate nothing on dry-run.
- Update mutation invalidates/refetches `['admin', 'achievements']` and closes the drawer on success.
- Table row key: `code`.
- Filters: `category`, `enabled`, `visibility`, `rarity`.
- Columns: code, category, title, enabled, visibility, rarity, rewards summary, completed, claimed, unclaimed, near completion, last completed, actions.
- System fields (`code`, `conditionType`, `historyPolicy`, `targetValue`, `chainGroup`, `chainLevel`, `trackingStartedAt`) are shown read-only in the drawer.
- Editable fields: title, description, iconUrl, accentColor, rarity, visibility, enabled, displayOrder, rewards.
- Rewards use `Form.List`; reward type select options are `FANTIKI`, `PROFILE_FRAME`, `CARD_SKIN_UNLOCK`, `COSMETIC_UNLOCK`, `BADGE_STYLE`.
- Dry-run card shows `instantCompleted`, `instantFantikiLiability`, and a small table of rows. Show a success `Alert` when both totals are zero and a warning `Alert` otherwise.

Use these option constants:

```ts
const RARITY_OPTIONS = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY'].map((value) => ({ value, label: value }))
const VISIBILITY_OPTIONS = ['PUBLIC', 'HIDDEN', 'SECRET', 'PRIVATE'].map((value) => ({ value, label: value }))
const REWARD_TYPE_OPTIONS = ['FANTIKI', 'PROFILE_FRAME', 'CARD_SKIN_UNLOCK', 'COSMETIC_UNLOCK', 'BADGE_STYLE'].map((value) => ({ value, label: value }))
```

Submit normalization:

```ts
function nullableText(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed.length ? trimmed : null
}

function normalizeReward(row: {
  type: string
  amount?: number | null
  code?: string | null
  metadata?: string | null
  displayOrder?: number | null
}) {
  return {
    type: row.type,
    amount: row.type === 'FANTIKI' ? (row.amount ?? null) : null,
    code: row.type === 'FANTIKI' ? null : nullableText(row.code),
    metadata: nullableText(row.metadata),
    displayOrder: row.displayOrder ?? 0,
  }
}
```

Form validation mirrors backend:

- `title` required.
- `accentColor` pattern `^#[0-9A-Fa-f]{6}$` when present.
- `displayOrder` min 0.
- reward amount min 1 when type is `FANTIKI`.
- reward code required when type is not `FANTIKI`.
- metadata field is a textarea with placeholder `{"key":"value"}`.

- [ ] **Step 5: Run admin build and verify green**

Run:

```bash
cd polemica-fantasy-admin && npm run build
```

Expected: TypeScript and Vite build pass.

---

### Task 5: Cross-Module Verification And Memory

**Files:**

- Append after implementation verification: `memory-bank/activeContext.md`
- Update after implementation verification: `memory-bank/progress.md`

- [ ] **Step 1: Run backend targeted tests**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage3AdminIntegrationTest"
```

Expected: pass.

- [ ] **Step 2: Run achievement regression tests**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage1IntegrationTest" --tests "io.github.mralex1810.fantasy.AchievementStage2ProfileShowcaseIntegrationTest"
```

Expected: pass.

- [ ] **Step 3: Run backend compile**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew compileKotlin compileTestKotlin
```

Expected: pass.

- [ ] **Step 4: Run admin build**

Run:

```bash
cd polemica-fantasy-admin && npm run build
```

Expected: pass.

- [ ] **Step 5: Run broad quick check**

Run:

```bash
./scripts/codex-check.sh quick
```

Expected: pass. If the command cannot run because dependencies or Docker/Testcontainers are unavailable, record the exact failure and still report the successful targeted commands.

- [ ] **Step 6: Update memory only after verification**

Append a short dated note to `memory-bank/activeContext.md`:

```markdown
- **2026-05-25 (backend+admin / user achievements Stage 3 admin):** Added admin achievements management: `GET /api/v1/admin/achievements`, `PATCH /api/v1/admin/achievements/{code}` with metadata/flags/full reward replacement, preserved `tracking_started_at` enable invariant and claimed reward snapshots, and added admin `/achievements` UI with analytics and dry-run. No new dormant marketplace/social/legendary evaluators were enabled by code. Verification: Stage 3 targeted test, Stage 1/2 achievement regression tests, backend compile, admin build, and quick check.
```

Update the achievement section in `memory-bank/progress.md` with a checked Stage 3 line:

```markdown
- [x] Реализован Stage 3 Admin для пользовательских достижений: admin list/edit metadata/rewards, aggregate stats, dry-run UI, инвариант `tracking_started_at` при первом включении dormant rows, без новых marketplace/social/legendary evaluators
```

---

## Plan Reviewer Risk Callouts

- Verify the implementation does not expose `conditionType`, `historyPolicy`, `targetValue`, or `code` as editable form fields or request properties.
- Confirm unknown JSON fields in `PATCH` fail with `400`, especially `code`, so clients cannot silently send ignored system-contract mutations.
- Confirm reward replacement does not mutate existing `user_achievement.reward_snapshot`; existing claimed rows must remain historically stable and idempotent.
- Confirm reward validation rejects zero/negative FANTIKI amounts, cosmetic rewards without codes, FANTIKI rewards with codes, non-object metadata, and unsupported reward types.
- Confirm enum validation is explicit for `rarity` and `visibility`; do not rely on raw strings passing through.
- Confirm full reward replacement is transactional and deletes old rewards before inserting new rewards. Use `flush()` after bulk delete to avoid stale persistence-context or ordering surprises.
- Confirm enabling a dormant row with `tracking_started_at IS NULL` sets it exactly once, and disabling a previously enabled row does not erase history.
- Confirm Stage 3 does not add evaluators/event sources for dormant marketplace/social/legendary definitions. Admin can edit/enable rows, but code should not start counting new dormant domains in this stage.
- Confirm the admin list uses persisted aggregates, while dry-run remains the explicit expensive live evaluator path.
- Confirm `POST /api/v1/admin/achievements/backfill/dry-run` still returns zero instant payout for untouched V1 launch baseline.

## Self-Review Checklist

- Spec coverage: Stage 3 admin page, metadata/reward editing, aggregate analytics, and dry-run UI are covered. Backfill job/status/user repair endpoints remain out of scope by explicit Stage 3 narrowing.
- Placeholder scan: no placeholder tokens, no unspecified validation, no "write tests for above" without concrete test scenarios.
- Type consistency: backend DTO names match frontend TypeScript names and `api/achievements.ts` function signatures.
- Contract synchronization: Kotlin DTOs, admin `src/api/types.ts`, admin API client, route, menu, and page are all included.

STATUS: DONE
