package io.github.mralex1810.fantasy

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import com.jayway.jsonpath.JsonPath
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import io.github.mralex1810.fantasy.repository.MarketplaceWatchPendingRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
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

    @Autowired
    private lateinit var marketplaceWatchPendingRepository: MarketplaceWatchPendingRepository

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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
    fun `GET me cards includes activeMarketplaceListing for listed card`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"MPL me cards T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":700201,"nickname":"MplListPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val ctResponse = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fpId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(ctResponse)!!.groupValues[1].toLong()

        val telegramUserId = 888_777_201L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"MplListUser"}""",
        )
        val tma = "tma $initData"

        val listingJson = mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":30}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val listingId = Regex("\"listingId\"\\s*:\\s*(\\d+)").find(listingJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            get("/api/v1/me/cards?tournamentId=$tournamentId")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(userCardId))
            .andExpect(jsonPath("$[0].activeMarketplaceListing.listingId").value(listingId))
            .andExpect(jsonPath("$[0].activeMarketplaceListing.price").value(30))
    }

    @Test
    fun `POST recycle succeeds for card listed on marketplace`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Recycle listed card T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":700202,"nickname":"RecycleListedPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val ctResponse = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fpId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(ctResponse)!!.groupValues[1].toLong()

        val telegramUserId = 888_777_202L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"RecycleListedUser"}""",
        )
        val tma = "tma $initData"

        val listingJson = mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":30}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val listingId = Regex("\"listingId\"\\s*:\\s*(\\d+)").find(listingJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            get("/api/v1/marketplace/my-listings")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].listingId").value(listingId))

        mockMvc.perform(
            post("/api/v1/me/cards/$userCardId/recycle")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantikiEarned").value(greaterThan(0)))
            .andExpect(jsonPath("$.newBalance").value(greaterThan(1000)))

        mockMvc.perform(
            get("/api/v1/me/cards?tournamentId=$tournamentId")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        mockMvc.perform(
            get("/api/v1/marketplace/my-listings")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        mockMvc.perform(
            get("/api/v1/user-cards/$userCardId/ownership-history")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].acquisitionType").value("ADMIN_GRANT"))
    }

    @Test
    fun `GET marketplace analytics detail returns active stats without server error`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"MPL analytics detail T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":700203,"nickname":"AnalyticsDetailPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val ctResponse = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fpId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(ctResponse)!!.groupValues[1].toLong()

        val telegramUserId = 888_777_203L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId,$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardIds = JsonPath.parse(giveJson).read<List<Number>>("$[*].id").map { it.toLong() }
        check(userCardIds.size == 2) { "expected 2 cards for analytics detail test, got ${userCardIds.size}" }

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"AnalyticsDetailSeller"}""",
        )
        val tma = "tma $initData"

        mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":${userCardIds[0]},"price":35}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":${userCardIds[1]},"price":80}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/marketplace/analytics/detail")
                .header("Authorization", tma)
                .param("fantasyPlayerId", fpId.toString())
                .param("rarity", "COMMON"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantasyPlayerId").value(fpId))
            .andExpect(jsonPath("$.rarity").value("COMMON"))
            .andExpect(jsonPath("$.activeCount").value(2))
            .andExpect(jsonPath("$.activeMinPrice").value(35))
            .andExpect(jsonPath("$.activeMaxPrice").value(80))
            .andExpect(jsonPath("$.recentSales", hasSize<Any>(0)))
            .andExpect(jsonPath("$.avgSalePrice").value(nullValue()))
    }

    @Test
    fun `GET marketplace analytics detail uses soldCardTemplate for rarity, not current userCard cardTemplate`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"MktAnalyticsRarity T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":700204,"nickname":"MktRarityPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val epicTemplateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(
            mockMvc.perform(
                post("/api/v1/admin/card-templates")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fantasyPlayerId":$fpId,"rarity":"EPIC"}"""),
            ).andExpect(status().isOk).andReturn().response.contentAsString,
        )!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"sniper"}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"voteForBlack"}"""),
        ).andExpect(status().isOk)

        val sellerTelegramId = 888_777_204L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$epicTemplateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()

        val sellerInitData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$sellerTelegramId,"first_name":"MktRaritySeller"}""",
        )
        val sellerTma = "tma $sellerInitData"

        val listingJson = mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", sellerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":200}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val epicListingId = Regex("\"listingId\"\\s*:\\s*(\\d+)").find(listingJson)!!.groupValues[1].toLong()

        val buyerTelegramId = 889_777_204L
        val buyerInitData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$buyerTelegramId,"first_name":"MktRarityBuyer"}""",
        )
        val buyerTma = "tma $buyerInitData"

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"MktRarityPack","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"COMMON","cardsCount":1}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packJson)!!.groupValues[1].toLong()

        repeat(3) {
            mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", buyerTma))
                .andExpect(status().isOk)
        }

        mockMvc.perform(post("/api/v1/marketplace/listings/$epicListingId/buy").header("Authorization", buyerTma))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/legendary-upgrade")
                .header("Authorization", buyerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"perkId":"findSheriff"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.card.rarity").value("LEGENDARY"))

        val legendaryListingJson = mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", buyerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":500}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val legendaryListingId = Regex("\"listingId\"\\s*:\\s*(\\d+)").find(legendaryListingJson)!!.groupValues[1].toLong()

        val buyer2TelegramId = 889_777_205L
        val buyer2InitData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$buyer2TelegramId,"first_name":"MktRarityBuyer2"}""",
        )
        val buyer2Tma = "tma $buyer2InitData"

        repeat(3) {
            mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", buyer2Tma))
                .andExpect(status().isOk)
        }

        mockMvc.perform(post("/api/v1/marketplace/listings/$legendaryListingId/buy").header("Authorization", buyer2Tma))
            .andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/marketplace/analytics/detail")
                .header("Authorization", sellerTma)
                .param("fantasyPlayerId", fpId.toString())
                .param("rarity", "EPIC"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantasyPlayerId").value(fpId))
            .andExpect(jsonPath("$.rarity").value("EPIC"))
            .andExpect(jsonPath("$.recentSales", hasSize<Any>(1)))
            .andExpect(jsonPath("$.recentSales[0].price").value(200))
            .andExpect(jsonPath("$.avgSalePrice").value(200))

        mockMvc.perform(
            get("/api/v1/marketplace/analytics/detail")
                .header("Authorization", sellerTma)
                .param("fantasyPlayerId", fpId.toString())
                .param("rarity", "LEGENDARY"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantasyPlayerId").value(fpId))
            .andExpect(jsonPath("$.rarity").value("LEGENDARY"))
            .andExpect(jsonPath("$.recentSales", hasSize<Any>(1)))
            .andExpect(jsonPath("$.recentSales[0].price").value(500))
            .andExpect(jsonPath("$.avgSalePrice").value(500))
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
    fun `PATCH me displayName persists and Telegram sync updates firstName only`() {
        val botToken = "test-token"
        val tid = 888_555_001L
        val initA = buildSignedInitData(
            botToken = botToken,
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$tid,"first_name":"Alpha","username":"al"}""",
        )
        val tmaA = "tma $initA"
        mockMvc.perform(
            patch("/api/v1/me")
                .header("Authorization", tmaA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"ИгровойНик"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("ИгровойНик"))
            .andExpect(jsonPath("$.firstName").value("Alpha"))

        val initB = buildSignedInitData(
            botToken = botToken,
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$tid,"first_name":"Beta","username":"al2"}""",
        )
        mockMvc.perform(
            get("/api/v1/me")
                .header("Authorization", "tma $initB"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("ИгровойНик"))
            .andExpect(jsonPath("$.firstName").value("Beta"))
            .andExpect(jsonPath("$.username").value("al2"))

        mockMvc.perform(
            patch("/api/v1/me")
                .header("Authorization", "tma $initB")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":null}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value(nullValue()))
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
    fun `GET perks without Authorization returns 401`() {
        mockMvc.perform(get("/api/v1/perks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET perks returns non-empty catalog from seed`() {
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888903,"first_name":"AchCat"}""",
        )
        mockMvc.perform(
            get("/api/v1/perks").header("Authorization", "tma $initData"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(greaterThan(0)))
            .andExpect(jsonPath("$[0].id").isString)
            .andExpect(jsonPath("$[0].name").isString)
            .andExpect(jsonPath("$[0].bonusPoints").isNumber)
            .andExpect(jsonPath("$[0].occurrenceType").isString)
            .andExpect(jsonPath("$[0].applicableRoles").isArray)
            .andExpect(jsonPath("$[0].canAppearOnRandomCards").isBoolean)
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
            .andExpect(jsonPath("$.seriesRewards.length()").value(8))
            .andExpect(jsonPath("$.seriesRewards[0].fantiki").value(250))
            .andExpect(jsonPath("$.seriesRewards[0].label").value("Награда за 1 место"))
            .andExpect(jsonPath("$.seriesRewards[6].fantiki").value(40))
            .andExpect(jsonPath("$.seriesRewards[6].label").value("Награда за 51–100 место"))
            .andExpect(jsonPath("$.seriesRewards[7].fantiki").value(30))
            .andExpect(jsonPath("$.seriesRewards[7].label").value("Награда за участие (101+ место)"))
            .andExpect(jsonPath("$.marketplaceCommissionPercent").value(10))
            .andExpect(jsonPath("$.marketplaceMinPrices.COMMON").value(30))
            .andExpect(jsonPath("$.marketplaceMinPrices.LEGENDARY").value(250))
            .andExpect(jsonPath("$.marketplaceMaxPrices.COMMON").value(150))
            .andExpect(jsonPath("$.marketplaceMaxPrices.LEGENDARY").value(1500))
            .andExpect(jsonPath("$.minPackOpensBeforeMarketplacePurchase").value(3))
            .andExpect(jsonPath("$.cardValues.perkBonus").value(10))
            .andExpect(jsonPath("$.cardValues.baseValues.COMMON").value(25))
            .andExpect(jsonPath("$.cardValues.baseValues.RARE").value(40))
            .andExpect(jsonPath("$.cardValues.baseValues.EPIC").value(80))
            .andExpect(jsonPath("$.cardValues.baseValues.LEGENDARY").value(370))
            .andExpect(jsonPath("$.leagues.BUDGET.rewardScale").value(75))
    }

    @Test
    fun `GET card-value info matches economy cardValues section`() {
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888905,"first_name":"CardValueInfo"}""",
        )
        mockMvc.perform(
            get("/api/v1/card-value/info").header("Authorization", "tma $initData"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.perkBonus").value(10))
            .andExpect(jsonPath("$.baseValues.COMMON").value(25))
            .andExpect(jsonPath("$.baseValues.LEGENDARY").value(370))
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
            .andExpect(jsonPath("$[?(@.id == $packId)].freeOpensRemaining").value(0))

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
    fun `store paid pack uses free opens then charges fantiki`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Store paid T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":141414,"nickname":"PaidPackPlayer"}"""),
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
                    {"name":"PaidWithFree","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":100,"freeOpensPerUser":2,"useAllTournamentPlayers":true,
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
            userJson = """{"id":888779,"first_name":"PaidBuyer"}""",
        )
        val tma = "tma $initData"

        mockMvc.perform(get("/api/v1/store/packs").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == $packId)].freeOpensRemaining").value(2))

        mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantiki").value(1000))

        mockMvc.perform(get("/api/v1/store/packs").header("Authorization", tma))
            .andExpect(jsonPath("$[?(@.id == $packId)].freeOpensRemaining").value(1))

        mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantiki").value(1000))

        mockMvc.perform(get("/api/v1/store/packs").header("Authorization", tma))
            .andExpect(jsonPath("$[?(@.id == $packId)].freeOpensRemaining").value(0))

        mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantiki").value(900))
    }

    @Test
    fun `choose pack buy creates pending and select materializes exactly once`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Choose pack T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        repeat(3) { idx ->
            mockMvc.perform(
                post("/api/v1/admin/tournaments/$tournamentId/players")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"polemicaUserId":${151510 + idx},"nickname":"ChoosePlayer$idx"}"""),
            )
                .andExpect(status().isOk)
        }

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"ChooseFree","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,"openingMode":"CHOOSE",
                    "priceFantiki":100,"freeOpensPerUser":1,"maxOpensPerUser":1,
                    "useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"COMMON","cardsCount":1}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openingMode").value("CHOOSE"))
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packJson)!!.groupValues[1].toLong()

        val telegramId = 888780L
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramId,"first_name":"ChooseBuyer"}""",
        )
        val tma = "tma $initData"

        mockMvc.perform(get("/api/v1/store/packs").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == $packId)].openingMode").value("CHOOSE"))
            .andExpect(jsonPath("$[?(@.id == $packId)].freeOpensRemaining").value(1))

        mockMvc.perform(
            post("/api/v1/admin/users/$telegramId/open-pack/$packId")
                .header("Authorization", auth),
        )
            .andExpect(status().isBadRequest)

        val firstBuy = mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind").value("PENDING_CHOICE"))
            .andExpect(jsonPath("$.fantiki").value(1000))
            .andExpect(jsonPath("$.cards.length()").value(0))
            .andExpect(jsonPath("$.choice.options.length()").value(3))
            .andReturn().response.contentAsString
        val choiceId = JsonPath.read<Int>(firstBuy, "$.choice.id").toLong()
        val optionId = JsonPath.read<String>(firstBuy, "$.choice.options[0].optionId")
        val otherOptionId = JsonPath.read<String>(firstBuy, "$.choice.options[1].optionId")
        val internalUserId = jdbcTemplate.queryForObject(
            "SELECT id FROM telegram_user WHERE telegram_id = ?",
            Long::class.java,
            telegramId,
        )!!

        assertSqlCount("SELECT COUNT(*) FROM user_card WHERE telegram_user_id = $internalUserId", 0)
        assertSqlCount("SELECT COUNT(*) FROM user_card_pack_open_event WHERE telegram_user_id = $internalUserId", 0)
        assertSqlCount("SELECT COALESCE(SUM(open_count), 0) FROM user_card_pack_opens WHERE telegram_user_id = $internalUserId", 0)
        assertSqlCount("SELECT free_opens_used FROM user_card_pack_free_usage WHERE telegram_user_id = $internalUserId AND card_pack_id = $packId", 1)

        val secondBuy = mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind").value("PENDING_CHOICE"))
            .andExpect(jsonPath("$.choice.id").value(choiceId.toInt()))
            .andReturn().response.contentAsString
        val repeatedOptionId = JsonPath.read<String>(secondBuy, "$.choice.options[0].optionId")
        org.assertj.core.api.Assertions.assertThat(repeatedOptionId).isEqualTo(optionId)
        assertSqlCount("SELECT free_opens_used FROM user_card_pack_free_usage WHERE telegram_user_id = $internalUserId AND card_pack_id = $packId", 1)

        mockMvc.perform(get("/api/v1/store/packs").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == $packId)].freeOpensRemaining").value(0))
            .andExpect(jsonPath("$[?(@.id == $packId)].packOpensUsed").value(1))
            .andExpect(jsonPath("$[?(@.id == $packId)].pendingChoice.id").value(choiceId.toInt()))

        val selectBody = """{"optionId":"$optionId"}"""
        mockMvc.perform(
            post("/api/v1/store/pack-choices/$choiceId/select")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content(selectBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind").value("OPENED"))
            .andExpect(jsonPath("$.cards.length()").value(1))
            .andExpect(jsonPath("$.openingCards.length()").value(1))
            .andExpect(jsonPath("$.cards[0].sourceCardPackId").value(packId.toInt()))

        assertSqlCount("SELECT COUNT(*) FROM user_card WHERE telegram_user_id = $internalUserId", 1)
        assertSqlCount("SELECT COUNT(*) FROM user_card_pack_open_event WHERE telegram_user_id = $internalUserId", 1)
        assertSqlCount("SELECT COALESCE(SUM(open_count), 0) FROM user_card_pack_opens WHERE telegram_user_id = $internalUserId", 1)
        assertSqlCount("SELECT pack_opens_count FROM telegram_user WHERE id = $internalUserId", 1)

        mockMvc.perform(
            post("/api/v1/store/pack-choices/$choiceId/select")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content(selectBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(1))
        assertSqlCount("SELECT COUNT(*) FROM user_card WHERE telegram_user_id = $internalUserId", 1)
        assertSqlCount("SELECT COUNT(*) FROM user_card_pack_open_event WHERE telegram_user_id = $internalUserId", 1)

        mockMvc.perform(
            post("/api/v1/store/pack-choices/$choiceId/select")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"optionId":"$otherOptionId"}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `store buy adds visual tyulenchik after developer card in openingCards`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Pack easter single T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":151501,"nickname":"DevSingle"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val developerFantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            put("/api/v1/admin/economy-config/easter_egg.developer_fantasy_player_id")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"$developerFantasyPlayerId"}"""),
        ).andExpect(status().isOk)

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"PackEasterSingle","tournamentId":$tournamentId,"active":true,
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
            userJson = """{"id":888790,"first_name":"PackSingle"}""",
        )
        val tma = "tma $initData"

        val buyJson = mockMvc.perform(
            post("/api/v1/store/packs/$packId/buy")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(1))
            .andExpect(jsonPath("$.openingCards.length()").value(2))
            .andExpect(jsonPath("$.openingCards[0].kind").value("USER_CARD"))
            .andExpect(jsonPath("$.openingCards[1].kind").value("COMPANION"))
            .andExpect(jsonPath("$.openingCards[1].companionCardName").value("Тюленчик"))
            .andReturn().response.contentAsString

        val root = JsonPath.parse(buyJson)
        val cardId = root.read<Number>("$.cards[0].id").toLong()
        val cardValue = root.read<Number>("$.cards[0].value").toLong()
        val cardRarity = root.read<String>("$.cards[0].rarity")
        val companionValue = root.read<Number>("$.openingCards[1].value").toLong()
        val companionRarity = root.read<String>("$.openingCards[1].rarity")
        val relatedUserCardId = root.read<Number>("$.openingCards[1].relatedUserCardId").toLong()
        val balanceAfterBuy = root.read<Number>("$.fantiki").toLong()

        check(companionValue == cardValue) {
            "expected companion value $companionValue to match developer card value $cardValue"
        }
        check(companionRarity == cardRarity) {
            "expected companion rarity $companionRarity to match developer card rarity $cardRarity"
        }
        check(relatedUserCardId == cardId) {
            "expected relatedUserCardId $relatedUserCardId to match card id $cardId"
        }
        check(balanceAfterBuy == 1000L + companionValue) {
            "expected balance to include companion bonus: ${1000L + companionValue}, got $balanceAfterBuy"
        }
    }

    @Test
    fun `store buy adds visual tyulenchik after each developer card in openingCards`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Pack easter multi T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":151502,"nickname":"DevMulti"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val developerFantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            put("/api/v1/admin/economy-config/easter_egg.developer_fantasy_player_id")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"$developerFantasyPlayerId"}"""),
        ).andExpect(status().isOk)

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"PackEasterMulti","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"RARE","cardsCount":3}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packJson)!!.groupValues[1].toLong()

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888791,"first_name":"PackMulti"}""",
        )
        val tma = "tma $initData"

        val buyJson = mockMvc.perform(
            post("/api/v1/store/packs/$packId/buy")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(3))
            .andExpect(jsonPath("$.openingCards.length()").value(6))
            .andReturn().response.contentAsString

        @Suppress("UNCHECKED_CAST")
        val root = JsonPath.parse(buyJson)
        val openingCards = root.read<List<Map<String, Any?>>>("$.openingCards")
        check(openingCards.size == 6) { "expected 6 opening cards, got ${openingCards.size}" }
        var totalCompanionBonus = 0L

        for (i in openingCards.indices step 2) {
            val userCardEntry = openingCards[i]
            val companionEntry = openingCards[i + 1]
            check(userCardEntry["kind"] == "USER_CARD") { "entry $i should be USER_CARD, got ${userCardEntry["kind"]}" }
            check(companionEntry["kind"] == "COMPANION") { "entry ${i + 1} should be COMPANION, got ${companionEntry["kind"]}" }

            @Suppress("UNCHECKED_CAST")
            val userCard = userCardEntry["card"] as Map<String, Any?>
            val userCardId = (userCard["id"] as Number).toLong()
            val userCardValue = (userCardEntry["value"] as Number).toLong()
            val userCardRarity = userCardEntry["rarity"] as String

            val companionRelatedUserCardId = (companionEntry["relatedUserCardId"] as Number).toLong()
            val companionValue = (companionEntry["value"] as Number).toLong()
            val companionRarity = companionEntry["rarity"] as String

            check(companionRelatedUserCardId == userCardId) {
                "companion should reference preceding user card id $userCardId, got $companionRelatedUserCardId"
            }
            check(companionValue == userCardValue) {
                "companion value $companionValue should match user card value $userCardValue"
            }
            check(companionRarity == userCardRarity) {
                "companion rarity $companionRarity should match user card rarity $userCardRarity"
            }
            totalCompanionBonus += companionValue
        }
        val balanceAfterBuy = root.read<Number>("$.fantiki").toLong()
        check(balanceAfterBuy == 1000L + totalCompanionBonus) {
            "expected balance to include all companion bonuses: ${1000L + totalCompanionBonus}, got $balanceAfterBuy"
        }
    }

    @Test
    fun `store buy from skinned pack assigns skin and exposes skinCode in card dto`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Skin pack T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":151503,"nickname":"SkinPackPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        ).andExpect(status().isOk)

        val skinsJson = mockMvc.perform(
            get("/api/v1/admin/card-skins").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        @Suppress("UNCHECKED_CAST")
        val skinRows = JsonPath.parse(skinsJson).read<List<Map<String, Any?>>>("$")
        val goldSkin = skinRows.first { it["code"] == "tournament_gold" }
        val skinId = (goldSkin["id"] as Number).toLong()
        val skinCode = goldSkin["code"] as String

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"SkinPack","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":0,"useAllTournamentPlayers":true,
                    "skinId":$skinId,
                    "rarityConfigs":[{"rarity":"COMMON","cardsCount":1}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.skinId").value(skinId))
            .andExpect(jsonPath("$.skinCode").value(skinCode))
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packJson)!!.groupValues[1].toLong()

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888792,"first_name":"SkinPackBuyer"}""",
        )
        val tma = "tma $initData"

        val buyJson = mockMvc.perform(
            post("/api/v1/store/packs/$packId/buy")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(1))
            .andExpect(jsonPath("$.cards[0].skinCode").value(skinCode))
            .andExpect(jsonPath("$.openingCards[0].kind").value("USER_CARD"))
            .andExpect(jsonPath("$.openingCards[0].card.skinCode").value(skinCode))
            .andReturn().response.contentAsString
        val openedCardId = JsonPath.parse(buyJson).read<Number>("$.cards[0].id").toLong()

        mockMvc.perform(
            get("/api/v1/me/cards?tournamentId=$tournamentId")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == $openedCardId)].skinCode").value(skinCode))
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
    fun `POST fantasy team rejects two cards for same fantasy player`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Dup player FT","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":800501,"nickname":"DupP0"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tpId = Regex("\"id\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val ctCommon = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fpId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateCommon = Regex("\"id\"\\s*:\\s*(\\d+)").find(ctCommon)!!.groupValues[1].toLong()

        val ctRare = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fpId,"rarity":"RARE"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateRare = Regex("\"id\"\\s*:\\s*(\\d+)").find(ctRare)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Dup Day","namePrefix":"DD","status":"UPCOMING",
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
                .content("""{"tournamentPlayerIds":[$tpId]}"""),
        ).andExpect(status().isOk)

        val telegramUserId = 889101L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateCommon,$templateRare]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val userCardIds = JsonPath.parse(giveJson).read<List<Number>>("$[*].id").map { it.toLong() }
        check(userCardIds.size == 2) { "expected 2 user card ids, got ${userCardIds.size} in $giveJson" }

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"DupFT"}""",
        )
        val tma = "tma $initData"

        mockMvc.perform(
            post("/api/v1/series/$seriesId/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"userCardIds":[${userCardIds[0]},${userCardIds[1]}]}""",
                ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST fantasy team accepts two cards for different fantasy players`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Two FP FT","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val tpIds = mutableListOf<Long>()
        val fpIds = mutableListOf<Long>()
        repeat(2) { i ->
            val pJson = mockMvc.perform(
                post("/api/v1/admin/tournaments/$tournamentId/players")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"polemicaUserId":${800601 + i},"nickname":"TwoFP$i"}"""),
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
                    {"name":"Two FP Day","namePrefix":"TFD","status":"UPCOMING",
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

        val telegramUserId = 889102L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[${templateIds.joinToString()}]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val userCardIds = JsonPath.parse(giveJson).read<List<Number>>("$[*].id").map { it.toLong() }
        check(userCardIds.size == 2) { "expected 2 user card ids, got ${userCardIds.size} in $giveJson" }

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"TwoFP"}""",
        )

        mockMvc.perform(
            post("/api/v1/series/$seriesId/fantasy-team")
                .header("Authorization", "tma $initData")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${userCardIds[0]},${userCardIds[1]}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slots.length()").value(2))
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

        mockMvc.perform(
            patch("/api/v1/me")
                .header("Authorization", "tma $ownerInitData")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"PubNick"}"""),
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
            .andExpect(jsonPath("$.owner.displayName").value("PubNick"))
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
            get("/api/v1/series/$seriesId/leaderboard")
                .header("Authorization", viewerTma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].user.displayName").value("PubNick"))

        mockMvc.perform(
            get("/api/v1/series/$seriesId/users/999888888888/fantasy-team")
                .header("Authorization", viewerTma),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            get("/api/v1/series/$seriesId/users/$viewerTelegramId/fantasy-team")
                .header("Authorization", viewerTma),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET legendary-upgrade info returns cost balance and canAfford`() {
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":889600,"first_name":"LegInfo"}""",
        )
        mockMvc.perform(
            get("/api/v1/legendary-upgrade/info")
                .header("Authorization", "tma $initData"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cost").value(400))
            .andExpect(jsonPath("$.balance").value(1000))
            .andExpect(jsonPath("$.canAfford").value(true))
            .andExpect(jsonPath("$.contractReissueDiscountPercent").value(15))
            .andExpect(jsonPath("$.costTiers[0].timesRenewed").value(0))
            .andExpect(jsonPath("$.costTiers[0].cost").value(400))
            .andExpect(jsonPath("$.costTiers[1].timesRenewed").value(1))
            .andExpect(jsonPath("$.costTiers[1].cost").value(340))
            .andExpect(jsonPath("$.costTiers[2].timesRenewed").value(2))
            .andExpect(jsonPath("$.costTiers[2].cost").value(280))
    }

    @Test
    fun `POST legendary-upgrade upgrades EPIC to LEGENDARY and sets craftedBy`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Legendary upgrade T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":910001,"nickname":"LegUpPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val epicTemplateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(
            mockMvc.perform(
                post("/api/v1/admin/card-templates")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fantasyPlayerId":$fpId,"rarity":"EPIC"}"""),
            ).andExpect(status().isOk).andReturn().response.contentAsString,
        )!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"sniper"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"voteForBlack"}"""),
        ).andExpect(status().isOk)

        val telegramUserId = 889601L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$epicTemplateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()
        jdbcTemplate.update("UPDATE user_card SET times_renewed = 2 WHERE id = ?", userCardId)

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"LegUp"}""",
        )
        val tma = "tma $initData"

        mockMvc.perform(
            post("/api/v1/legendary-upgrade")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"perkId":"findSheriff"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.card.rarity").value("LEGENDARY"))
            .andExpect(jsonPath("$.card.usesRemaining").value(5))
            .andExpect(jsonPath("$.card.timesRenewed").value(2))
            .andExpect(jsonPath("$.card.perks.length()").value(3))
            .andExpect(jsonPath("$.card.craftedByTelegramUserId").value(telegramUserId))

        mockMvc.perform(get("/api/v1/me").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantiki").value(720))
    }

    @Test
    fun `GET marketplace feed shows EPIC after purchased card upgraded to LEGENDARY`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Mkt sold snapshot T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":920101,"nickname":"MktFeedPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val epicTemplateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(
            mockMvc.perform(
                post("/api/v1/admin/card-templates")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fantasyPlayerId":$fpId,"rarity":"EPIC"}"""),
            ).andExpect(status().isOk).andReturn().response.contentAsString,
        )!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"sniper"}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"voteForBlack"}"""),
        ).andExpect(status().isOk)

        val sellerTelegramId = 889710L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$epicTemplateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()

        val sellerInitData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$sellerTelegramId,"first_name":"MktSell"}""",
        )

        val listingJson = mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", "tma $sellerInitData")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":120}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val listingId = Regex("\"listingId\"\\s*:\\s*(\\d+)").find(listingJson)!!.groupValues[1].toLong()

        val buyerTelegramId = 889711L
        val buyerInitData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$buyerTelegramId,"first_name":"MktBuy"}""",
        )
        val buyerTma = "tma $buyerInitData"

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"MktFeedPack","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"COMMON","cardsCount":1}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packJson)!!.groupValues[1].toLong()

        repeat(3) {
            mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", buyerTma))
                .andExpect(status().isOk)
        }

        mockMvc.perform(post("/api/v1/marketplace/listings/$listingId/buy").header("Authorization", buyerTma))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/legendary-upgrade")
                .header("Authorization", buyerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"perkId":"findSheriff"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.card.rarity").value("LEGENDARY"))

        val feedJson = mockMvc.perform(
            get("/api/v1/marketplace/feed")
                .param("limit", "50")
                .header("Authorization", buyerTma),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        @Suppress("UNCHECKED_CAST")
        val rows = JsonPath.parse(feedJson).read<List<Map<String, Any>>>(
            """$.items[?(@.playerName == 'MktFeedPlayer')]""",
        )
        check(rows.isNotEmpty()) { "expected feed row for MktFeedPlayer in $feedJson" }
        val epicRows = rows.filter { it["rarity"] == "EPIC" }
        check(epicRows.isNotEmpty()) {
            "expected at least one EPIC feed row for MktFeedPlayer (sold snapshot), got rarities=${rows.map { it["rarity"] }}"
        }
        val row = epicRows.first()
        val card = row["card"] as Map<*, *>
        check(card["rarity"] == "EPIC") { "expected feed card.rarity EPIC, got ${card["rarity"]}" }
    }

    @Test
    fun `POST legendary-upgrade rejects duplicate perk on card`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Leg dup perk T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":910002,"nickname":"DupAch"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val epicTemplateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(
            mockMvc.perform(
                post("/api/v1/admin/card-templates")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fantasyPlayerId":$fpId,"rarity":"EPIC"}"""),
            ).andExpect(status().isOk).andReturn().response.contentAsString,
        )!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"sniper"}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"voteForBlack"}"""),
        ).andExpect(status().isOk)

        val telegramUserId = 889602L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$epicTemplateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"DupA"}""",
        )

        mockMvc.perform(
            post("/api/v1/legendary-upgrade")
                .header("Authorization", "tma $initData")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"perkId":"sniper"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST fantasy team rejects more than one LEGENDARY per team`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Two leg T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val tpIds = mutableListOf<Long>()
        val fpIds = mutableListOf<Long>()
        repeat(2) { i ->
            val pJson = mockMvc.perform(
                post("/api/v1/admin/tournaments/$tournamentId/players")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"polemicaUserId":${920001 + i},"nickname":"Leg$i"}"""),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
            tpIds.add(Regex("\"id\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong())
            fpIds.add(Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong())
        }

        fun createLegendaryTemplate(fpId: Long): Long {
            val tid = Regex("\"id\"\\s*:\\s*(\\d+)").find(
                mockMvc.perform(
                    post("/api/v1/admin/card-templates")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"fantasyPlayerId":$fpId,"rarity":"LEGENDARY"}"""),
                ).andExpect(status().isOk).andReturn().response.contentAsString,
            )!!.groupValues[1].toLong()
            for (perk in listOf("sniper", "voteForBlack", "findSheriff")) {
                mockMvc.perform(
                    post("/api/v1/admin/card-templates/$tid/perks")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"perkId":"$perk"}"""),
                ).andExpect(status().isOk)
            }
            return tid
        }

        val legT1 = createLegendaryTemplate(fpIds[0])
        val legT2 = createLegendaryTemplate(fpIds[1])

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Leg day","namePrefix":"LD","status":"UPCOMING",
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

        val telegramUserId = 889603L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$legT1,$legT2]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val idRegex = Regex(""""id"\s*:\s*(\d+)""")
        val userCardIds = idRegex.findAll(giveJson).map { it.groupValues[1].toLong() }.toList()
        check(userCardIds.size == 2) { "expected 2 user card ids in $giveJson" }

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"TwoLeg"}""",
        )

        mockMvc.perform(
            post("/api/v1/series/$seriesId/fantasy-team")
                .header("Authorization", "tma $initData")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${userCardIds.joinToString()}]}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET tournaments series-open-for-team returns series with open deadline in active tournaments`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Open series home T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            put("/api/v1/admin/tournaments/$tournamentId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"ACTIVE"}"""),
        ).andExpect(status().isOk)

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Open Day","namePrefix":"OD","status":"UPCOMING",
                    "startsAt":"2031-06-01T12:00:00Z",
                    "teamDeadline":"2031-06-15T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(seriesJson)!!.groupValues[1].toLong()

        val telegramUserId = 777888001L
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"HomeOpen"}""",
        )

        mockMvc.perform(
            get("/api/v1/tournaments/series-open-for-team")
                .header("Authorization", "tma $initData"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(greaterThan(0))))
            .andExpect(jsonPath("$[0].seriesId").value(seriesId))
            .andExpect(jsonPath("$[0].tournamentId").value(tournamentId))
            .andExpect(jsonPath("$[0].tournamentName").value("Open series home T"))
            .andExpect(jsonPath("$[0].seriesName").value("Open Day"))
    }

    @Test
    fun `finalize series decrements uses by leagues count for same card`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Finalize leagues uses T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":990001,"nickname":"FinalizeLeaguePlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tpId = Regex("\"id\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()
        val fpId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val templateJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fpId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(templateJson)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Finalize leagues uses S","namePrefix":"FLU","status":"UPCOMING",
                    "startsAt":"2030-07-01T12:00:00Z",
                    "teamDeadline":"2030-07-15T12:00:00Z"}
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
                .content("""{"tournamentPlayerIds":[$tpId]}"""),
        ).andExpect(status().isOk)

        val telegramUserId = 889997701L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = JsonPath.parse(giveJson).read<List<Number>>("$[*].id").single().toLong()

        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"FinalizeLeagues"}""",
        )
        val tma = "tma $initData"
        val beforeFinalizeCardsJson = mockMvc.perform(
            get("/api/v1/me/cards?seriesId=$seriesId")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val initialUses = JsonPath.parse(beforeFinalizeCardsJson).read<Number>("$[0].usesRemaining").toInt()

        mockMvc.perform(
            post("/api/v1/series/$seriesId/leagues/MAIN/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[$userCardId]}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/series/$seriesId/leagues/BUDGET/fantasy-team")
                .header("Authorization", tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[$userCardId]}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/finalize")
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cardsDecremented").value(2))

        val expectedUses = (initialUses - 2).coerceAtLeast(0)
        mockMvc.perform(
            get("/api/v1/me/cards?seriesId=$seriesId")
                .header("Authorization", tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(userCardId.toInt()))
            .andExpect(jsonPath("$[0].usesRemaining").value(expectedUses))
    }

    @Test
    fun `card reserved in another non-finalized series cannot be submitted again`() {
        val fixture = createReservedUsesFixture("single", 889_997_801L, 990101L)
        jdbcTemplate.update("UPDATE user_card SET uses_remaining = 1 WHERE id = ?", fixture.userCardId)

        mockMvc.perform(
            post("/api/v1/series/${fixture.firstSeriesId}/leagues/MAIN/fantasy-team")
                .header("Authorization", fixture.tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${fixture.userCardId}]}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/me/cards?tournamentId=${fixture.tournamentId}&seriesId=${fixture.secondSeriesId}")
                .header("Authorization", fixture.tma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(fixture.userCardId.toInt()))
            .andExpect(jsonPath("$[0].canJoinMoreLeagues").value(false))

        mockMvc.perform(
            post("/api/v1/series/${fixture.secondSeriesId}/leagues/MAIN/fantasy-team")
                .header("Authorization", fixture.tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${fixture.userCardId}]}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `card with two uses can join two leagues in one series but not a third active league`() {
        val fixture = createReservedUsesFixture("dual", 889_997_802L, 990102L)

        mockMvc.perform(
            post("/api/v1/series/${fixture.firstSeriesId}/leagues/MAIN/fantasy-team")
                .header("Authorization", fixture.tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${fixture.userCardId}]}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/series/${fixture.firstSeriesId}/leagues/BUDGET/fantasy-team")
                .header("Authorization", fixture.tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${fixture.userCardId}]}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/series/${fixture.secondSeriesId}/leagues/MAIN/fantasy-team")
                .header("Authorization", fixture.tma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardIds":[${fixture.userCardId}]}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `GET marketplace listings hides seller and card value but my-listings keeps them`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Anon listings T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":991001,"nickname":"AnonPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()

        val templateJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(templateJson)!!.groupValues[1].toLong()

        val sellerTelegramId = 889_920_001L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()

        val sellerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$sellerTelegramId,"first_name":"AnonSeller"}""",
        )
        mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", sellerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":60}"""),
        ).andExpect(status().isOk)

        val viewerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":889920002,"first_name":"AnonViewer"}""",
        )
        mockMvc.perform(
            get("/api/v1/marketplace/listings")
                .header("Authorization", viewerTma)
                .param("fantasyPlayerId", fantasyPlayerId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].seller").value(nullValue()))
            .andExpect(jsonPath("$.content[0].card.value").value(nullValue()))

        mockMvc.perform(
            get("/api/v1/marketplace/my-listings")
                .header("Authorization", sellerTma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].seller.displayName").value("AnonSeller"))
            .andExpect(jsonPath("$[0].card.value").isNumber)
    }

    @Test
    fun `GET me cards and marketplace listings filter by any selected perk`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Perk filter T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        fun createPlayerTemplate(polemicaUserId: Long, nickname: String): Long {
            val playerJson = mockMvc.perform(
                post("/api/v1/admin/tournaments/$tournamentId/players")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"polemicaUserId":$polemicaUserId,"nickname":"$nickname"}"""),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
            val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()
            val templateJson = mockMvc.perform(
                post("/api/v1/admin/card-templates")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
            return Regex("\"id\"\\s*:\\s*(\\d+)").find(templateJson)!!.groupValues[1].toLong()
        }

        val sniperTemplateId = createPlayerTemplate(992_101L, "AchSniper")
        val voteTemplateId = createPlayerTemplate(992_102L, "AchVote")
        val plainTemplateId = createPlayerTemplate(992_103L, "AchPlain")
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$sniperTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"sniper"}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$voteTemplateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"voteForBlack"}"""),
        ).andExpect(status().isOk)

        val sellerTelegramId = 889_940_001L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$sniperTemplateId,$voteTemplateId,$plainTemplateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val cardIds = JsonPath.parse(giveJson).read<List<Number>>("$[*].id").map { it.toLong() }

        val sellerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$sellerTelegramId,"first_name":"AchSeller"}""",
        )

        mockMvc.perform(
            get("/api/v1/me/cards")
                .header("Authorization", sellerTma)
                .param("perkIds", "sniper")
                .param("perkIds", "voteForBlack"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[*].id", containsInAnyOrder(cardIds[0].toInt(), cardIds[1].toInt())))

        for ((index, cardId) in cardIds.withIndex()) {
            mockMvc.perform(
                post("/api/v1/marketplace/listings")
                    .header("Authorization", sellerTma)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userCardId":$cardId,"price":${60 + index}}"""),
            ).andExpect(status().isOk)
        }

        val viewerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":889940002,"first_name":"AchViewer"}""",
        )
        mockMvc.perform(
            get("/api/v1/marketplace/listings")
                .header("Authorization", viewerTma)
                .param("perkIds", "sniper")
                .param("perkIds", "voteForBlack"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", hasSize<Any>(2)))
            .andExpect(
                jsonPath(
                    "$.content[*].card.userCardId",
                    containsInAnyOrder(cardIds[0].toInt(), cardIds[1].toInt()),
                ),
            )
    }

    @Test
    fun `marketplace watches support perk filters and match listings by intersection`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Watch perk T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":992201,"nickname":"WatchAchPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()
        val templateJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(templateJson)!!.groupValues[1].toLong()
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$templateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"sniper"}"""),
        ).andExpect(status().isOk)

        val watcherTelegramId = 889_950_001L
        val otherWatcherTelegramId = 889_950_002L
        val watcherTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$watcherTelegramId,"first_name":"WatchOne"}""",
        )
        val otherWatcherTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$otherWatcherTelegramId,"first_name":"WatchTwo"}""",
        )

        mockMvc.perform(
            post("/api/v1/settings/marketplace-watches")
                .header("Authorization", watcherTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkIds":["voteForBlack","sniper"]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.perks", hasSize<Any>(2)))
            .andExpect(jsonPath("$.perks[*].id", containsInAnyOrder("sniper", "voteForBlack")))

        mockMvc.perform(
            post("/api/v1/settings/marketplace-watches")
                .header("Authorization", watcherTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkIds":["sniper","voteForBlack"]}"""),
        ).andExpect(status().isConflict)

        mockMvc.perform(
            post("/api/v1/settings/marketplace-watches")
                .header("Authorization", watcherTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkIds":["unknownPerk"]}"""),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/settings/marketplace-watches")
                .header("Authorization", otherWatcherTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkIds":["findSheriff"]}"""),
        ).andExpect(status().isOk)

        val sellerTelegramId = 889_950_003L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = JsonPath.parse(giveJson).read<Number>("$[0].id").toLong()
        val sellerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$sellerTelegramId,"first_name":"WatchSeller"}""",
        )
        mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", sellerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":60}"""),
        ).andExpect(status().isOk)

        val watcherInternalId = telegramUserRepository.findByTelegramId(watcherTelegramId)!!.id!!
        val otherWatcherInternalId = telegramUserRepository.findByTelegramId(otherWatcherTelegramId)!!.id!!
        waitForPendingCount(watcherInternalId, 1)
        waitForPendingCount(otherWatcherInternalId, 0)
    }

    @Test
    fun `marketplace transaction detail and complain endpoint work for sold listing`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Transaction detail T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":991002,"nickname":"TxPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()

        val templateJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(templateJson)!!.groupValues[1].toLong()

        val sellerTelegramId = 889_930_001L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()

        val sellerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$sellerTelegramId,"first_name":"TxSeller"}""",
        )
        val listingJson = mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", sellerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":120}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val listingId = Regex("\"listingId\"\\s*:\\s*(\\d+)").find(listingJson)!!.groupValues[1].toLong()

        val buyerTelegramId = 889_930_002L
        val buyerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$buyerTelegramId,"first_name":"TxBuyer"}""",
        )

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"TxBuyPack","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"COMMON","cardsCount":1}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packJson)!!.groupValues[1].toLong()
        repeat(3) {
            mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", buyerTma))
                .andExpect(status().isOk)
        }
        mockMvc.perform(post("/api/v1/marketplace/listings/$listingId/buy").header("Authorization", buyerTma))
            .andExpect(status().isOk)

        val observerTelegramId = 889_930_003L
        val observerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$observerTelegramId,"first_name":"TxObserver"}""",
        )
        mockMvc.perform(
            post("/api/v1/marketplace/transactions/$listingId/complain")
                .header("Authorization", observerTma),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.listingId").value(listingId))
            .andExpect(jsonPath("$.totalComplaints").value(1))
            .andExpect(jsonPath("$.remainingToday").value(4))

        mockMvc.perform(
            post("/api/v1/marketplace/transactions/$listingId/complain")
                .header("Authorization", observerTma),
        ).andExpect(status().isConflict)

        mockMvc.perform(
            get("/api/v1/marketplace/transactions/$listingId")
                .header("Authorization", observerTma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.listingId").value(listingId))
            .andExpect(jsonPath("$.price").value(120))
            .andExpect(jsonPath("$.commission").value(12))
            .andExpect(jsonPath("$.sellerReceived").value(108))
            .andExpect(jsonPath("$.seller.telegramId").value(sellerTelegramId))
            .andExpect(jsonPath("$.buyer.telegramId").value(buyerTelegramId))
            .andExpect(jsonPath("$.card.fantasyPlayerId").value(fantasyPlayerId))
            .andExpect(jsonPath("$.complaint.totalComplaints").value(1))
            .andExpect(jsonPath("$.complaint.userAlreadyComplained").value(true))
            .andExpect(jsonPath("$.sanction").value(nullValue()))
    }

    @Test
    fun `marketplace preserves skin code across listing feed transaction and purchased card`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Skin marketplace T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":991003,"nickname":"SkinTxPlayer"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()

        val templateJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(templateJson)!!.groupValues[1].toLong()

        val skinsJson = mockMvc.perform(
            get("/api/v1/admin/card-skins").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        @Suppress("UNCHECKED_CAST")
        val skinRows = JsonPath.parse(skinsJson).read<List<Map<String, Any?>>>("$")
        val goldSkin = skinRows.first { it["code"] == "tournament_gold" }
        val skinId = (goldSkin["id"] as Number).toLong()
        val skinCode = goldSkin["code"] as String

        val sellerTelegramId = 889_940_001L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId],"skinId":$skinId}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].telegramUserId").value(sellerTelegramId))
            .andReturn().response.contentAsString
        val userCardId = Regex("\"id\"\\s*:\\s*(\\d+)").find(giveJson)!!.groupValues[1].toLong()

        val sellerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$sellerTelegramId,"first_name":"SkinSeller"}""",
        )
        val listingJson = mockMvc.perform(
            post("/api/v1/marketplace/listings")
                .header("Authorization", sellerTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userCardId":$userCardId,"price":120}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val listingId = Regex("\"listingId\"\\s*:\\s*(\\d+)").find(listingJson)!!.groupValues[1].toLong()

        val viewerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":889940010,"first_name":"SkinViewer"}""",
        )
        mockMvc.perform(
            get("/api/v1/marketplace/listings")
                .header("Authorization", viewerTma)
                .param("fantasyPlayerId", fantasyPlayerId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].card.skinCode").value(skinCode))

        val buyerTelegramId = 889_940_002L
        val buyerTma = "tma " + buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$buyerTelegramId,"first_name":"SkinBuyer"}""",
        )

        val packJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"SkinTxPack","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[{"rarity":"COMMON","cardsCount":1}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packJson)!!.groupValues[1].toLong()
        repeat(3) {
            mockMvc.perform(post("/api/v1/store/packs/$packId/buy").header("Authorization", buyerTma))
                .andExpect(status().isOk)
        }

        val buyResultJson = mockMvc.perform(
            post("/api/v1/marketplace/listings/$listingId/buy").header("Authorization", buyerTma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.listing.card.skinCode").value(skinCode))
            .andExpect(jsonPath("$.card.skinCode").value(skinCode))
            .andReturn().response.contentAsString
        val purchasedCardId = JsonPath.parse(buyResultJson).read<Number>("$.card.id").toLong()

        val feedJson = mockMvc.perform(
            get("/api/v1/marketplace/feed")
                .param("limit", "50")
                .header("Authorization", buyerTma),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        @Suppress("UNCHECKED_CAST")
        val feedRows = JsonPath.parse(feedJson).read<List<Map<String, Any?>>>("$.items[?(@.listingId == $listingId)]")
        check(feedRows.isNotEmpty()) { "expected sold feed row for listing $listingId in $feedJson" }
        val soldCard = feedRows.first()["card"] as Map<*, *>
        check(soldCard["skinCode"] == skinCode) {
            "expected sold feed skinCode=$skinCode, got ${soldCard["skinCode"]}"
        }

        mockMvc.perform(
            get("/api/v1/marketplace/transactions/$listingId")
                .header("Authorization", buyerTma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.card.skinCode").value(skinCode))

        mockMvc.perform(
            get("/api/v1/me/cards")
                .header("Authorization", buyerTma),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == $purchasedCardId)].skinCode").value(skinCode))
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

    private fun waitForPendingCount(internalUserId: Long, expected: Int) {
        repeat(20) {
            val actual = marketplaceWatchPendingRepository.findAllByTelegramUser_Id(internalUserId).size
            if (actual == expected) return
            Thread.sleep(100)
        }
        val actual = marketplaceWatchPendingRepository.findAllByTelegramUser_Id(internalUserId).size
        check(actual == expected) {
            "Expected $expected marketplace watch pending rows for user $internalUserId, got $actual"
        }
    }

    private data class ReservedUsesFixture(
        val tournamentId: Long,
        val firstSeriesId: Long,
        val secondSeriesId: Long,
        val userCardId: Long,
        val tma: String,
    )

    private fun createReservedUsesFixture(
        suffix: String,
        telegramUserId: Long,
        polemicaUserId: Long,
    ): ReservedUsesFixture {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Reserved uses $suffix T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":$polemicaUserId,"nickname":"Reserved$suffix"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentPlayerId = Regex("\"id\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(playerJson)!!.groupValues[1].toLong()

        val templateJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(templateJson)!!.groupValues[1].toLong()

        fun createSeries(label: String): Long {
            val seriesJson = mockMvc.perform(
                post("/api/v1/admin/tournaments/$tournamentId/series")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Reserved uses $suffix $label","namePrefix":"RU$suffix$label","status":"UPCOMING",
                        "startsAt":"2030-08-01T12:00:00Z",
                        "teamDeadline":"2030-08-15T12:00:00Z"}
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
                    .content("""{"tournamentPlayerIds":[$tournamentPlayerId]}"""),
            ).andExpect(status().isOk)
            return seriesId
        }

        val firstSeriesId = createSeries("S1")
        val secondSeriesId = createSeries("S2")

        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramUserId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = JsonPath.parse(giveJson).read<List<Number>>("$[*].id").single().toLong()
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":$telegramUserId,"first_name":"Reserved$suffix"}""",
        )
        return ReservedUsesFixture(
            tournamentId = tournamentId,
            firstSeriesId = firstSeriesId,
            secondSeriesId = secondSeriesId,
            userCardId = userCardId,
            tma = "tma $initData",
        )
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun assertSqlCount(sql: String, expected: Long) {
        val actual = jdbcTemplate.queryForObject(sql, Number::class.java)?.toLong()
        org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected)
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
