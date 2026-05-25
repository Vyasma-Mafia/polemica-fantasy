# User Achievements Stage 2 Profile Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Stage 2 of `docs/features/DESIGN-ACHIEVEMENTS.md`: public profile achievement showcase, profile frame selection, featured achievement ordering, and a TMA customization screen.

**Architecture:** Add Flyway-owned profile customization tables and keep the business rules in a new `ProfileCustomizationService`. Extend `PlayerProfileService` by composing read-only achievement showcase data from Stage 1 catalog/progress tables, while keeping entities inside the service layer and synchronizing Kotlin DTOs with TMA TypeScript types.

**Tech Stack:** Kotlin/Spring Boot/JPA/PostgreSQL/Flyway, MockMvc integration tests with Testcontainers, React 19/Vite/TanStack Query/React Router in the Telegram Mini App.

---

## Decisions For Stage 2

- Public featured achievements require `claimed_at IS NOT NULL`, not only `completed_at IS NOT NULL`. The design says completed/claimed; Stage 2 chooses claimed as the stricter public-display rule so unclaimed rewards are not displayed as status badges.
- Disabled achievements remain displayable after claim if they are already featured. The profile query must not filter featured rows by `achievement_definition.enabled`.
- Featured badges must still respect `achievement_definition.visibility = 'PUBLIC'` on selection and rendering. Disabled public claimed achievements may remain displayable if featured; hidden/private achievements must never be selectable or exposed on the public profile.
- Selected profile frames are validated on both write and read. If a stale customization row points at a frame code that is no longer present in `user_cosmetic_unlock` as `PROFILE_FRAME`, both customization and public profile responses return `null` for the active frame.
- `nextAchievement` is read-only recommendation data, not a new persisted preference. Selection rule: visible enabled public achievements that are not claimed, sorted by whether progress is positive, completion ratio descending, then `achievement_definition.display_order ASC`, then id. If no progress exists, show the first enabled public unclaimed achievement by display order.
- Profile frame metadata comes from `user_cosmetic_unlock` plus a small backend label mapper for known Stage 1 frame codes (`budget_master`, `dynasty`). No separate `profile_frame` catalog is introduced in Stage 2.
- Planning task does not update `memory-bank/`. The implementer updates `memory-bank/activeContext.md` and `memory-bank/progress.md` only after working code and verification.

## Files Map

Backend create:
- `polemica-fantasy-backend/src/main/resources/db/migration/V47__profile_showcase.sql` - profile customization and featured achievement schema.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/entity/UserProfileCustomization.kt` - one customization row per user.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/entity/UserProfileFeaturedAchievement.kt` - ordered featured achievement rows.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserProfileCustomizationRepository.kt` - customization row lookup.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserProfileFeaturedAchievementRepository.kt` - featured rows lookup/delete/save.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/user/request/ProfileCustomizationRequests.kt` - `UpdateProfileCustomizationRequest`.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/user/response/ProfileCustomizationDtos.kt` - customization page DTOs.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/ProfileCustomizationService.kt` - rules, DTO mapping, public profile showcase summary.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/controller/user/ProfileCustomizationController.kt` - `/api/v1/me/profile-customization`.
- `polemica-fantasy-backend/src/test/kotlin/io/github/mralex1810/fantasy/AchievementStage2ProfileShowcaseIntegrationTest.kt` - red-first integration coverage.

Backend modify:
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/user/response/PlayerProfileDtos.kt` - add `achievementSummary`, `profileFrame`, `featuredAchievements`, `nextAchievement`.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/PlayerProfileService.kt` - inject `ProfileCustomizationService` and compose showcase DTO fields.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserAchievementRepository.kt` - add claimed/progress query methods.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserCosmeticUnlockRepository.kt` - add frame lookup methods.
- `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/AchievementDefinitionRepository.kt` - add enabled public definitions query if needed for `nextAchievement`.

TMA create:
- `polemica-fantasy-webapp/src/pages/ProfileCustomizationPage.tsx` - frame and featured badge editor.

TMA modify:
- `polemica-fantasy-webapp/src/api/types.ts` - profile showcase and customization DTOs.
- `polemica-fantasy-webapp/src/api/achievements.ts` - `fetchProfileCustomization`, `useProfileCustomization`, `useUpdateProfileCustomization`.
- `polemica-fantasy-webapp/src/pages/PlayerProfilePage.tsx` - header frame, featured badges, counter, next progress, own-profile edit button.
- `polemica-fantasy-webapp/src/App.tsx` - route `/profile-customization`.
- `polemica-fantasy-webapp/src/index.css` - profile showcase and editor styles.

---

### Task 1: Backend Red Tests

**Files:**
- Create: `polemica-fantasy-backend/src/test/kotlin/io/github/mralex1810/fantasy/AchievementStage2ProfileShowcaseIntegrationTest.kt`

- [ ] **Step 1: Write failing integration tests**

Create a new test class using the same `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Testcontainers`, `@ActiveProfiles("test")`, `MockMvc`, `JdbcTemplate`, `tmaAuth`, `basicAuth`, and fixture-helper style as `AchievementStage1IntegrationTest`.

Required tests:

