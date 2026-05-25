package io.github.mralex1810.fantasy

import com.jayway.jsonpath.JsonPath
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
class AchievementFutureIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `future achievement definitions are enabled and visible in user catalog`() {
        val tma = tmaAuth(930_000_001L, "FutureCatalog")

        mockMvc.perform(get("/api/v1/achievements").header("Authorization", tma))
            .andExpect(status().isOk)

        assertAchievement(tma, "market_buy_1", 0, "LOCKED")
        assertAchievement(tma, "market_watch_1", 0, "LOCKED")
        assertAchievement(tma, "share_profile_1", 0, "LOCKED")
        assertAchievement(tma, "legendary_upgrade_1", 0, "LOCKED")
    }

    @Test
    fun `marketplace buy sell watch and counterparty achievements progress from post launch facts only`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val sellerTelegramId = 930_000_010L
        val buyerTelegramId = 930_000_011L
        val sellerTma = tmaAuth(sellerTelegramId, "FutureSeller")
        val buyerTma = tmaAuth(buyerTelegramId, "FutureBuyer")
        val fixture = createMarketplaceFixture(auth, sellerTelegramId)
        ensureUser(sellerTma)
        ensureUser(buyerTma)
        jdbcTemplate.update("UPDATE telegram_user SET pack_opens_count = 3 WHERE telegram_id = ?", buyerTelegramId)

        val listingId = createListing(sellerTma, fixture.userCardIds[0], price = 30)
        val sellerInternalId = internalUserId(sellerTelegramId)
        val buyerInternalId = internalUserId(buyerTelegramId)
        insertSoldListing(
            sellerInternalId = sellerInternalId,
            buyerInternalId = buyerInternalId,
            userCardId = fixture.userCardIds[1],
            soldAtSql = "TIMESTAMP '2000-01-01 00:00:00'",
        )

        mockMvc.perform(post("/api/v1/marketplace/listings/$listingId/buy").header("Authorization", buyerTma))
            .andExpect(status().isOk)

        assertAchievement(buyerTma, "market_buy_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(buyerTma, "market_buy_5", 1, "IN_PROGRESS")
        assertAchievement(sellerTma, "market_sell_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(sellerTma, "market_sell_5", 1, "IN_PROGRESS")

        repeat(4) { index ->
            val counterpartyTelegramId = 930_000_020L + index
            ensureUser(tmaAuth(counterpartyTelegramId, "Counterparty$index"))
            insertSoldListing(
                sellerInternalId = sellerInternalId,
                buyerInternalId = internalUserId(counterpartyTelegramId),
                userCardId = fixture.userCardIds[1],
                soldAtSql = "now()",
            )
        }
        mockMvc.perform(
            post("/api/v1/settings/marketplace-watches")
                .header("Authorization", sellerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":${fixture.fantasyPlayerId}}"""),
        ).andExpect(status().isOk)

        assertAchievement(sellerTma, "market_watch_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(sellerTma, "market_unique_counterparties_5", 5, "COMPLETED_UNCLAIMED")
    }

    @Test
    fun `social achievements progress from tracked product events and ignore own profile views`() {
        val viewerTelegramId = 930_000_100L
        val tma = tmaAuth(viewerTelegramId, "FutureSocial")
        ensureUser(tma)

        recordProductEvent(tma, "SHARE_PROFILE", "PROFILE", 930_000_101L)
        recordProductEvent(tma, "SHARE_PROFILE", "PROFILE", 930_000_101L)
        recordProductEvent(tma, "SHARE_TEAM", "TEAM", 1L)
        recordProductEvent(tma, "COMPARE_OPEN", "SERIES_COMPARE", 1L)
        recordProductEvent(tma, "PUBLIC_PROFILE_VIEW", "PROFILE", viewerTelegramId)
        repeat(5) { index ->
            recordProductEvent(tma, "PUBLIC_PROFILE_VIEW", "PROFILE", 930_000_200L + index)
        }
        repeat(5) { index ->
            recordProductEvent(tma, "PUBLIC_PROFILE_VIEW", "PROFILE", 930_000_200L + index)
        }

        assertAchievement(tma, "share_profile_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "share_team_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "compare_open_1", 1, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "view_public_profile_5", 5, "COMPLETED_UNCLAIMED")
        assertSqlLong(
            """
            SELECT COUNT(*)
            FROM product_event
            WHERE telegram_user_id = ${internalUserId(viewerTelegramId)}
              AND event_type = 'PUBLIC_PROFILE_VIEW'
              AND subject_type = 'PROFILE'
              AND subject_id >= 930000200
            """.trimIndent(),
            5,
        )
    }

    @Test
    fun `legendary upgrade achievements use timestamped upgrade facts`() {
        val telegramId = 930_000_300L
        val tma = tmaAuth(telegramId, "FutureLegendary")
        ensureUser(tma)
        val internalId = internalUserId(telegramId)
        jdbcTemplate.update(
            """
            INSERT INTO user_legendary_upgrade_event (telegram_user_id, user_card_id, upgraded_at)
            VALUES (?, NULL, (SELECT tracking_started_at FROM achievement_definition WHERE code = 'legendary_upgrade_1') - interval '1 hour')
            """.trimIndent(),
            internalId,
        )
        repeat(3) {
            jdbcTemplate.update(
                "INSERT INTO user_legendary_upgrade_event (telegram_user_id, user_card_id, upgraded_at) VALUES (?, NULL, now())",
                internalId,
            )
        }
        recordProductEvent(tma, "COMPARE_OPEN", "SERIES_COMPARE", 1L)

        assertAchievement(tma, "legendary_upgrade_1", 3, "COMPLETED_UNCLAIMED")
        assertAchievement(tma, "crafted_legendary_3", 3, "COMPLETED_UNCLAIMED")
    }

    private fun createMarketplaceFixture(auth: String, sellerTelegramId: Long): MarketplaceFixture {
        val tournamentId = createTournament(auth, "Future marketplace")
        val fantasyPlayerId = createTournamentPlayer(auth, tournamentId, 930_100_010L, "FutureMarketPlayer")
        val templateId = createCardTemplate(auth, fantasyPlayerId, "COMMON")
        val userCardIds = giveCards(auth, sellerTelegramId, listOf(templateId, templateId))
        return MarketplaceFixture(fantasyPlayerId, userCardIds)
    }

    private fun createListing(tma: String, userCardId: Long, price: Long): Long {
        val json = mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":$price}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.parse(json).read<Number>("$.listingId").toLong()
    }

    private fun insertSoldListing(
        sellerInternalId: Long,
        buyerInternalId: Long,
        userCardId: Long,
        soldAtSql: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO marketplace_listing (seller_id, buyer_id, user_card_id, price, status, created_at, sold_at)
            VALUES (?, ?, ?, 30, 'SOLD', now(), $soldAtSql)
            """.trimIndent(),
            sellerInternalId,
            buyerInternalId,
            userCardId,
        )
    }

    private fun recordProductEvent(tma: String, eventType: String, subjectType: String, subjectId: Long) {
        mockMvc.perform(
            post("/api/v1/product-events")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"eventType":"$eventType","subjectType":"$subjectType","subjectId":$subjectId}"""),
        ).andExpect(status().isNoContent)
    }

    private fun ensureUser(tma: String) {
        mockMvc.perform(get("/api/v1/me").header("Authorization", tma)).andExpect(status().isOk)
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

    private fun internalUserId(telegramId: Long): Long =
        jdbcTemplate.queryForObject("SELECT id FROM telegram_user WHERE telegram_id = ?", Long::class.java, telegramId)!!

    private fun assertSqlLong(sql: String, expected: Long) {
        val actual = jdbcTemplate.queryForObject(sql, Long::class.java)
        check(actual == expected) { "Expected $expected for SQL [$sql], got $actual" }
    }

    private data class MarketplaceFixture(
        val fantasyPlayerId: Long,
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
