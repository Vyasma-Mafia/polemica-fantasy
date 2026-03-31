package io.github.mralex1810.fantasy

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class UserApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `GET me without Authorization returns 401`() {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET me cards with seriesId returns only cards for players on series roster`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Series roster filter T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val p1Json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":700001,"nickname":"InSeries"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tp1Id = Regex("\"id\"\\s*:\\s*(\\d+)").find(p1Json)!!.groupValues[1].toLong()
        val fp1Id = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(p1Json)!!.groupValues[1].toLong()

        val p2Json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":700002,"nickname":"NotInSeries"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fp2Id = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(p2Json)!!.groupValues[1].toLong()

        val ct1Response = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fp1Id,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val template1Id = Regex("\"id\"\\s*:\\s*(\\d+)").find(ct1Response)!!.groupValues[1].toLong()

        val ct2Response = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fp2Id,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val template2Id = Regex("\"id\"\\s*:\\s*(\\d+)").find(ct2Response)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Day 1","namePrefix":"D1","status":"UPCOMING",
                    "startsAt":"2030-06-01T12:00:00Z",
                    "teamDeadline":"2030-06-15T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(seriesJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[$tp1Id]}"""),
        ).andExpect(status().isOk)

        val telegramUserId = 888999L
        mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$template1Id,$template2Id]}"""),
        ).andExpect(status().isOk)

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"RosterTester"}""",
        )
        val tma = "tma $initData"

        mockMvc.perform(
            get("/api/v1/me/cards?tournamentId=$tournamentId&seriesId=$seriesId")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].fantasyPlayerId").value(fp1Id))

        mockMvc.perform(
            get("/api/v1/me/cards?tournamentId=$tournamentId")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[*].fantasyPlayerId", containsInAnyOrder(fp1Id.toInt(), fp2Id.toInt())))

        mockMvc.perform(
            get("/api/v1/me/cards?seriesId=${Long.MAX_VALUE}")
                .header("Authorization", tma),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET fantasy team details without team returns 404`() {
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888404,"first_name":"NoTeam"}""",
        )
        mockMvc.perform(
            get("/api/v1/me/fantasy-teams/999999/details")
                .header("Authorization", "tma $initData"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET me with valid tma initData returns profile`() {
        val botToken = "test-token"
        val initData = buildSignedInitData(
            botToken = botToken,
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888001,"first_name":"Tma","username":"tmauser"}""",
        )
        mockMvc.perform(
            get("/api/v1/me")
                .header("Authorization", "tma $initData"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.telegramId").value(888001))
            .andExpect(jsonPath("$.username").value("tmauser"))
            .andExpect(jsonPath("$.fantiki").value(1000))
    }

    @Test
    fun `GET economy-info returns numeric maps and series tiers from economy_config seed`() {
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888902,"first_name":"EconomyInfo"}""",
        )
        mockMvc.perform(
            get("/api/v1/me/economy-info").header("Authorization", "tma $initData"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.usesPerRarity.COMMON").value(2))
            .andExpect(jsonPath("$.usesPerRarity.LEGENDARY").value(5))
            .andExpect(jsonPath("$.recycleValues.EPIC").value(60))
            .andExpect(jsonPath("$.renewalCosts.RARE").value(60))
            .andExpect(jsonPath("$.maxRenewals").value(2))
            .andExpect(jsonPath("$.seriesRewards.length()").value(5))
            .andExpect(jsonPath("$.seriesRewards[0].fantiki").value(100))
            .andExpect(jsonPath("$.seriesRewards[0].label").value("Награда за 1 место"))
    }

    @Test
    fun `GET store packs and buy free pack returns cards`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Store T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":131313,"nickname":"StorePlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        ).andExpect(status().isOk)

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Freebie","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"COMMON","cardsCount":1}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packJson)!!.groupValues[1].toLong()

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888777,"first_name":"Buyer"}""",
        )
        val tma = "tma $initData"

        mockMvc.perform(get("/api/v1/store/packs").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(packId.toInt())))

        mockMvc.perform(
            post("/api/v1/store/packs/$packId/buy")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantiki").value(1000))
            .andExpect(jsonPath("$.cards.length()").value(1))
            .andExpect(jsonPath("$.cards[0].rarity").value("COMMON"))
    }

    @Test
    fun `POST fantasy team then PUT with reordered cards succeeds`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Fantasy update T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val tpIds = mutableListOf<Long>()
        val fpIds = mutableListOf<Long>()
        repeat(3) { i ->
            val pJson = mockMvc.perform(
                post("/api/v1/admin/tournaments/$tournamentId/players")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"polemicaUserId":${800001 + i},"nickname":"FTU$i"}"""),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
            tpIds.add(Regex("\"id\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong())
            fpIds.add(Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong())
        }

        val templateIds = fpIds.map { fpId ->
            val ct = mockMvc.perform(
                post("/api/v1/admin/card-templates")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fantasyPlayerId":$fpId,"rarity":"COMMON"}"""),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
            Regex("\"id\"\\s*:\\s*(\\d+)").find(ct)!!.groupValues[1].toLong()
        }

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Day A","namePrefix":"DA","status":"UPCOMING",
                    "startsAt":"2030-06-01T12:00:00Z",
                    "teamDeadline":"2030-06-15T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(seriesJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[${tpIds.joinToString()}]}"""),
        ).andExpect(status().isOk)

        val telegramUserId = 889001L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[${templateIds.joinToString()}]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val idRegex = Regex(""""id"\s*:\s*(\d+)""")
        val userCardIds = idRegex.findAll(giveJson).map { it.groupValues[1].toLong() }.toList()
        check(userCardIds.size == 3) { "expected 3 user card ids, got ${userCardIds.size} in $giveJson" }

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"FTeam"}""",
        )
        val tma = "tma $initData"

        mockMvc.perform(
            post("/api/v1/series/$seriesId/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"userCardIds":[${userCardIds.joinToString()}]}""",
                ),
        ).andExpect(status().isOk)

        val reordered = listOf(userCardIds[2], userCardIds[1], userCardIds[0])
        mockMvc.perform(
            put("/api/v1/series/$seriesId/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"userCardIds":[${reordered.joinToString()}]}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slots.length()").value(3))
            .andExpect(jsonPath("$.slots[0].slot").value(1))
            .andExpect(jsonPath("$.slots[0].userCardId").value(reordered[0]))
            .andExpect(jsonPath("$.slots[1].userCardId").value(reordered[1]))
            .andExpect(jsonPath("$.slots[2].userCardId").value(reordered[2]))
    }

    @Test
    fun `GET public fantasy team and details for other user telegramId`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Public FT T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val tpIds = mutableListOf<Long>()
        val fpIds = mutableListOf<Long>()
        repeat(3) { i ->
            val pJson = mockMvc.perform(
                post("/api/v1/admin/tournaments/$tournamentId/players")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"polemicaUserId":${810001 + i},"nickname":"PubFT$i"}"""),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
            tpIds.add(Regex("\"id\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong())
            fpIds.add(Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong())
        }

        val templateIds = fpIds.map { fpId ->
            val ct = mockMvc.perform(
                post("/api/v1/admin/card-templates")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fantasyPlayerId":$fpId,"rarity":"COMMON"}"""),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
            Regex("\"id\"\\s*:\\s*(\\d+)").find(ct)!!.groupValues[1].toLong()
        }

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Pub Day","namePrefix":"PD","status":"UPCOMING",
                    "startsAt":"2030-06-01T12:00:00Z",
                    "teamDeadline":"2030-06-15T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(seriesJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[${tpIds.joinToString()}]}"""),
        ).andExpect(status().isOk)

        val ownerTelegramId = 887701L
        val viewerTelegramId = 887702L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$ownerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[${templateIds.joinToString()}]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val idRegex = Regex(""""id"\s*:\s*(\d+)""")
        val userCardIds = idRegex.findAll(giveJson).map { it.groupValues[1].toLong() }.toList()
        check(userCardIds.size == 3) { "expected 3 user card ids, got ${userCardIds.size} in $giveJson" }

        val ownerInitData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$ownerTelegramId,"first_name":"OwnerPub"}""",
        )
        mockMvc.perform(
            post("/api/v1/series/$seriesId/fantasy-team")
                .header("Authorization", "tma $ownerInitData")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${userCardIds.joinToString()}]}"""),
        ).andExpect(status().isOk)

        val viewerInitData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$viewerTelegramId,"first_name":"ViewerPub"}""",
        )
        val viewerTma = "tma $viewerInitData"

        mockMvc.perform(
            get("/api/v1/series/$seriesId/users/$ownerTelegramId/fantasy-team")
                .header("Authorization", viewerTma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.owner.telegramId").value(ownerTelegramId))
            .andExpect(jsonPath("$.seriesId").value(seriesId))
            .andExpect(jsonPath("$.slots.length()").value(3))
            .andExpect(jsonPath("$.slots[0].slot").value(1))
            .andExpect(jsonPath("$.slots[0].card.id").exists())
            .andExpect(jsonPath("$.slots[0].card.playerNickname").exists())

        mockMvc.perform(
            get("/api/v1/series/$seriesId/users/$ownerTelegramId/fantasy-team/details")
                .header("Authorization", viewerTma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.seriesId").value(seriesId))
            .andExpect(jsonPath("$.columns.length()").value(3))

        mockMvc.perform(
            get("/api/v1/series/$seriesId/users/999888888888/fantasy-team")
                .header("Authorization", viewerTma),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            get("/api/v1/series/$seriesId/users/$viewerTelegramId/fantasy-team")
                .header("Authorization", viewerTma),
        ).andExpect(status().isNotFound)
    }

    private fun buildSignedInitData(botToken: String, authDate: Long, userJson: String): String {
        val userEncoded = java.net.URLEncoder.encode(userJson, StandardCharsets.UTF_8)
        val pairs = linkedMapOf(
            "auth_date" to authDate.toString(),
            "user" to userJson,
        )
        val dataCheckString = pairs.keys.sorted().joinToString("\n") { k -> "$k=${pairs[k]}" }
        val webAppDataKey = "WebAppData".toByteArray(StandardCharsets.UTF_8)
        val secretKey = hmacSha256(webAppDataKey, botToken.toByteArray(StandardCharsets.UTF_8))
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