```kotlin
@Test
fun `GET profile customization returns unlocked frames and claimed selectable achievements`() {
    val auth = basicAuth("admin", "test-admin-secret")
    val fixture = createClaimedAchievementFixture(auth, "customization-get", "series_win_1")
    val tma = tmaAuth(fixture.telegramPlatformId, "ShowcaseGet")

    mockMvc.perform(get("/api/v1/me/profile-customization").header("Authorization", tma))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.profileFrameCode").value(nullValue()))
        .andExpect(jsonPath("$.unlockedFrames[0].code").value("budget_master"))
        .andExpect(jsonPath("$.availableFeaturedAchievements[0].code").value("series_win_1"))
        .andExpect(jsonPath("$.featuredAchievementCodes").isArray)
}

@Test
fun `PUT profile customization validates max featured frame unlock and claimed achievements`() {
    val auth = basicAuth("admin", "test-admin-secret")
    val fixture = createClaimedAchievementFixture(auth, "customization-put", "series_win_1")
    val tma = tmaAuth(fixture.telegramPlatformId, "ShowcasePut")

    mockMvc.perform(putJson(tma, """{"profileFrameCode":null,"featuredAchievementCodes":["a","b","c","d","e","f"]}"""))
        .andExpect(status().isBadRequest)

    mockMvc.perform(putJson(tma, """{"profileFrameCode":"dynasty","featuredAchievementCodes":[]}"""))
        .andExpect(status().isBadRequest)

    mockMvc.perform(putJson(tma, """{"profileFrameCode":null,"featuredAchievementCodes":["team_submit_1"]}"""))
        .andExpect(status().isBadRequest)

    mockMvc.perform(putJson(tma, """{"profileFrameCode":"budget_master","featuredAchievementCodes":["series_win_1"]}"""))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.profileFrameCode").value("budget_master"))
        .andExpect(jsonPath("$.featuredAchievementCodes[0]").value("series_win_1"))
}

@Test
fun `public profile returns achievement summary frame featured badges and disabled claimed featured`() {
    val auth = basicAuth("admin", "test-admin-secret")
    val fixture = createClaimedAchievementFixture(auth, "public-profile", "series_win_1")
    val tma = tmaAuth(fixture.telegramPlatformId, "PublicProfile")

    mockMvc.perform(putJson(tma, """{"profileFrameCode":"budget_master","featuredAchievementCodes":["series_win_1"]}"""))
        .andExpect(status().isOk)
    jdbcTemplate.update("UPDATE achievement_definition SET enabled = FALSE WHERE code = 'series_win_1'")

    mockMvc.perform(
        get("/api/v1/players/${fixture.telegramPlatformId}/profile")
            .header("Authorization", tma),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.achievementSummary.claimed").value(1))
        .andExpect(jsonPath("$.profileFrame.code").value("budget_master"))
        .andExpect(jsonPath("$.featuredAchievements[0].code").value("series_win_1"))
        .andExpect(jsonPath("$.featuredAchievements[0].title").value("Первая победа"))
}

@Test
fun `public profile next achievement chooses visible enabled unclaimed progress`() {
    val auth = basicAuth("admin", "test-admin-secret")
    val fixture = createTeamFixture(auth, "next-achievement")
    val tma = tmaAuth(fixture.telegramPlatformId, "NextAchievement")

    createFantasyTeam(tma, fixture.seriesId, "MAIN", fixture.userCardIds.take(1))

    mockMvc.perform(
        get("/api/v1/players/${fixture.telegramPlatformId}/profile")
            .header("Authorization", tma),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.nextAchievement.code").value("team_submit_1"))
        .andExpect(jsonPath("$.nextAchievement.progressValue").value(1))
        .andExpect(jsonPath("$.nextAchievement.targetValue").value(1))

    mockMvc.perform(post("/api/v1/achievements/team_submit_1/claim").header("Authorization", tma))
        .andExpect(status().isOk)

    mockMvc.perform(
        get("/api/v1/players/${fixture.telegramPlatformId}/profile")
            .header("Authorization", tma),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.nextAchievement.code").value("team_submit_5"))
        .andExpect(jsonPath("$.nextAchievement.progressValue").value(1))
        .andExpect(jsonPath("$.nextAchievement.targetValue").value(5))
}
```

Additional required tests:

