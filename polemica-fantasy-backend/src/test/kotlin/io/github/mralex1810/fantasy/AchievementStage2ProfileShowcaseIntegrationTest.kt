package io.github.mralex1810.fantasy

import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AchievementStage2ProfileShowcaseIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun resetMutableAchievementDefinitions() {
        jdbcTemplate.update(
            """
            UPDATE achievement_definition
            SET enabled = TRUE,
                visibility = 'PUBLIC'
            WHERE code IN ('series_win_1', 'team_submit_1', 'team_submit_5')
            """.trimIndent(),
        )
    }

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
    fun `public rating and leaderboard users expose selected profile frame`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createClaimedAchievementFixture(auth, "public-lists-frame", "series_win_1")
        val tma = tmaAuth(fixture.telegramPlatformId, "PublicListsFrame")

        mockMvc.perform(putJson(tma, """{"profileFrameCode":"budget_master","featuredAchievementCodes":[]}"""))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/rating").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentUser.user.profileFrameCode").value("budget_master"))

        mockMvc.perform(
            get("/api/v1/series/${fixture.seriesId}/leaderboard")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].user.telegramId").value(fixture.telegramPlatformId))
            .andExpect(jsonPath("$[0].user.profileFrameCode").value("budget_master"))
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

    private fun putJson(tma: String, body: String) =
        put("/api/v1/me/profile-customization")
            .header("Authorization", tma)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun createClaimedAchievementFixture(auth: String, suffix: String, code: String): TeamFixture {
        val fixture = createTeamFixture(auth, suffix)
        val winnerTma = tmaAuth(fixture.telegramPlatformId, "Winner$suffix")
        val loserTelegramId = fixture.telegramPlatformId + 1
        val loserTma = tmaAuth(loserTelegramId, "Loser$suffix")
        val loserCardIds = giveCards(auth, loserTelegramId, fixture.templateIds)

        createFantasyTeam(winnerTma, fixture.seriesId, "MAIN", listOf(fixture.userCardIds[0]))
        createFantasyTeam(loserTma, fixture.seriesId, "MAIN", listOf(loserCardIds[0]))
        jdbcTemplate.update(
            "UPDATE fantasy_team SET total_score = CASE WHEN telegram_user_id = ? THEN 100 ELSE 10 END",
            internalUserId(fixture.telegramPlatformId),
        )

        mockMvc.perform(post("/api/v1/admin/series/${fixture.seriesId}/finalize").header("Authorization", auth))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/achievements/$code/claim").header("Authorization", winnerTma))
            .andExpect(status().isOk)

        insertProfileFrameUnlock(fixture.telegramPlatformId, "budget_master", code)
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_achievement ua
            JOIN achievement_definition d ON d.id = ua.achievement_id
            WHERE ua.telegram_user_id = ${internalUserId(fixture.telegramPlatformId)}
              AND d.code = '$code'
              AND ua.claimed_at IS NOT NULL
            """.trimIndent(),
            1,
        )
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_cosmetic_unlock
            WHERE telegram_user_id = ${internalUserId(fixture.telegramPlatformId)}
              AND cosmetic_type = 'PROFILE_FRAME'
              AND cosmetic_code = 'budget_master'
            """.trimIndent(),
            1,
        )
        return fixture
    }

    private fun createTwoClaimedAchievementsFixture(
        auth: String,
        suffix: String,
        firstCode: String,
        secondCode: String,
    ): TeamFixture {
        val fixture = createTeamFixture(auth, suffix)
        val winnerTma = tmaAuth(fixture.telegramPlatformId, "Winner$suffix")
        val loserTelegramId = fixture.telegramPlatformId + 1
        val loserTma = tmaAuth(loserTelegramId, "Loser$suffix")
        val loserCardIds = giveCards(auth, loserTelegramId, fixture.templateIds)

        createFantasyTeam(winnerTma, fixture.seriesId, "MAIN", listOf(fixture.userCardIds[0]))
        mockMvc.perform(post("/api/v1/achievements/$firstCode/claim").header("Authorization", winnerTma))
            .andExpect(status().isOk)

        createFantasyTeam(loserTma, fixture.seriesId, "MAIN", listOf(loserCardIds[0]))
        jdbcTemplate.update(
            "UPDATE fantasy_team SET total_score = CASE WHEN telegram_user_id = ? THEN 100 ELSE 10 END",
            internalUserId(fixture.telegramPlatformId),
        )
        mockMvc.perform(post("/api/v1/admin/series/${fixture.seriesId}/finalize").header("Authorization", auth))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/achievements/$secondCode/claim").header("Authorization", winnerTma))
            .andExpect(status().isOk)

        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_achievement ua
            JOIN achievement_definition d ON d.id = ua.achievement_id
            WHERE ua.telegram_user_id = ${internalUserId(fixture.telegramPlatformId)}
              AND d.code IN ('$firstCode', '$secondCode')
              AND ua.claimed_at IS NOT NULL
            """.trimIndent(),
            2,
        )
        return fixture
    }

    private fun createTeamFixture(auth: String, suffix: String): TeamFixture {
        val tournamentId = createTournament(auth, "Achievements $suffix")
        val fpIds = (1..2).map { index ->
            createTournamentPlayer(auth, tournamentId, 910_100_000L + suffix.hashCode().toLong().mod(100_000L) * 10 + index, "P$suffix$index")
        }
        val templateIds = fpIds.map { createCardTemplate(auth, it, "COMMON") }
        val telegramId = 910_000_000L + suffix.hashCode().toLong().mod(100_000L)
        val userCardIds = giveCards(auth, telegramId, templateIds)
        val seriesId = createSeries(auth, tournamentId, "Series $suffix")
        val tournamentPlayerIds = fpIds.map { fantasyPlayerId ->
            jdbcTemplate.queryForObject(
                "SELECT id FROM tournament_player WHERE tournament_id = ? AND fantasy_player_id = ?",
                Long::class.java,
                tournamentId,
                fantasyPlayerId,
            )!!
        }
        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[${tournamentPlayerIds.joinToString(",")}]}"""),
        ).andExpect(status().isOk)
        return TeamFixture(telegramId, seriesId, templateIds, userCardIds)
    }

    private fun createTournament(auth: String, name: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","status":"DRAFT"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.id").toLong()
    }

    private fun createTournamentPlayer(auth: String, tournamentId: Long, polemicaUserId: Long, nickname: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":$polemicaUserId,"nickname":"$nickname"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.fantasyPlayerId").toLong()
    }

    private fun createCardTemplate(auth: String, fantasyPlayerId: Long, rarity: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"$rarity"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.id").toLong()
    }

    private fun giveCards(auth: String, telegramId: Long, templateIds: List<Long>): List<Long> {
        val json = mockMvc.perform(
            post("/api/v1/admin/users/$telegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[${templateIds.joinToString(",")}]}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<List<Number>>("$[*].id").map { it.toLong() }
    }

    private fun createSeries(auth: String, tournamentId: Long, name: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"$name","namePrefix":"$name","status":"UPCOMING",
                    "startsAt":"2030-06-01T12:00:00Z",
                    "teamDeadline":"2030-06-15T12:00:00Z"}
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.id").toLong()
    }

    private fun createFantasyTeam(tma: String, seriesId: Long, leagueCode: String, userCardIds: List<Long>) {
        mockMvc.perform(
            post("/api/v1/series/$seriesId/leagues/$leagueCode/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${userCardIds.joinToString(",")}]}"""),
        ).andExpect(status().isOk)
    }

    private fun insertProfileFrameUnlock(telegramId: Long, code: String, sourceCode: String) {
        jdbcTemplate.update(
            """
            INSERT INTO user_cosmetic_unlock (telegram_user_id, cosmetic_type, cosmetic_code, source_type, source_code, unlocked_at)
            VALUES (?, 'PROFILE_FRAME', ?, 'ACHIEVEMENT', ?, now())
            ON CONFLICT (telegram_user_id, cosmetic_type, cosmetic_code) DO NOTHING
            """.trimIndent(),
            internalUserId(telegramId),
            code,
            sourceCode,
        )
    }

    private fun internalUserId(telegramId: Long): Long =
        jdbcTemplate.queryForObject("SELECT id FROM telegram_user WHERE telegram_id = ?", Long::class.java, telegramId)!!

    private fun assertSqlLong(sql: String, expected: Long) {
        val actual = jdbcTemplate.queryForObject(sql, Long::class.java)
        check(actual == expected) { "Expected $expected for SQL [$sql], got $actual" }
    }

    private data class TeamFixture(
        val telegramPlatformId: Long,
        val seriesId: Long,
        val templateIds: List<Long>,
        val userCardIds: List<Long>,
    )

    private fun tmaAuth(telegramId: Long, firstName: String): String {
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramId,"first_name":"$firstName"}""",
        )
        return "tma $initData"
    }

    private fun buildSignedInitData(botToken: String, authDate: Long, userJson: String): String {
        val userEncoded = URLEncoder.encode(userJson, StandardCharsets.UTF_8)
        val pairs = linkedMapOf(
            "auth_date" to authDate.toString(),
            "user" to userJson,
        )
        val dataCheckString = pairs.keys.sorted().joinToString("\n") { k -> "$k=${pairs[k]}" }
        val secretKey = hmacSha256("WebAppData".toByteArray(StandardCharsets.UTF_8), botToken.toByteArray(StandardCharsets.UTF_8))
        val hashBytes = hmacSha256(secretKey, dataCheckString.toByteArray(StandardCharsets.UTF_8))
        val hashHex = hashBytes.joinToString("") { b -> "%02x".format(b) }
        return "auth_date=$authDate&user=$userEncoded&hash=$hashHex"
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        private fun basicAuth(user: String, password: String): String {
            val token = Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
            return "Basic $token"
        }
    }
}
