package io.github.mralex1810.fantasy

import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.greaterThan
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
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AchievementStage1IntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Order(1)
    fun `seed definitions have launch policy and stage one visibility`() {
        assertSqlLong("SELECT COUNT(*) FROM achievement_definition", 42)
        assertSqlLong(
            "SELECT COUNT(*) FROM achievement_definition WHERE history_policy = 'FROM_ACHIEVEMENTS_LAUNCH'",
            42,
        )
        assertSqlLong("SELECT COUNT(*) FROM achievement_definition WHERE enabled = TRUE", 42)
        assertSqlLong(
            "SELECT COUNT(*) FROM achievement_definition WHERE enabled = TRUE AND tracking_started_at IS NOT NULL",
            42,
        )
        assertSqlLong(
            "SELECT COUNT(*) FROM achievement_definition WHERE enabled = FALSE AND tracking_started_at IS NULL",
            0,
        )
    }

    @Test
    @Order(2)
    fun `GET achievements requires TMA auth and returns enabled catalog only`() {
        mockMvc.perform(get("/api/v1/achievements"))
            .andExpect(status().isUnauthorized)

        val tma = tmaAuth(910_000_001L, "CatalogUser")
        mockMvc.perform(get("/api/v1/achievements").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.totalVisible").value(42))
            .andExpect(jsonPath("$.summary.completed").value(0))
            .andExpect(jsonPath("$.summary.claimed").value(0))
            .andExpect(jsonPath("$.categories[*].code", containsInAnyOrder("PARTICIPATION", "BUDGET", "RESULTS", "COLLECTION", "PACKS", "MARKETPLACE", "SOCIAL")))
            .andExpect(jsonPath("$.categories[*].achievements[*].code", hasItem("team_submit_1")))
            .andExpect(jsonPath("$.categories[*].achievements[*].code", hasItem("pack_open_1")))
            .andExpect(jsonPath("$.categories[*].achievements[*].code", hasItem("market_buy_1")))
            .andExpect(jsonPath("$.categories[*].achievements[*].rewards[*].type", hasItem("FANTIKI")))
    }

    @Test
    @Order(3)
    fun `admin dry run requires basic auth and V1 seed has zero instant payout`() {
        mockMvc.perform(post("/api/v1/admin/achievements/backfill/dry-run"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/v1/admin/achievements/backfill/dry-run")
                .header("Authorization", basicAuth("admin", "test-admin-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.instantCompleted").value(0))
            .andExpect(jsonPath("$.instantFantikiLiability").value(0))
            .andExpect(jsonPath("$.rows", hasSize<Any>(42)))
    }

    @Test
    @Order(10)
    fun `team creation updates participation and budget achievements post commit`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createTeamFixture(auth, suffix = "team-progress")
        val tma = tmaAuth(fixture.telegramPlatformId, "TeamProgress")

        createFantasyTeam(tma, fixture.seriesId, "MAIN", fixture.userCardIds.take(1))
        assertAchievement(tma, "team_submit_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "budget_team_1", 0, "LOCKED")

        mockMvc.perform(
            put("/api/v1/series/${fixture.seriesId}/leagues/MAIN/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${fixture.userCardIds[1]}]}"""),
        ).andExpect(status().isOk)
        assertAchievement(tma, "team_submit_5", 1, "IN_PROGRESS")

        createFantasyTeam(tma, fixture.seriesId, "BUDGET", fixture.userCardIds.take(1))
        assertAchievement(tma, "budget_team_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "dual_league_1", 1, "COMPLETED_UNCLAIMED")

        jdbcTemplate.update(
            "UPDATE fantasy_team SET created_at = (SELECT tracking_started_at FROM achievement_definition WHERE code = 'team_submit_1') - interval '1 hour'",
        )
        mockMvc.perform(post("/api/v1/achievements/team_submit_5/claim").header("Authorization", tma))
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(20)
    fun `finalization sets finalizedAt and updates result achievements from leaderboard order`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createTeamFixture(auth, suffix = "results")
        val winnerTma = tmaAuth(fixture.telegramPlatformId, "Winner")
        val loserTelegramId = fixture.telegramPlatformId + 1
        val loserTma = tmaAuth(loserTelegramId, "Loser")
        giveCards(auth, loserTelegramId, fixture.templateIds)

        createFantasyTeam(winnerTma, fixture.seriesId, "MAIN", listOf(fixture.userCardIds[0]))
        createFantasyTeam(loserTma, fixture.seriesId, "MAIN", listOf(fixture.loserCardIds(auth, loserTelegramId)[0]))
        createFantasyTeam(winnerTma, fixture.seriesId, "BUDGET", listOf(fixture.userCardIds[0]))
        createFantasyTeam(loserTma, fixture.seriesId, "BUDGET", listOf(fixture.loserCardIds(auth, loserTelegramId)[1]))

        val winnerInternalId = internalUserId(fixture.telegramPlatformId)
        jdbcTemplate.update("UPDATE fantasy_team SET total_score = CASE WHEN telegram_user_id = ? THEN 100 ELSE 10 END", winnerInternalId)

        mockMvc.perform(post("/api/v1/admin/series/${fixture.seriesId}/finalize").header("Authorization", auth))
            .andExpect(status().isOk)

        assertSqlLong("SELECT COUNT(*) FROM series WHERE id = ${fixture.seriesId} AND finalized_at IS NOT NULL", 1)
        assertAchievement(winnerTma, "series_win_1", 2, "COMPLETED_UNCLAIMED")
        assertAchievement(winnerTma, "budget_win_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(winnerTma, "top3_5", 2, "IN_PROGRESS")
    }

    @Test
    @Order(30)
    fun `pack open inserts event and updates pack and collection achievements`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val packId = createAutoPack(auth, suffix = "pack-open", rarity = "EPIC")
        val telegramId = 910_000_030L
        val tma = tmaAuth(telegramId, "PackUser")

        mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards[0].rarity").value("EPIC"))

        assertSqlLong("SELECT COUNT(*) FROM user_card_pack_open_event WHERE telegram_user_id = ${internalUserId(telegramId)}", 1)
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_card uc
            JOIN card_template ct ON ct.id = uc.card_template_id
            WHERE uc.telegram_user_id = ${internalUserId(telegramId)}
              AND uc.deleted_at IS NULL
              AND ct.rarity = 'EPIC'
            """.trimIndent(),
            1,
        )
        assertAchievement(tma, "pack_open_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "first_epic", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "pack_epic_drop_1", 1, "COMPLETED_UNCLAIMED")
    }

    @Test
    @Order(31)
    fun `pack epic drop ignores old pack card after post launch open of same pack`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val packId = createAutoPack(auth, suffix = "old-pack-epic", rarity = "COMMON")
        val telegramId = 910_000_031L
        val tma = tmaAuth(telegramId, "OldPackEpic")
        val tournamentId = jdbcTemplate.queryForObject("SELECT tournament_id FROM card_pack WHERE id = ?", Long::class.java, packId)!!
        val fantasyPlayerId = createTournamentPlayer(auth, tournamentId, 920_031_001L, "OldEpicPlayer")
        val epicTemplateId = createCardTemplate(auth, fantasyPlayerId, "EPIC")
        val oldCardId = giveCards(auth, telegramId, listOf(epicTemplateId)).single()
        jdbcTemplate.update(
            """
            UPDATE user_card
            SET source_card_pack_id = ?,
                acquired_at = (
                    SELECT tracking_started_at AT TIME ZONE 'UTC'
                    FROM achievement_definition
                    WHERE code = 'pack_epic_drop_1'
                ) - interval '1 hour'
            WHERE id = ?
            """.trimIndent(),
            packId,
            oldCardId,
        )
        jdbcTemplate.update(
            """
            UPDATE user_card_ownership_history
            SET acquired_at = (
                SELECT tracking_started_at AT TIME ZONE 'UTC'
                FROM achievement_definition
                WHERE code = 'pack_epic_drop_1'
            ) - interval '1 hour'
            WHERE user_card_id = ?
            """.trimIndent(),
            oldCardId,
        )

        mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards[0].rarity").value("COMMON"))

        assertAchievement(tma, "pack_open_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "pack_epic_drop_1", 0, "LOCKED")
    }

    @Test
    @Order(40)
    fun `claim rejects incomplete achievement and rewards completed achievement once`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createTeamFixture(auth, suffix = "claim")
        val tma = tmaAuth(fixture.telegramPlatformId, "ClaimUser")

        mockMvc.perform(post("/api/v1/achievements/team_submit_5/claim").header("Authorization", tma))
            .andExpect(status().isBadRequest)

        createFantasyTeam(tma, fixture.seriesId, "MAIN", fixture.userCardIds.take(1))
        mockMvc.perform(post("/api/v1/achievements/team_submit_1/claim").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.achievementCode").value("team_submit_1"))
            .andExpect(jsonPath("$.fantikiDelta").value(10))
            .andExpect(jsonPath("$.newFantikiBalance", greaterThan(1000)))

        mockMvc.perform(post("/api/v1/achievements/team_submit_1/claim").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantikiDelta").value(0))

        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM fantiki_transaction ft
            JOIN telegram_user u ON u.id = ft.telegram_user_id
            WHERE u.telegram_id = ${fixture.telegramPlatformId}
              AND ft.reason = 'ACHIEVEMENT_REWARD'
              AND ft.amount = 10
            """.trimIndent(),
            1,
        )
    }

    @Test
    @Order(41)
    fun `claim writes badge style cosmetic unlock`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val fixture = createTeamFixture(auth, suffix = "badge-claim")
        val winnerTma = tmaAuth(fixture.telegramPlatformId, "BadgeWinner")
        val loserTelegramId = fixture.telegramPlatformId + 1
        val loserTma = tmaAuth(loserTelegramId, "BadgeLoser")
        val loserCardIds = giveCards(auth, loserTelegramId, fixture.templateIds)

        createFantasyTeam(winnerTma, fixture.seriesId, "MAIN", listOf(fixture.userCardIds[0]))
        createFantasyTeam(loserTma, fixture.seriesId, "MAIN", listOf(loserCardIds[0]))
        val winnerInternalId = internalUserId(fixture.telegramPlatformId)
        jdbcTemplate.update("UPDATE fantasy_team SET total_score = CASE WHEN telegram_user_id = ? THEN 100 ELSE 10 END", winnerInternalId)

        mockMvc.perform(post("/api/v1/admin/series/${fixture.seriesId}/finalize").header("Authorization", auth))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/achievements/series_win_1/claim").header("Authorization", winnerTma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cosmeticUnlocks[0].type").value("BADGE_STYLE"))
            .andExpect(jsonPath("$.cosmeticUnlocks[0].code").value("series_winner"))

        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM user_cosmetic_unlock
            WHERE telegram_user_id = $winnerInternalId
              AND cosmetic_type = 'BADGE_STYLE'
              AND cosmetic_code = 'series_winner'
              AND source_code = 'series_win_1'
            """.trimIndent(),
            1,
        )
    }

    @Test
    @Order(50)
    fun `admin dry run evaluates current aggregates and ignores old finalized series`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val liabilityFixture = createTeamFixture(auth, suffix = "dry-run-liability")
        val liabilityTma = tmaAuth(liabilityFixture.telegramPlatformId, "DryRunLiability")
        createFantasyTeam(liabilityTma, liabilityFixture.seriesId, "MAIN", listOf(liabilityFixture.userCardIds[0]))

        val beforeOldSeriesJson = mockMvc.perform(
            post("/api/v1/admin/achievements/backfill/dry-run")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesWinBefore = dryRunInstantCompleted(beforeOldSeriesJson, "series_win_1")

        val fixture = createTeamFixture(auth, suffix = "dry-run-old-finalized")
        val tma = tmaAuth(fixture.telegramPlatformId, "DryRunOld")
        createFantasyTeam(tma, fixture.seriesId, "MAIN", listOf(fixture.userCardIds[0]))

        jdbcTemplate.update(
            """
            UPDATE fantasy_team
            SET created_at = (SELECT tracking_started_at FROM achievement_definition WHERE code = 'series_win_1') - interval '1 hour',
                total_score = 100
            WHERE telegram_user_id = ?
            """.trimIndent(),
            internalUserId(fixture.telegramPlatformId),
        )
        jdbcTemplate.update(
            """
            UPDATE series
            SET status = 'FINISHED',
                finalized = TRUE,
                finalized_at = (SELECT tracking_started_at FROM achievement_definition WHERE code = 'series_win_1')
            WHERE id = ?
            """.trimIndent(),
            fixture.seriesId,
        )

        val response = mockMvc.perform(
            post("/api/v1/admin/achievements/backfill/dry-run")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.instantCompleted", greaterThan(0)))
            .andExpect(jsonPath("$.instantFantikiLiability", greaterThan(0)))
            .andReturn().response.contentAsString

        val rows = JsonPath.parse(response).read<List<Map<String, Any?>>>("$.rows")
        val teamSubmit1 = rows.single { it["code"] == "team_submit_1" }
        check((teamSubmit1["instantCompleted"] as Number).toLong() >= 1L) {
            "Expected team_submit_1 liability in $response"
        }
        val seriesWin1 = rows.single { it["code"] == "series_win_1" }
        check((seriesWin1["instantCompleted"] as Number).toLong() == seriesWinBefore) {
            "Old finalized series must not increase series_win_1 dry-run: before=$seriesWinBefore response=$response"
        }
    }

    private fun dryRunInstantCompleted(response: String, code: String): Long {
        val rows = JsonPath.parse(response).read<List<Map<String, Any?>>>("$.rows")
        return (rows.single { it["code"] == code }["instantCompleted"] as Number).toLong()
    }

    private fun assertAchievement(tma: String, code: String, progressValue: Int, state: String) {
        val json = mockMvc.perform(get("/api/v1/achievements").header("Authorization", tma))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val items = JsonPath.parse(json).read<List<Map<String, Any?>>>("$..achievements[?(@.code == '$code')]")
        check(items.size == 1) { "Expected one achievement $code in $json" }
        val item = items.first()
        check((item["progressValue"] as Number).toInt() == progressValue) {
            "Expected $code progress $progressValue, got ${item["progressValue"]}"
        }
        check(item["state"] == state) { "Expected $code state $state, got ${item["state"]}" }
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

    private fun TeamFixture.loserCardIds(auth: String, loserTelegramId: Long): List<Long> =
        giveCards(auth, loserTelegramId, templateIds)

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

    private fun createAutoPack(auth: String, suffix: String, rarity: String): Long {
        val tournamentId = createTournament(auth, "Pack T $suffix")
        createTournamentPlayer(auth, tournamentId, 920_000_000L + suffix.hashCode().toLong().mod(100_000L), "PackP$suffix")
        val json = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Pack $suffix","tournamentId":$tournamentId,"active":true,"autoGenerated":true,
                    "priceFantiki":0,"freeOpensPerUser":0,"maxOpensPerUser":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"$rarity","cardsCount":1}]}
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.id").toLong()
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