```kotlin
@Test
fun `hidden claimed achievement is rejected and never exposed as featured`() {
    val auth = basicAuth("admin", "test-admin-secret")
    val fixture = createClaimedAchievementFixture(auth, "hidden-featured", "series_win_1")
    val tma = tmaAuth(fixture.telegramPlatformId, "HiddenFeatured")
    jdbcTemplate.update("UPDATE achievement_definition SET visibility = 'HIDDEN' WHERE code = 'series_win_1'")

    mockMvc.perform(get("/api/v1/me/profile-customization").header("Authorization", tma))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.availableFeaturedAchievements[*].code", not(hasItem("series_win_1"))))

    mockMvc.perform(putJson(tma, """{"profileFrameCode":null,"featuredAchievementCodes":["series_win_1"]}"""))
        .andExpect(status().isBadRequest)

    jdbcTemplate.update(
        """
        INSERT INTO user_profile_featured_achievement (telegram_user_id, achievement_id, display_order)
        SELECT ?, id, 0 FROM achievement_definition WHERE code = 'series_win_1'
        """.trimIndent(),
        internalUserId(fixture.telegramPlatformId),
    )

    mockMvc.perform(
        get("/api/v1/players/${fixture.telegramPlatformId}/profile")
            .header("Authorization", tma),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.featuredAchievements", hasSize<Any>(0)))
}

@Test
fun `stale selected frame is not returned without current frame unlock`() {
    val auth = basicAuth("admin", "test-admin-secret")
    val fixture = createClaimedAchievementFixture(auth, "stale-frame", "series_win_1")
    val tma = tmaAuth(fixture.telegramPlatformId, "StaleFrame")
    val internalId = internalUserId(fixture.telegramPlatformId)
    jdbcTemplate.update(
        """
        INSERT INTO user_profile_customization (telegram_user_id, profile_frame_code)
        VALUES (?, 'dynasty')
        ON CONFLICT (telegram_user_id) DO UPDATE SET profile_frame_code = EXCLUDED.profile_frame_code
        """.trimIndent(),
        internalId,
    )

    mockMvc.perform(get("/api/v1/me/profile-customization").header("Authorization", tma))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.profileFrameCode").value(nullValue()))

    mockMvc.perform(
        get("/api/v1/players/${fixture.telegramPlatformId}/profile")
            .header("Authorization", tma),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.profileFrame").value(nullValue()))
}

@Test
fun `featured achievement reorder preserves order without display order collisions`() {
    val auth = basicAuth("admin", "test-admin-secret")
    val fixture = createTwoClaimedAchievementsFixture(auth, "reorder-featured", "team_submit_1", "series_win_1")
    val tma = tmaAuth(fixture.telegramPlatformId, "ReorderFeatured")

    mockMvc.perform(putJson(tma, """{"profileFrameCode":null,"featuredAchievementCodes":["team_submit_1","series_win_1"]}"""))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.featuredAchievementCodes[0]").value("team_submit_1"))
        .andExpect(jsonPath("$.featuredAchievementCodes[1]").value("series_win_1"))

    mockMvc.perform(putJson(tma, """{"profileFrameCode":null,"featuredAchievementCodes":["series_win_1","team_submit_1"]}"""))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.featuredAchievementCodes[0]").value("series_win_1"))
        .andExpect(jsonPath("$.featuredAchievementCodes[1]").value("team_submit_1"))

    assertSqlLong(
        "SELECT COUNT(*) FROM user_profile_featured_achievement WHERE telegram_user_id = ${internalUserId(fixture.telegramPlatformId)}",
        2,
    )
}
```

The `createClaimedAchievementFixture` helper should create two users/teams, finish a series with the fixture user first, call `POST /api/v1/achievements/{code}/claim`, insert a test-only `PROFILE_FRAME` unlock for `budget_master`, and assert the claimed achievement plus frame unlock exist. Add `org.hamcrest.Matchers.nullValue` to the test imports. Reuse the Stage 1 helper logic locally in this test file; do not refactor Stage 1 tests during the red phase.

The `createTwoClaimedAchievementsFixture` helper should reuse the same user and create/claim two public achievements so reorder coverage can exercise the unique `(telegram_user_id, display_order)` index. It can create a team and claim `team_submit_1`, then use the same finalization path as `createClaimedAchievementFixture` to claim `series_win_1`.

