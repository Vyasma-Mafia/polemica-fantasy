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
    fun `GET achievements without Authorization returns 401`() {
        mockMvc.perform(get("/api/v1/achievements"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET achievements returns non-empty catalog from seed`() {
        val initData = buildSignedInitData(
            botToken = "test-token",
            authDate = Instant.now().epochSecond,
            userJson = """{"id":888903,"first_name":"AchCat"}""",
        )
        mockMvc.perform(
            get("/api/v1/achievements").header("Authorization", "tma $initData"),
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
            .andExpect(jsonPath("$.seriesRewards.length()").value(7))
            .andExpect(jsonPath("$.seriesRewards[0].fantiki").value(100))
            .andExpect(jsonPath("$.seriesRewards[0].label").value("Награда за 1 место"))
            .andExpect(jsonPath("$.marketplaceCommissionPercent").value(10))
            .andExpect(jsonPath("$.marketplaceMinPrices.COMMON").value(30))
            .andExpect(jsonPath("$.marketplaceMinPrices.LEGENDARY").value(250))
            .andExpect(jsonPath("$.marketplaceMaxPrices.COMMON").value(150))
            .andExpect(jsonPath("$.marketplaceMaxPrices.LEGENDARY").value(1500))
            .andExpect(jsonPath("$.minPackOpensBeforeMarketplacePurchase").value(3))
            .andExpect(jsonPath("$.cardValues.achievementBonus").value(10))
            .andExpect(jsonPath("$.cardValues.baseValues.COMMON").value(25))
            .andExpect(jsonPath("$.cardValues.baseValues.RARE").value(40))
            .andExpect(jsonPath("$.cardValues.baseValues.EPIC").value(80))
            .andExpect(jsonPath("$.cardValues.baseValues.LEGENDARY").value(370))
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
            .andExpect(jsonPath("$.achievementBonus").value(10))
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
            post("/api/v1/admin/card-templates/$epicTemplateId/achievements")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"achievementId":"sniper"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/achievements")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"achievementId":"voteForBlack"}"""),
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
                .content("""{"userCardId":$userCardId,"achievementId":"findSheriff"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rarity").value("LEGENDARY"))
            .andExpect(jsonPath("$.usesRemaining").value(5))
            .andExpect(jsonPath("$.achievements.length()").value(3))
            .andExpect(jsonPath("$.craftedByTelegramUserId").value(telegramUserId))

        mockMvc.perform(get("/api/v1/me").header("Authorization", tma))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantiki").value(600))
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
            post("/api/v1/admin/card-templates/$epicTemplateId/achievements")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"achievementId":"sniper"}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/achievements")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"achievementId":"voteForBlack"}"""),
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
                .content("""{"userCardId":$userCardId,"achievementId":"findSheriff"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rarity").value("LEGENDARY"))

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
    fun `POST legendary-upgrade rejects duplicate achievement on card`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Leg dup ach T","status":"DRAFT"}"""),
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
            post("/api/v1/admin/card-templates/$epicTemplateId/achievements")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"achievementId":"sniper"}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$epicTemplateId/achievements")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"achievementId":"voteForBlack"}"""),
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
                .content("""{"userCardId":$userCardId,"achievementId":"sniper"}"""),
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
            for (ach in listOf("sniper", "voteForBlack", "findSheriff")) {
                mockMvc.perform(
                    post("/api/v1/admin/card-templates/$tid/achievements")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"achievementId":"$ach"}"""),
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
