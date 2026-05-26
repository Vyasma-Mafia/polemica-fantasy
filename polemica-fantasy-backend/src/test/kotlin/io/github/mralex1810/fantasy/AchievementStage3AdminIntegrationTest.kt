package io.github.mralex1810.fantasy

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AchievementStage3AdminIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Order(1)
    fun `admin dry run remains available and launch baseline remains zero in untouched seed`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(post("/api/v1/admin/achievements/backfill/dry-run").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.instantCompleted").value(0))
            .andExpect(jsonPath("$.instantFantikiLiability").value(0))
            .andExpect(jsonPath("$.rows", hasSize<Any>(EXPECTED_ACHIEVEMENT_COUNT.toInt())))
    }

    @Test
    @Order(2)
    fun `GET admin achievements requires basic auth and returns all definitions rewards and stats`() {
        mockMvc.perform(get("/api/v1/admin/achievements"))
            .andExpect(status().isUnauthorized)

        val response = mockMvc.perform(get("/api/v1/admin/achievements").header("Authorization", basicAuth("admin", "test-admin-secret")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.achievements", hasSize<Any>(EXPECTED_ACHIEVEMENT_COUNT.toInt())))
            .andExpect(jsonPath("$.achievements[*].code", hasItem("team_submit_1")))
            .andExpect(jsonPath("$.achievements[*].code", hasItem("market_buy_1")))
            .andReturn().response.contentAsString

        val teamSubmit = achievementByCode(response, "team_submit_1")
        assertThat(teamSubmit["conditionType"]).isEqualTo("TEAMS_SUBMITTED")
        assertThat(teamSubmit["targetValue"]).isEqualTo(1)
        assertThat((teamSubmit["rewards"] as List<*>).first()).isEqualTo(
            mapOf("type" to "FANTIKI", "amount" to 10, "code" to null, "metadata" to null, "displayOrder" to 10),
        )
        val marketBuy = achievementByCode(response, "market_buy_1")
        assertThat(marketBuy["enabled"]).isEqualTo(true)
        assertThat(marketBuy["trackingStartedAt"]).isNotNull()
        val stats = teamSubmit["stats"] as Map<*, *>
        assertThat(stats["completedUsers"]).isInstanceOf(Number::class.java)
        assertThat(stats["claimedUsers"]).isInstanceOf(Number::class.java)
        assertThat(stats["unclaimedUsers"]).isInstanceOf(Number::class.java)
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

        assertSqlLong(
            "SELECT COUNT(*) FROM achievement_reward ar JOIN achievement_definition d ON d.id = ar.achievement_id WHERE d.code = 'team_submit_5'",
            2,
        )
    }

    @Test
    @Order(4)
    fun `PATCH enabling dormant achievement sets trackingStartedAt once and disabling preserves it`() {
        val auth = basicAuth("admin", "test-admin-secret")
        jdbcTemplate.update(
            "UPDATE achievement_definition SET enabled = FALSE, tracking_started_at = NULL WHERE code = 'market_buy_1'",
        )
        assertSqlLong("SELECT COUNT(*) FROM achievement_definition WHERE code = 'market_buy_1' AND enabled = FALSE AND tracking_started_at IS NULL", 1)

        patchAchievement(auth, "market_buy_1", enabled = true)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.trackingStartedAt").isString)

        val firstTrackingStartedAt = trackingStartedAtText("market_buy_1")

        patchAchievement(auth, "market_buy_1", enabled = false)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.trackingStartedAt").isString)

        val secondTrackingStartedAt = trackingStartedAtText("market_buy_1")
        assertThat(secondTrackingStartedAt).isEqualTo(firstTrackingStartedAt)

        patchAchievement(auth, "market_buy_1", enabled = true)
            .andExpect(status().isOk)
        val thirdTrackingStartedAt = trackingStartedAtText("market_buy_1")
        assertThat(thirdTrackingStartedAt).isEqualTo(firstTrackingStartedAt)

        val enabledTrackingBefore = trackingStartedAtText("team_submit_1")
        patchAchievement(auth, "team_submit_1", enabled = true)
            .andExpect(status().isOk)
        val enabledTrackingAfter = trackingStartedAtText("team_submit_1")
        assertThat(enabledTrackingAfter).isEqualTo(enabledTrackingBefore)

        jdbcTemplate.update("UPDATE achievement_definition SET enabled = FALSE WHERE code = 'team_submit_5'")
        val disabledNonNullBefore = trackingStartedAtText("team_submit_5")
        patchAchievement(auth, "team_submit_5", enabled = false)
            .andExpect(status().isOk)
        val disabledNonNullAfter = trackingStartedAtText("team_submit_5")
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
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"CARD_SKIN_UNLOCK","amount":null,"code":"legacy_skin","metadata":null,"displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"RANDOM_CARD","amount":null,"code":null,"metadata":null,"displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"RANDOM_CARD","amount":1,"code":null,"metadata":"{\"rarity\":\"RARE\",\"count\":1,\"source\":\"ACTIVE_PACKS\"}","displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"RANDOM_CARD","amount":null,"code":"reward_code","metadata":"{\"rarity\":\"RARE\",\"count\":1,\"source\":\"ACTIVE_PACKS\"}","displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"RANDOM_CARD","amount":null,"code":null,"metadata":"{\"rarity\":\"LEGENDARY\",\"count\":1,\"source\":\"ACTIVE_PACKS\"}","displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"CARD_CHOICE_ROLL","amount":null,"code":null,"metadata":"{\"rarity\":\"RARE\",\"count\":2,\"options\":1,\"source\":\"ACTIVE_PACKS\"}","displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"RANDOM_CARD","amount":null,"code":null,"metadata":"{\"rarity\":\"RARE\",\"count\":1,\"options\":3,\"source\":\"ACTIVE_PACKS\"}","displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"CARD_CHOICE_ROLL","amount":null,"code":null,"metadata":"{\"rarity\":\"RARE\",\"count\":1,\"options\":3,\"skinCode\":\"unknown_skin\",\"source\":\"ACTIVE_PACKS\"}","displayOrder":10}]""")
            .andExpect(status().isBadRequest)
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"RANDOM_CARD","amount":null,"code":null,"metadata":"{\"rarity\":\"RARE\",\"count\":1,\"source\":\"ACTIVE_PACKS\"}","displayOrder":10}]""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rewards[0].type").value("RANDOM_CARD"))
        patchAchievementWithRewards(auth, "team_submit_15", """[{"type":"CARD_CHOICE_ROLL","amount":null,"code":null,"metadata":"{\"rarity\":\"EPIC\",\"count\":2,\"options\":5,\"skinCode\":\"tournament_gold\",\"source\":\"ACTIVE_PACKS\"}","displayOrder":10}]""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rewards[0].type").value("CARD_CHOICE_ROLL"))
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

        val response = mockMvc.perform(get("/api/v1/admin/achievements").header("Authorization", auth))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        @Suppress("UNCHECKED_CAST")
        val rewards = achievementByCode(response, "team_submit_1")["rewards"] as List<Map<String, Any?>>
        assertThat(rewards).hasSize(1)
        assertThat(rewards.first()["amount"]).isEqualTo(99)

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

    private fun trackingStartedAtText(code: String): String =
        jdbcTemplate.queryForObject(
            "SELECT tracking_started_at::text FROM achievement_definition WHERE code = ?",
            String::class.java,
            code,
        )!!

    private fun achievementByCode(response: String, code: String): Map<String, Any?> {
        val achievements = JsonPath.parse(response).read<List<Map<String, Any?>>>("$.achievements")
        return achievements.single { it["code"] == code }
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

        private const val EXPECTED_ACHIEVEMENT_COUNT = 81L
    }
}