- [ ] **Step 2: Run targeted tests and verify red**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage2ProfileShowcaseIntegrationTest"
```

Expected: compilation fails because `V47`, customization DTOs/controller/service, and `PlayerProfileDto` fields do not exist yet. This is the correct red failure.

---

### Task 2: Schema And Entities

**Files:**
- Create: `polemica-fantasy-backend/src/main/resources/db/migration/V47__profile_showcase.sql`
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/entity/UserProfileCustomization.kt`
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/entity/UserProfileFeaturedAchievement.kt`
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserProfileCustomizationRepository.kt`
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserProfileFeaturedAchievementRepository.kt`

- [ ] **Step 1: Add Flyway migration**

Create `V47__profile_showcase.sql`:

```sql
CREATE TABLE user_profile_customization (
    telegram_user_id BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    profile_frame_code VARCHAR(96),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_profile_featured_achievement (
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    achievement_id BIGINT NOT NULL REFERENCES achievement_definition(id) ON DELETE CASCADE,
    display_order INT NOT NULL,
    PRIMARY KEY (telegram_user_id, achievement_id)
);

CREATE UNIQUE INDEX ux_user_profile_featured_order
    ON user_profile_featured_achievement(telegram_user_id, display_order);

CREATE INDEX idx_user_profile_featured_user_order
    ON user_profile_featured_achievement(telegram_user_id, display_order, achievement_id);
```

- [ ] **Step 2: Add JPA entities**

Use the existing `@IdClass` style from `UserAchievement` and `UserCosmeticUnlock`.

Required entity shapes:

```kotlin
@Entity
@Table(name = "user_profile_customization")
class UserProfileCustomization(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Column(name = "profile_frame_code", length = 96)
    var profileFrameCode: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
```

```kotlin
data class UserProfileFeaturedAchievementId(
    var telegramUser: Long? = null,
    var achievement: Long? = null,
) : Serializable

@Entity
@IdClass(UserProfileFeaturedAchievementId::class)
@Table(name = "user_profile_featured_achievement")
class UserProfileFeaturedAchievement(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    var achievement: AchievementDefinition? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)
```

- [ ] **Step 3: Add repositories**

```kotlin
interface UserProfileCustomizationRepository : JpaRepository<UserProfileCustomization, Long> {
    fun findByTelegramUser_Id(telegramUserId: Long): UserProfileCustomization?
}
```

```kotlin
interface UserProfileFeaturedAchievementRepository :
    JpaRepository<UserProfileFeaturedAchievement, UserProfileFeaturedAchievementId> {

    @Query(
        """
        SELECT f FROM UserProfileFeaturedAchievement f
        JOIN FETCH f.achievement d
        WHERE f.telegramUser.id = :telegramUserId
        ORDER BY f.displayOrder ASC
        """,
    )
    fun findAllByTelegramUserIdOrdered(@Param("telegramUserId") telegramUserId: Long): List<UserProfileFeaturedAchievement>

    @Modifying
    @Query("DELETE FROM UserProfileFeaturedAchievement f WHERE f.telegramUser.id = :telegramUserId")
    fun deleteAllByTelegramUserId(@Param("telegramUserId") telegramUserId: Long): Int
}
```

- [ ] **Step 4: Run compile and verify remaining red**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew compileKotlin
```

Expected: compile still fails until DTO/service/controller code is added in the next task, or succeeds if only isolated schema/entity code was added. The Stage 2 test remains red.

---

### Task 3: Backend DTOs, Service, And Controller

**Files:**
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/user/request/ProfileCustomizationRequests.kt`
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/user/response/ProfileCustomizationDtos.kt`
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/achievement/ProfileCustomizationService.kt`
- Create: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/controller/user/ProfileCustomizationController.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserAchievementRepository.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/UserCosmeticUnlockRepository.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/repository/AchievementDefinitionRepository.kt`

- [ ] **Step 1: Add request/response DTOs**

Add:

```kotlin
data class UpdateProfileCustomizationRequest(
    val profileFrameCode: String?,
    val featuredAchievementCodes: List<String> = emptyList(),
)
```

Add response DTOs:

```kotlin
data class ProfileCustomizationDto(
    val profileFrameCode: String?,
    val unlockedFrames: List<ProfileFrameDto>,
    val featuredAchievementCodes: List<String>,
    val availableFeaturedAchievements: List<AchievementBadgeDto>,
)

data class ProfileFrameDto(
    val code: String,
    val name: String,
    val assetUrl: String?,
)

data class AchievementBadgeDto(
    val code: String,
    val title: String,
    val iconUrl: String?,
    val rarity: String,
    val accentColor: String?,
)

data class PlayerAchievementSummaryDto(
    val completed: Int,
    val claimed: Int,
    val totalVisible: Int,
)

data class PlayerNextAchievementDto(
    val code: String,
    val title: String,
    val progressValue: Long,
    val targetValue: Long,
)

data class PlayerAchievementShowcaseDto(
    val achievementSummary: PlayerAchievementSummaryDto,
    val profileFrame: ProfileFrameDto?,
    val featuredAchievements: List<AchievementBadgeDto>,
    val nextAchievement: PlayerNextAchievementDto?,
)
```

- [ ] **Step 2: Add repository methods**

Add to `UserAchievementRepository`:

```kotlin
@Query(
    """
    SELECT ua FROM UserAchievement ua
    JOIN FETCH ua.achievement d
    WHERE ua.telegramUser.id = :telegramUserId
      AND ua.claimedAt IS NOT NULL
      AND d.visibility = 'PUBLIC'
    ORDER BY d.displayOrder ASC, d.id ASC
    """,
)
fun findClaimedWithDefinitions(@Param("telegramUserId") telegramUserId: Long): List<UserAchievement>

@Query(
    """
    SELECT ua FROM UserAchievement ua
    JOIN FETCH ua.achievement d
    WHERE ua.telegramUser.id = :telegramUserId
    """,
)
fun findAllWithDefinitionsByTelegramUserId(@Param("telegramUserId") telegramUserId: Long): List<UserAchievement>
```

Add to `UserCosmeticUnlockRepository`:

```kotlin
fun findAllByTelegramUser_IdAndCosmeticType(telegramUserId: Long, cosmeticType: String): List<UserCosmeticUnlock>

fun existsByTelegramUser_IdAndCosmeticTypeAndCosmeticCode(
    telegramUserId: Long,
    cosmeticType: String,
    cosmeticCode: String,
): Boolean
```

Add to `AchievementDefinitionRepository`:

```kotlin
@Query(
    """
    SELECT DISTINCT d FROM AchievementDefinition d
    LEFT JOIN FETCH d.rewards r
    WHERE d.enabled = TRUE
      AND d.visibility = 'PUBLIC'
    ORDER BY d.displayOrder ASC, d.id ASC
    """,
)
fun findAllEnabledPublicWithRewards(): List<AchievementDefinition>
```

- [ ] **Step 3: Implement `ProfileCustomizationService`**

Constructor dependencies:

```kotlin
class ProfileCustomizationService(
    private val customizationRepository: UserProfileCustomizationRepository,
    private val featuredRepository: UserProfileFeaturedAchievementRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val userCosmeticUnlockRepository: UserCosmeticUnlockRepository,
    private val achievementDefinitionRepository: AchievementDefinitionRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val achievementCatalogService: AchievementCatalogService,
)
```

Public methods:

```kotlin
@Transactional(readOnly = true)
fun getCustomization(user: TelegramUser): ProfileCustomizationDto

@Transactional
fun updateCustomization(user: TelegramUser, request: UpdateProfileCustomizationRequest): ProfileCustomizationDto

@Transactional(readOnly = true)
fun buildPublicShowcase(internalTelegramUserId: Long): PlayerAchievementShowcaseDto
```

Rules inside `updateCustomization`:
- Reject `featuredAchievementCodes.size > 5` with `ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 5 featured achievements are allowed")`.
- Reject duplicate featured codes by comparing `distinct().size`.
- Reject non-null `profileFrameCode` unless `user_cosmetic_unlock` has `(cosmetic_type = 'PROFILE_FRAME', cosmetic_code = request.profileFrameCode)`.
- Featured codes must all be claimed by the current user and public: `claimed_at IS NOT NULL` and `achievement_definition.visibility = 'PUBLIC'`.
- Save order exactly as request order with `display_order = index`.
- Allow `profileFrameCode = null` and empty `featuredAchievementCodes`.

Implementation notes:
- Use internal `telegram_user.id` from `TelegramUser`.
- Fetch managed `TelegramUser` through `telegramUserRepository.findById(user.id!!)` before creating new entity rows.
- Delete featured rows, call `featuredRepository.flush()`, then insert new rows to avoid unique collisions on reordered `display_order`. The reorder integration test must fail without this flush.
- In `getCustomization`, return `profileFrameCode = null` if the stored selected frame is no longer unlocked as `PROFILE_FRAME`.
- For frame names, use:

```kotlin
private fun frameName(code: String): String = when (code) {
    "budget_master" -> "Мастер бюджета"
    "dynasty" -> "Династия"
    else -> code.replace('_', ' ')
}
```

- [ ] **Step 4: Implement `buildPublicShowcase`**

Summary:
- `completed`: count enabled visible catalog items whose state is `COMPLETED_UNCLAIMED` or `CLAIMED`.
- `claimed`: count claimed visible achievements for the user. Include disabled claimed rows in the count only if they are featured; otherwise keep the summary anchored to enabled visible catalog plus claimed visible definitions.
- `totalVisible`: enabled visible definitions count from `findAllEnabledPublicWithRewards()`.

Featured:
- Read `user_profile_featured_achievement` by order.
- Include only rows where the user still has `claimed_at IS NOT NULL`.
- Do not filter out disabled definitions.
- Require `achievement_definition.visibility = 'PUBLIC'` so hidden/private definitions are never exposed.

Profile frame:
- Read selected `user_profile_customization.profile_frame_code`.
- Return `profileFrame = null` unless `user_cosmetic_unlock` still contains `(telegram_user_id, cosmetic_type = 'PROFILE_FRAME', cosmetic_code = selectedCode)`.

Next:
- Build candidate items from `achievementDefinitionRepository.findAllEnabledPublicWithRewards()`.
- For each candidate, call `achievementCatalogService.toItem(definition, progress, internalTelegramUserId)` using progress from `findAllWithDefinitionsByTelegramUserId`.
- Exclude `state == "CLAIMED"`.
- Sort by:

```kotlin
compareByDescending<AchievementItemDto> { it.progressValue > 0 }
    .thenByDescending { if (it.targetValue > 0) it.progressValue.toDouble() / it.targetValue.toDouble() else 0.0 }
    .thenBy { definitionDisplayOrderByCode[it.code] ?: Int.MAX_VALUE }
```

- Return `PlayerNextAchievementDto` for the first candidate, with `progressValue` capped at `targetValue` for display.

- [ ] **Step 5: Add controller**

Create:

```kotlin
@RestController
@RequestMapping("/api/v1/me/profile-customization")
class ProfileCustomizationController(
    private val profileCustomizationService: ProfileCustomizationService,
) {
    @GetMapping
    fun get(@AuthenticationPrincipal user: TelegramUser): ProfileCustomizationDto =
        profileCustomizationService.getCustomization(user)

    @PutMapping
    fun update(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: UpdateProfileCustomizationRequest,
    ): ProfileCustomizationDto = profileCustomizationService.updateCustomization(user, request)
}
```

- [ ] **Step 6: Run targeted tests and verify progress**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage2ProfileShowcaseIntegrationTest"
```

Expected: tests still fail only on public profile missing DTO fields until Task 4 is complete.

---

### Task 4: Extend Public Profile API

**Files:**
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/dto/user/response/PlayerProfileDtos.kt`
- Modify: `polemica-fantasy-backend/src/main/kotlin/io/github/mralex1810/fantasy/service/PlayerProfileService.kt`

- [ ] **Step 1: Extend `PlayerProfileDto`**

Add fields after `seriesWins`:

```kotlin
val achievementSummary: PlayerAchievementSummaryDto,
val profileFrame: ProfileFrameDto?,
val featuredAchievements: List<AchievementBadgeDto>,
val nextAchievement: PlayerNextAchievementDto?,
```

Import DTOs from `ProfileCustomizationDtos.kt`.

- [ ] **Step 2: Compose showcase in `PlayerProfileService`**

Inject `ProfileCustomizationService`:

```kotlin
private val profileCustomizationService: ProfileCustomizationService,
```

Inside `getProfile`, after `seriesWins`:

```kotlin
val achievementShowcase = profileCustomizationService.buildPublicShowcase(userId)
```

Pass to `PlayerProfileDto`:

```kotlin
achievementSummary = achievementShowcase.achievementSummary,
profileFrame = achievementShowcase.profileFrame,
featuredAchievements = achievementShowcase.featuredAchievements,
nextAchievement = achievementShowcase.nextAchievement,
```

- [ ] **Step 3: Run Stage 2 backend tests green**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage2ProfileShowcaseIntegrationTest"
```

Expected: PASS.

- [ ] **Step 4: Run backend compile checks**

Run:

```bash
cd polemica-fantasy-backend && ./gradlew compileKotlin compileTestKotlin
```

Expected: BUILD SUCCESSFUL.

---

### Task 5: TMA API Types And Client

**Files:**
- Modify: `polemica-fantasy-webapp/src/api/types.ts`
- Modify: `polemica-fantasy-webapp/src/api/achievements.ts`

- [ ] **Step 1: Add TypeScript DTOs**

Add near existing achievement types:

```ts
export interface ProfileFrame {
  code: string
  name: string
  assetUrl: string | null
}

export interface AchievementBadge {
  code: string
  title: string
  iconUrl: string | null
  rarity: Rarity
  accentColor: string | null
}

export interface ProfileCustomization {
  profileFrameCode: string | null
  unlockedFrames: ProfileFrame[]
  featuredAchievementCodes: string[]
  availableFeaturedAchievements: AchievementBadge[]
}

export interface UpdateProfileCustomizationRequest {
  profileFrameCode: string | null
  featuredAchievementCodes: string[]
}

export interface PlayerAchievementSummary {
  completed: number
  claimed: number
  totalVisible: number
}

export interface PlayerNextAchievement {
  code: string
  title: string
  progressValue: number
  targetValue: number
}
```

Extend `PlayerProfile`:

```ts
achievementSummary: PlayerAchievementSummary
profileFrame: ProfileFrame | null
featuredAchievements: AchievementBadge[]
nextAchievement: PlayerNextAchievement | null
```

- [ ] **Step 2: Add API hooks**

In `achievements.ts`, add:

```ts
export function useProfileCustomization(initData: string | undefined) {
  return useQuery({
    queryKey: ['profile-customization', initData],
    queryFn: () => apiGet<ProfileCustomization>('/api/v1/me/profile-customization', initData),
    enabled: !!initData,
  })
}

export function useUpdateProfileCustomization(initData: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateProfileCustomizationRequest) =>
      apiSend<ProfileCustomization>('PUT', '/api/v1/me/profile-customization', initData, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['profile-customization'] })
      void queryClient.invalidateQueries({ queryKey: ['player-profile'] })
      void queryClient.invalidateQueries({ queryKey: ['achievements'] })
    },
  })
}
```

Import `ProfileCustomization` and `UpdateProfileCustomizationRequest` from `types.ts`.

- [ ] **Step 3: Run TMA build red/green checkpoint**

Run:

```bash
cd polemica-fantasy-webapp && npm run build
```

Expected after Task 5 alone: build may fail because route/page usage is not yet added, or pass if types are isolated. Continue to Task 6 before final frontend verification.

---

### Task 6: TMA Profile Header Showcase

**Files:**
- Modify: `polemica-fantasy-webapp/src/pages/PlayerProfilePage.tsx`
- Modify: `polemica-fantasy-webapp/src/index.css`

- [ ] **Step 1: Detect own profile**

Use existing TMA auth data only if a local helper exists. If there is no helper for current Telegram id, add a lightweight query to existing `/api/v1/me` data by using the same user profile hook used by `TopBarDisplayName` or `FantikiBalance`; compare `me.telegramId === profile.user.telegramId`.

Expected local variable:

```ts
const isOwnProfile = meQ.data?.telegramId === profile.user.telegramId
```

- [ ] **Step 2: Render showcase in profile header**

Inside `.pf-profile-header`, render:

```tsx
<div className={`pf-profile-frame ${profile.profileFrame ? `pf-profile-frame--${profile.profileFrame.code}` : ''}`}>
  <div className="pf-profile-showcase">
    <div className="pf-profile-showcase__summary">
      <strong>{profile.achievementSummary.claimed}</strong>
      <span>из {profile.achievementSummary.totalVisible}</span>
    </div>
    <div className="pf-profile-showcase__badges">
      {profile.featuredAchievements.map((achievement) => (
        <span
          key={achievement.code}
          className={`pf-profile-badge pf-profile-badge--${rarityClass(achievement.rarity)}`}
          style={achievement.accentColor ? { borderColor: achievement.accentColor } : undefined}
        >
          {achievement.title}
        </span>
      ))}
    </div>
  </div>
  {profile.nextAchievement && (
    <div className="pf-profile-next">
      <span>{profile.nextAchievement.title}</span>
      <div className="pf-profile-next__bar">
        <span style={{ width: `${nextAchievementPercent(profile.nextAchievement)}%` }} />
      </div>
      <small>
        {profile.nextAchievement.progressValue} / {profile.nextAchievement.targetValue}
      </small>
    </div>
  )}
</div>
```

Add a local `nextAchievementPercent` helper that caps at `100`.

- [ ] **Step 3: Add own-profile edit route button**

In the existing share row, render for own profile:

```tsx
{isOwnProfile && (
  <Link to="/profile-customization" className="pf-btn pf-btn--small">
    Настроить витрину
  </Link>
)}
```

Keep the existing share button.

- [ ] **Step 4: Add CSS**

Add styles under the existing Player Profile section:

```css
.pf-profile-frame {
  margin: 12px 0 0;
  padding: 10px;
  border: 1px solid var(--pf-border);
  border-radius: 8px;
  background: rgba(12, 18, 28, 0.72);
}

.pf-profile-frame--budget_master {
  border-color: rgba(84, 204, 168, 0.72);
  box-shadow: 0 0 0 1px rgba(84, 204, 168, 0.18);
}

.pf-profile-frame--dynasty {
  border-color: rgba(255, 204, 92, 0.72);
  box-shadow: 0 0 0 1px rgba(255, 204, 92, 0.18);
}

.pf-profile-showcase {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.pf-profile-showcase__summary strong,
.pf-profile-showcase__summary span {
  display: block;
}

.pf-profile-showcase__summary strong {
  color: var(--pf-gold);
  font-size: 1.3rem;
  line-height: 1;
}

.pf-profile-showcase__summary span {
  color: var(--pf-text-muted);
  font-size: 0.72rem;
}

.pf-profile-showcase__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.pf-profile-badge {
  max-width: 100%;
  padding: 4px 8px;
  border: 1px solid var(--pf-border);
  border-radius: 6px;
  color: var(--pf-text-heading);
  background: rgba(255, 255, 255, 0.06);
  font-size: 0.76rem;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.pf-profile-next {
  display: grid;
  gap: 5px;
  margin-top: 10px;
  text-align: left;
}

.pf-profile-next > span {
  color: var(--pf-text-heading);
  font-size: 0.82rem;
  font-weight: 700;
}

.pf-profile-next small {
  color: var(--pf-text-muted);
  font-size: 0.72rem;
}

.pf-profile-next__bar {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
}

.pf-profile-next__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #57d6b2, #79b8ff);
}
```

---

### Task 7: TMA Customization Page

**Files:**
- Create: `polemica-fantasy-webapp/src/pages/ProfileCustomizationPage.tsx`
- Modify: `polemica-fantasy-webapp/src/App.tsx`
- Modify: `polemica-fantasy-webapp/src/index.css`

- [ ] **Step 1: Add route**

In `App.tsx`:

```tsx
import { ProfileCustomizationPage } from './pages/ProfileCustomizationPage'
```

Add route:

```tsx
<Route path="/profile-customization" element={<ProfileCustomizationPage />} />
```

- [ ] **Step 2: Implement page state**

Create `ProfileCustomizationPage.tsx` with:
- `useProfileCustomization(initData)`
- local `profileFrameCode`
- local `featuredCodes`
- client validation `featuredCodes.length <= 5`
- toggling a badge appends to the end; clicking an already selected badge removes it.
- order controls: `↑`, `↓`, and remove `×` buttons for selected badges. Use text symbols already present in the app style; no new icon dependency is required for this narrow Stage 2 page.

Core save mutation:

```tsx
saveM.mutate({
  profileFrameCode,
  featuredAchievementCodes: featuredCodes,
})
```

On success, update local state from returned DTO.

- [ ] **Step 3: Render unlocked frame selector**

Frame controls:

```tsx
<button
  type="button"
  className={`pf-showcase-frame-option${profileFrameCode === null ? ' active' : ''}`}
  onClick={() => setProfileFrameCode(null)}
>
  Без рамки
</button>
{customization.unlockedFrames.map((frame) => (
  <button
    key={frame.code}
    type="button"
    className={`pf-showcase-frame-option${profileFrameCode === frame.code ? ' active' : ''}`}
    onClick={() => setProfileFrameCode(frame.code)}
  >
    {frame.name}
  </button>
))}
```

- [ ] **Step 4: Render selected and available achievements**

Selected list:

```tsx
{featuredCodes.map((code, index) => {
  const achievement = achievementByCode.get(code)
  if (!achievement) return null
  return (
    <li key={code} className="pf-showcase-selected__item">
      <span>{achievement.title}</span>
      <div>
        <button type="button" onClick={() => moveFeatured(index, -1)} disabled={index === 0}>↑</button>
        <button type="button" onClick={() => moveFeatured(index, 1)} disabled={index === featuredCodes.length - 1}>↓</button>
        <button type="button" onClick={() => removeFeatured(code)}>×</button>
      </div>
    </li>
  )
})}
```

Available grid:

```tsx
{customization.availableFeaturedAchievements.map((achievement) => {
  const selected = featuredCodes.includes(achievement.code)
  return (
    <button
      key={achievement.code}
      type="button"
      className={`pf-showcase-badge-option pf-showcase-badge-option--${rarityClass(achievement.rarity)}${selected ? ' active' : ''}`}
      disabled={!selected && featuredCodes.length >= 5}
      onClick={() => toggleFeatured(achievement.code)}
    >
      {achievement.title}
    </button>
  )
})}
```

- [ ] **Step 5: Add CSS**

Add compact full-width section styles:

```css
.pf-showcase-editor {
  display: grid;
  gap: 16px;
}

.pf-showcase-frame-options,
.pf-showcase-badge-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pf-showcase-frame-option,
.pf-showcase-badge-option {
  min-height: 36px;
  padding: 7px 10px;
  border: 1px solid var(--pf-border);
  border-radius: 6px;
  color: var(--pf-text);
  background: var(--pf-bg-elevated);
  font: inherit;
  font-size: 0.84rem;
}

.pf-showcase-frame-option.active,
.pf-showcase-badge-option.active {
  border-color: var(--pf-gold);
  color: var(--pf-text-heading);
}

.pf-showcase-selected {
  display: grid;
  gap: 8px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.pf-showcase-selected__item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--pf-border);
}

.pf-showcase-selected__item span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.pf-showcase-selected__item div {
  display: flex;
  gap: 4px;
}

.pf-showcase-selected__item button {
  width: 30px;
  height: 30px;
  border: 1px solid var(--pf-border);
  border-radius: 6px;
  color: var(--pf-text);
  background: rgba(255, 255, 255, 0.06);
}

.pf-showcase-actions {
  display: grid;
  gap: 8px;
}
```

- [ ] **Step 6: Run TMA build**

Run:

```bash
cd polemica-fantasy-webapp && npm run build
```

Expected: build succeeds.

---

### Task 8: Verification, Browser Check, And Memory

**Files:**
- Modify after implementation verification: `memory-bank/activeContext.md`
- Modify after implementation verification: `memory-bank/progress.md`

- [ ] **Step 1: Run backend targeted test**

```bash
cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.AchievementStage2ProfileShowcaseIntegrationTest"
```

Expected: PASS.

- [ ] **Step 2: Run backend compile**

```bash
cd polemica-fantasy-backend && ./gradlew compileKotlin compileTestKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run TMA build**

```bash
cd polemica-fantasy-webapp && npm run build
```

Expected: build succeeds.

- [ ] **Step 4: Run broad quick check if dependencies are available**

```bash
./scripts/codex-check.sh quick
```

Expected: quick check succeeds. If Docker/Testcontainers or local dependencies block it, record the exact failing command and run the targeted checks above instead.

- [ ] **Step 5: Run local TMA visual check if feasible**

Use project skill `.codex/skills/polemica-local-testing` for local stack startup and browser verification.

Minimum visual checks:
- Open own profile `/players/{currentTelegramId}` and confirm frame area, featured badges, achievement counter, next progress, share button, and `Настроить витрину` button do not overlap at mobile width.
- Open `/profile-customization`, select a frame, select 0-5 claimed badges, reorder them, save, and return to profile.
- Open another user profile and confirm there is no customization button.

- [ ] **Step 6: Update memory after implementation**

Append a dated note to `memory-bank/activeContext.md` and update `memory-bank/progress.md` with Stage 2 status and verification results. Preserve all existing dirty memory content.

---

## Reviewer Risk Callouts

- **Claimed vs completed:** The plan intentionally requires claimed featured achievements. Reviewers should reject an implementation that allows `COMPLETED_UNCLAIMED` badges in public featured slots.
- **Disabled after claim:** Featured disabled achievements must remain visible if claimed. Repository queries for featured rows must avoid `WHERE enabled = TRUE`.
- **Frame type validation:** `profileFrameCode` must validate against `user_cosmetic_unlock.cosmetic_type = 'PROFILE_FRAME'`. Badge-style unlocks are not frames.
- **Order preservation:** The API must preserve request order exactly and avoid unique collisions when reordering. Delete and flush before insert, or use an update strategy that cannot violate `ux_user_profile_featured_order`.
- **DTO contract sync:** Kotlin DTO additions, `polemica-fantasy-webapp/src/api/types.ts`, API hooks, and `PlayerProfilePage.tsx` must land together.
- **Next achievement policy:** The design does not define exact selection ranking. This plan defines one deterministic rule; reviewer should verify tests lock that rule and product accepts it.
- **Dirty worktree:** Stage 1 is uncommitted. Do not revert or rewrite existing Stage 1 files except narrow additive changes required for Stage 2.

## Status

DONE - plan written for Stage 2 profile showcase implementation.
