package io.github.mralex1810.fantasy

import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.MarketplaceComplaint
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.DeadlineReminderRepository
import io.github.mralex1810.fantasy.repository.MarketplaceComplaintRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.Base64

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AdminApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var deadlineReminderRepository: DeadlineReminderRepository

    @Autowired
    private lateinit var marketplaceListingRepository: MarketplaceListingRepository

    @Autowired
    private lateinit var userCardRepository: UserCardRepository

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Autowired
    private lateinit var marketplaceComplaintRepository: MarketplaceComplaintRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Order(1)
    fun `admin tournaments without auth returns 401`() {
        mockMvc.perform(get("/api/v1/admin/tournaments"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @Order(2)
    fun `create tournament and list with basic auth`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Integration Cup","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Integration Cup"))
            .andExpect(jsonPath("$.kind").value("STANDALONE"))

        mockMvc.perform(
            get("/api/v1/admin/tournaments").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Integration Cup"))
    }

    @Test
    @Order(27)
    fun `tournament expected game default is inherited overridden updated and cleared`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Default Games Tournament","status":"DRAFT","defaultExpectedGameCount":5}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultExpectedGameCount").value(5))
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            get("/api/v1/admin/tournaments/$tournamentId").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultExpectedGameCount").value(5))

        val inheritedSeriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Defaulted 101","namePrefix":"DG101","status":"SCORING",
                    "startsAt":"2030-06-01T12:00:00Z","teamDeadline":"2030-06-01T11:00:00Z",
                    "expectedGameCount":null}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expectedGameCount").value(5))
            .andReturn().response.contentAsString
        val inheritedSeriesId = Regex("\"id\"\\s*:\\s*(\\d+)")
            .find(inheritedSeriesJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Override 102","namePrefix":"DG102","status":"UPCOMING",
                    "startsAt":"2030-06-02T12:00:00Z","teamDeadline":"2030-06-02T11:00:00Z",
                    "expectedGameCount":7}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expectedGameCount").value(7))

        mockMvc.perform(
            put("/api/v1/admin/tournaments/$tournamentId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Default Games Tournament Renamed"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultExpectedGameCount").value(5))

        mockMvc.perform(
            put("/api/v1/admin/tournaments/$tournamentId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultExpectedGameCount":1000}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultExpectedGameCount").value(1000))

        mockMvc.perform(
            put("/api/v1/admin/tournaments/$tournamentId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultExpectedGameCount":0}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            put("/api/v1/admin/tournaments/$tournamentId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultExpectedGameCount":1001}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/v1/admin/series/$inheritedSeriesId").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expectedGameCount").value(5))

        mockMvc.perform(
            put("/api/v1/admin/tournaments/$tournamentId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultExpectedGameCount":null}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultExpectedGameCount").value(nullValue()))

        mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"No Default 103","namePrefix":"DG103","status":"UPCOMING",
                    "startsAt":"2030-06-03T12:00:00Z","teamDeadline":"2030-06-03T11:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expectedGameCount").value(nullValue()))

        mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Scoring Without Default 104","namePrefix":"DG104","status":"SCORING",
                    "startsAt":"2030-06-04T12:00:00Z","teamDeadline":"2030-06-04T11:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(1001)
    fun `admin me endpoint returns roles`() {
        val adminAuth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(get("/api/v1/admin/me").header("Authorization", adminAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"))

        val moderatorAuth = basicAuth("moderator", "test-moderator-secret")
        mockMvc.perform(get("/api/v1/admin/me").header("Authorization", moderatorAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("moderator"))
            .andExpect(jsonPath("$.role").value("MODERATOR"))
    }

    @Test
    @Order(1002)
    fun `moderator can access tournament series league and polemica endpoints`() {
        val auth = basicAuth("moderator", "test-moderator-secret")
        mockMvc.perform(get("/api/v1/admin/tournaments").header("Authorization", auth))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/admin/series/999").header("Authorization", auth))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/admin/leagues").header("Authorization", auth))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/admin/polemica/competitions/999").header("Authorization", auth))
            .andExpect(status().isNotFound)
    }

    @Test
    @Order(1003)
    fun `moderator cannot access users cards packs economy perks marketplace or notifications`() {
        val auth = basicAuth("moderator", "test-moderator-secret")
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", auth))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/v1/admin/card-templates").header("Authorization", auth))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/v1/admin/card-packs").header("Authorization", auth))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/v1/admin/economy-config").header("Authorization", auth))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/v1/admin/perks").header("Authorization", auth))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/v1/admin/marketplace/pair-analysis").header("Authorization", auth))
            .andExpect(status().isForbidden)
        mockMvc.perform(
            post("/api/v1/admin/notifications/broadcast")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"test"}"""),
        )
            .andExpect(status().isForbidden)
        mockMvc.perform(
            post("/api/v1/admin/notifications/direct")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramUserId":123,"text":"test"}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    @Order(3)
    fun `sync games returns 404 for missing series before credential validation`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/series/999/sync-games").header("Authorization", auth),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    @Order(4)
    fun `give cards smoke`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Smoke T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":424242,"nickname":"Player"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val ctJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(ctJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/users/777001/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].cardTemplateId").value(templateId))
            .andExpect(jsonPath("$[0].telegramUserId").value(777001))
    }

    @Test
    @Order(5)
    fun `list series by tournament get series by id and list card packs`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Series Read T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            get("/api/v1/admin/tournaments/$tournamentId/series").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Round 1","namePrefix":"R1","status":"UPCOMING",
                    "startsAt":"2025-06-01T12:00:00Z","teamDeadline":"2025-06-10T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(seriesJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            get("/api/v1/admin/tournaments/$tournamentId/series").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Round 1"))

        mockMvc.perform(
            get("/api/v1/admin/series/$seriesId").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.namePrefix").value("R1"))

        mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":900001,"nickname":"PackPool"}"""),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/admin/card-packs").header("Authorization", auth),
        )
            .andExpect(status().isOk)

        val packCreateJson = mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Starter","tournamentId":$tournamentId,"active":true,
                    "autoGenerated":true,
                    "priceFantiki":0,"useAllTournamentPlayers":true,
                    "rarityConfigs":[
                      {"rarity":"COMMON","cardsCount":1}
                    ]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val packId = Regex("\"id\"\\s*:\\s*(\\d+)").find(packCreateJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            get("/api/v1/admin/card-packs/$packId").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Starter"))
            .andExpect(jsonPath("$.autoGenerated").value(true))
            .andExpect(jsonPath("$.useAllTournamentPlayers").value(true))

        mockMvc.perform(
            post("/api/v1/admin/users/777100/open-pack/$packId")
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userCards.length()").value(1))
            .andExpect(jsonPath("$.userCards[0].sourceCardPackId").value(packId.toInt()))

        mockMvc.perform(
            get("/api/v1/admin/card-packs").param("tournamentId", tournamentId.toString())
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Starter"))
    }

    @Test
    @Order(6)
    fun `admin give fantiki increases balance`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/users/777500/give-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":250,"adminReason":"Manual promo"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.telegramId").value(777500))
            .andExpect(jsonPath("$.fantiki").value(1250))

        val row = jdbcTemplate.queryForMap(
            """
            SELECT ft.amount, ft.admin_reason
            FROM fantiki_transaction ft
            INNER JOIN telegram_user tu ON tu.id = ft.telegram_user_id
            WHERE tu.telegram_id = 777500 AND ft.reason = 'ADMIN_GRANT'
            ORDER BY ft.id DESC
            LIMIT 1
            """.trimIndent(),
        )
        assertEquals(250L, (row["amount"] as Number).toLong())
        assertEquals("Manual promo", row["admin_reason"])
    }

    @Test
    @Order(7)
    fun `economy config get put and reject invalid value`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(get("/api/v1/admin/economy-config").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].key").exists())
        mockMvc.perform(
            put("/api/v1/admin/economy-config/card.uses.COMMON")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"2"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("card.uses.COMMON"))
            .andExpect(jsonPath("$.value").value("2"))
        mockMvc.perform(
            put("/api/v1/admin/economy-config/card.uses.COMMON")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"not-a-number"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(8)
    fun `admin give fantiki rejects non positive amount`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/users/777501/give-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":0,"adminReason":"Bad adjustment"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(9)
    fun `put series status FINISHED is rejected and explicit finalize requires readiness`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Finalize On Finish T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Round F","namePrefix":"RF","status":"UPCOMING",
                    "startsAt":"2026-06-01T12:00:00Z","teamDeadline":"2026-06-10T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.finalized").value(false))
            .andReturn().response.contentAsString
        val seriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(seriesJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            put("/api/v1/admin/series/$seriesId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"FINISHED"}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/v1/admin/series/$seriesId/completion-preview").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ready").value(false))
            .andExpect(jsonPath("$.readinessChecksum").doesNotExist())
            .andExpect(jsonPath("$.reason").value("expected game count is not configured"))

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/finalize")
                .header("Authorization", auth)
        )
            .andExpect(status().isConflict)
    }

    @Test
    @Order(10)
    fun `list admin users without series returns null cardsInSeries`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].fantiki").isNumber)
            .andExpect(jsonPath("$[0].cardsInSeries").value(nullValue()))
    }

    @Test
    @Order(11)
    fun `list admin users seriesId without tournamentId returns 400`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            get("/api/v1/admin/users").param("seriesId", "1").header("Authorization", auth),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(12)
    fun `list admin users with series counts cards for series roster`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Users List Cup","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":777888,"nickname":"SeriesRoster"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentPlayerId = Regex("\"id\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"UL Round","namePrefix":"UL","status":"UPCOMING",
                    "startsAt":"2026-07-01T12:00:00Z","teamDeadline":"2026-07-10T12:00:00Z"}
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
        )
            .andExpect(status().isOk)

        val ctJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(ctJson)!!.groupValues[1].toLong()

        val telegramTarget = 777999L
        mockMvc.perform(
            post("/api/v1/admin/users/$telegramTarget/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)

        val listJson = mockMvc.perform(
            get("/api/v1/admin/users")
                .param("tournamentId", tournamentId.toString())
                .param("seriesId", seriesId.toString())
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val rows = JsonPath.parse(listJson).read<List<Map<String, Any?>>>("$")
        val row = rows.first { (it["telegramId"] as Number).toLong() == telegramTarget }
        assertEquals(1L, (row["cardsInSeries"] as Number).toLong())
        assertEquals(1000L, (row["fantiki"] as Number).toLong())
    }

    @Test
    @Order(13)
    fun `admin take fantiki decreases balance`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tid = 778001L
        mockMvc.perform(
            post("/api/v1/admin/users/$tid/give-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":50,"adminReason":"Setup balance"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fantiki").value(1050))

        mockMvc.perform(
            post("/api/v1/admin/users/$tid/take-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":50,"adminReason":"Rollback grant"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.telegramId").value(tid))
            .andExpect(jsonPath("$.fantiki").value(1000))

        val row = jdbcTemplate.queryForMap(
            """
            SELECT ft.amount, ft.admin_reason
            FROM fantiki_transaction ft
            INNER JOIN telegram_user tu ON tu.id = ft.telegram_user_id
            WHERE tu.telegram_id = ? AND ft.reason = 'ADMIN_CONFISCATE'
            ORDER BY ft.id DESC
            LIMIT 1
            """.trimIndent(),
            tid,
        )
        assertEquals(-50L, (row["amount"] as Number).toLong())
        assertEquals("Rollback grant", row["admin_reason"])
    }

    @Test
    @Order(14)
    fun `admin take fantiki insufficient balance returns 400`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tid = 778002L
        mockMvc.perform(
            post("/api/v1/admin/users/$tid/give-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":1,"adminReason":"Setup balance"}"""),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/admin/users/$tid/take-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":999999,"adminReason":"Too much"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(15)
    fun `admin take fantiki unknown telegram user returns 404`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/users/999888777/take-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":1,"adminReason":"Unknown user"}"""),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    @Order(151)
    fun `admin fantiki reason is required`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tooLong = "x".repeat(501)

        mockMvc.perform(
            post("/api/v1/admin/users/778003/give-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":1,"adminReason":"   "}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/admin/users/778003/give-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":1}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/admin/users/778003/take-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":1,"adminReason":"$tooLong"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(152)
    fun `admin fantiki transactions history returns all transactions newest first with optional user filter`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tid = 778004L

        mockMvc.perform(
            post("/api/v1/admin/users/$tid/give-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":20,"adminReason":"History grant"}"""),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/admin/users/$tid/take-fantiki")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":10,"adminReason":"History take"}"""),
        )
            .andExpect(status().isOk)

        jdbcTemplate.update(
            """
            UPDATE fantiki_transaction ft
            SET created_at = TIMESTAMP '2099-01-01 10:00:00'
            FROM telegram_user tu
            WHERE tu.id = ft.telegram_user_id
              AND tu.telegram_id = ?
              AND ft.reason = 'ADMIN_GRANT'
              AND ft.admin_reason = 'History grant'
            """.trimIndent(),
            tid,
        )
        jdbcTemplate.update(
            """
            UPDATE fantiki_transaction ft
            SET created_at = TIMESTAMP '2099-01-01 10:02:00'
            FROM telegram_user tu
            WHERE tu.id = ft.telegram_user_id
              AND tu.telegram_id = ?
              AND ft.reason = 'ADMIN_CONFISCATE'
              AND ft.admin_reason = 'History take'
            """.trimIndent(),
            tid,
        )
        jdbcTemplate.update(
            """
            INSERT INTO fantiki_transaction (telegram_user_id, amount, reason, admin_reason, created_at)
            SELECT id, 99, 'SERIES_REWARD', 'Hidden automatic reward', TIMESTAMP '2099-01-01 10:03:00'
            FROM telegram_user
            WHERE telegram_id = ?
            """.trimIndent(),
            tid,
        )

        mockMvc.perform(
            get("/api/v1/admin/users/fantiki-transactions")
                .param("telegramUserId", tid.toString())
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.content[0].telegramId").value(tid))
            .andExpect(jsonPath("$.content[0].amount").value(99))
            .andExpect(jsonPath("$.content[0].reason").value("SERIES_REWARD"))
            .andExpect(jsonPath("$.content[0].adminReason").value("Hidden automatic reward"))
            .andExpect(jsonPath("$.content[1].amount").value(-10))
            .andExpect(jsonPath("$.content[1].reason").value("ADMIN_CONFISCATE"))
            .andExpect(jsonPath("$.content[1].adminReason").value("History take"))
            .andExpect(jsonPath("$.content[2].amount").value(20))
            .andExpect(jsonPath("$.content[2].reason").value("ADMIN_GRANT"))
            .andExpect(jsonPath("$.content[2].adminReason").value("History grant"))

        mockMvc.perform(
            get("/api/v1/admin/users/fantiki-transactions")
                .param("size", "1")
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].telegramId").value(tid))
            .andExpect(jsonPath("$.content[0].reason").value("SERIES_REWARD"))

        mockMvc.perform(
            get("/api/v1/admin/users/fantiki-transactions")
                .param("telegramUserId", tid.toString())
                .param("reason", "ADMIN_GRANT")
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].telegramId").value(tid))
            .andExpect(jsonPath("$.content[0].amount").value(20))
            .andExpect(jsonPath("$.content[0].reason").value("ADMIN_GRANT"))
            .andExpect(jsonPath("$.content[0].adminReason").value("History grant"))
    }

    @Test
    @Order(16)
    fun `list admin users with q matches telegramId`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val listJson = mockMvc.perform(
            get("/api/v1/admin/users")
                .param("q", "777001")
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val rows = JsonPath.parse(listJson).read<List<Map<String, Any?>>>("$")
        val hit = rows.first { (it["telegramId"] as Number).toLong() == 777001L }
        assertEquals(777001L, (hit["telegramId"] as Number).toLong())
    }

    @Test
    @Order(17)
    fun `list admin users with q no match returns empty`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val listJson = mockMvc.perform(
            get("/api/v1/admin/users")
                .param("q", "__no_such_user_match_zq9k2m7__")
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val rows = JsonPath.parse(listJson).read<List<Any>>("$")
        assertTrue(rows.isEmpty())
    }

    @Test
    @Order(18)
    fun `deadline reminder upsert on create and update resets sent state for future deadline`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val initialDeadline = Instant.parse("2099-08-10T12:00:00Z")
        val updatedDeadline = Instant.parse("2099-08-12T15:00:00Z")

        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Reminder Cup","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Reminder Round","namePrefix":"RR","status":"UPCOMING",
                    "startsAt":"2099-08-01T12:00:00Z","teamDeadline":"$initialDeadline"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(seriesJson)!!.groupValues[1].toLong()

        val createdReminder = deadlineReminderRepository.findBySeries_Id(seriesId)
        assertNotNull(createdReminder)
        assertEquals(initialDeadline.minusSeconds(3600), createdReminder!!.remindAt)
        assertEquals(false, createdReminder.sent)

        createdReminder.sent = true
        createdReminder.sentAt = Instant.parse("2099-08-01T00:00:00Z")
        createdReminder.recipientCount = 9
        deadlineReminderRepository.save(createdReminder)

        mockMvc.perform(
            put("/api/v1/admin/series/$seriesId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"teamDeadline":"$updatedDeadline"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teamDeadline").value(updatedDeadline.toString()))

        val updatedReminder = deadlineReminderRepository.findBySeries_Id(seriesId)
        assertNotNull(updatedReminder)
        assertEquals(updatedDeadline.minusSeconds(3600), updatedReminder!!.remindAt)
        assertEquals(false, updatedReminder.sent)
        assertEquals(null, updatedReminder.sentAt)
        assertEquals(null, updatedReminder.recipientCount)
    }

    @Test
    @Order(181)
    fun `deadline reminder recipient query executes on PostgreSQL`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Reminder Recipient Query Cup","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Reminder Recipient Query Round","namePrefix":"RRQ","status":"UPCOMING",
                    "startsAt":"2099-09-01T12:00:00Z","teamDeadline":"2099-09-01T13:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val seriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(seriesJson)!!.groupValues[1].toLong()

        val eligibleTelegramId = 777_181L
        telegramUserRepository.save(TelegramUser(telegramId = eligibleTelegramId))

        val recipients = telegramUserRepository.findDeadlineReminderRecipients(seriesId, tournamentId)

        assertTrue(eligibleTelegramId in recipients)
    }

    @Test
    @Order(19)
    fun `series admin can remove selected player listings from marketplace`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Series Unlist Cup","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val pJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":778899,"nickname":"Unlist Me"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentPlayerId = Regex("\"id\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()
        val fantasyPlayerId = Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(pJson)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Unlist Round","namePrefix":"ULS","status":"UPCOMING",
                    "startsAt":"2026-09-01T12:00:00Z","teamDeadline":"2026-09-10T12:00:00Z"}
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
        )
            .andExpect(status().isOk)

        val ctJson = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"COMMON"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val templateId = Regex("\"id\"\\s*:\\s*(\\d+)").find(ctJson)!!.groupValues[1].toLong()

        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/779111/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = JsonPath.read<List<Map<String, Any>>>(giveJson, "$")[0]["id"].toString().toLong()
        val userCard = userCardRepository.findById(userCardId).orElseThrow()
        val seller = userCard.telegramUser!!

        val listing = marketplaceListingRepository.save(
            MarketplaceListing(
                seller = seller,
                userCard = userCard,
                price = 333,
                status = MarketplaceListingStatus.ACTIVE,
                createdAt = Instant.parse("2026-09-01T10:00:00Z"),
            ),
        )

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players/$tournamentPlayerId/unlist-marketplace")
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tournamentPlayerId").value(tournamentPlayerId))
            .andExpect(jsonPath("$.fantasyPlayerId").value(fantasyPlayerId))
            .andExpect(jsonPath("$.cancelledListings").value(1))

        val updated = marketplaceListingRepository.findById(listing.id!!).orElseThrow()
        assertEquals(MarketplaceListingStatus.CANCELLED, updated.status)
    }

    @Test
    @Order(20)
    fun `admin complained transactions and complaints details endpoints return data`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Complaints admin T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":779001,"nickname":"ComplainedPlayer"}"""),
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

        val sellerTelegramId = 779210L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = JsonPath.read<List<Map<String, Any>>>(giveJson, "$")[0]["id"].toString().toLong()
        val userCard = userCardRepository.findById(userCardId).orElseThrow()
        val seller = userCard.telegramUser!!
        val buyer = telegramUserRepository.save(TelegramUser(telegramId = 779211L))
        val complainant = telegramUserRepository.save(TelegramUser(telegramId = 779212L, firstName = "Reporter"))

        val listing = marketplaceListingRepository.save(
            MarketplaceListing(
                seller = seller,
                buyer = buyer,
                userCard = userCard,
                price = 150,
                status = MarketplaceListingStatus.SOLD,
                createdAt = Instant.now().minusSeconds(300),
                soldAt = Instant.now().minusSeconds(60),
            ),
        )
        marketplaceComplaintRepository.save(
            MarketplaceComplaint(
                listing = listing,
                telegramUser = complainant,
                createdAt = Instant.now().minusSeconds(30),
            ),
        )

        mockMvc.perform(
            get("/api/v1/admin/marketplace/complained-transactions")
                .header("Authorization", auth)
                .param("minComplaints", "1"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
            .andExpect(jsonPath("$.content[0].listingId").value(listing.id!!.toInt()))
            .andExpect(jsonPath("$.content[0].complaintsCount").value(1))
            .andExpect(jsonPath("$.content[0].sanctioned").value(false))

        mockMvc.perform(
            get("/api/v1/admin/marketplace/transactions/${listing.id}/complaints")
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.complaints.length()").value(1))
            .andExpect(jsonPath("$.complaints[0].telegramId").value(complainant.telegramId))
            .andExpect(jsonPath("$.complaints[0].displayName").value("Reporter"))
    }

    @Test
    @Order(21)
    fun `admin can sanction complained transaction and apply balances`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sanction admin T","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val playerJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":779002,"nickname":"SanctionedPlayer"}"""),
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

        val sellerTelegramId = 779220L
        val giveJson = mockMvc.perform(
            post("/api/v1/admin/users/$sellerTelegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[$templateId]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val userCardId = JsonPath.read<List<Map<String, Any>>>(giveJson, "$")[0]["id"].toString().toLong()
        val userCard = userCardRepository.findById(userCardId).orElseThrow()
        val seller = userCard.telegramUser!!
        val buyer = telegramUserRepository.save(TelegramUser(telegramId = 779221L))
        val complainantA = telegramUserRepository.save(TelegramUser(telegramId = 779222L))
        val complainantB = telegramUserRepository.save(TelegramUser(telegramId = 779223L))

        val listing = marketplaceListingRepository.save(
            MarketplaceListing(
                seller = seller,
                buyer = buyer,
                userCard = userCard,
                price = 180,
                status = MarketplaceListingStatus.SOLD,
                createdAt = Instant.now().minusSeconds(300),
                soldAt = Instant.now().minusSeconds(60),
            ),
        )
        marketplaceComplaintRepository.save(MarketplaceComplaint(listing = listing, telegramUser = complainantA))
        marketplaceComplaintRepository.save(MarketplaceComplaint(listing = listing, telegramUser = complainantB))

        mockMvc.perform(
            post("/api/v1/admin/marketplace/transactions/${listing.id}/sanction")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason":"Нерыночная сделка","sellerFine":100,"buyerFine":50,
                    "complainantReward":20,"banSeller":{"days":3}}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.listingId").value(listing.id!!.toInt()))
            .andExpect(jsonPath("$.sellerFined").value(100))
            .andExpect(jsonPath("$.buyerFined").value(50))
            .andExpect(jsonPath("$.complainantsRewarded").value(2))
            .andExpect(jsonPath("$.totalRewardPaid").value(40))
            .andExpect(jsonPath("$.sellerBannedUntil").isNotEmpty)

        val sellerAfter = telegramUserRepository.findById(seller.id!!).orElseThrow()
        val buyerAfter = telegramUserRepository.findById(buyer.id!!).orElseThrow()
        val complainantAAfter = telegramUserRepository.findById(complainantA.id!!).orElseThrow()
        val complainantBAfter = telegramUserRepository.findById(complainantB.id!!).orElseThrow()
        assertEquals(900L, sellerAfter.fantiki)
        assertEquals(950L, buyerAfter.fantiki)
        assertEquals(1020L, complainantAAfter.fantiki)
        assertEquals(1020L, complainantBAfter.fantiki)
    }

    @Test
    @Order(22)
    fun `admin temporary ban endpoint sets banned until and unban clears both flags`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val user = telegramUserRepository.save(TelegramUser(telegramId = 779300L))

        mockMvc.perform(
            post("/api/v1/admin/marketplace/users/${user.telegramId}/ban")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"days":7}"""),
        ).andExpect(status().isNoContent)

        val banned = telegramUserRepository.findByTelegramId(user.telegramId)!!
        assertEquals(false, banned.marketplaceBanned)
        assertNotNull(banned.marketplaceBannedUntil)

        mockMvc.perform(
            post("/api/v1/admin/marketplace/unban/${user.telegramId}")
                .header("Authorization", auth),
        ).andExpect(status().isNoContent)

        val unbanned = telegramUserRepository.findByTelegramId(user.telegramId)!!
        assertEquals(false, unbanned.marketplaceBanned)
        assertEquals(null, unbanned.marketplaceBannedUntil)
    }

    @Test
    @Order(23)
    fun `series gameStartedOn works for standalone and is rejected for competition`() {
        val auth = basicAuth("admin", "test-admin-secret")

        val standaloneTournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Started Day Standalone","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val standaloneTournamentId =
            Regex("\"id\"\\s*:\\s*(\\d+)").find(standaloneTournamentJson)!!.groupValues[1].toLong()

        val standaloneSeriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$standaloneTournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Started Day Series","namePrefix":"SDS","gameStartedOn":"2026-10-05","status":"UPCOMING",
                    "startsAt":"2026-10-01T12:00:00Z","teamDeadline":"2026-10-10T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gameStartedOn").value("2026-10-05"))
            .andReturn().response.contentAsString
        val standaloneSeriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(standaloneSeriesJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            get("/api/v1/admin/series/$standaloneSeriesId").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gameStartedOn").value("2026-10-05"))

        mockMvc.perform(
            put("/api/v1/admin/series/$standaloneSeriesId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gameStartedOn":"2026-10-06"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gameStartedOn").value("2026-10-06"))

        mockMvc.perform(
            put("/api/v1/admin/series/$standaloneSeriesId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gameStartedOn":null}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gameStartedOn").value(nullValue()))

        val competitionTournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Started Day Competition","status":"DRAFT","kind":"POLEMICA_COMPETITION","polemicaCompetitionId":99887766}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val competitionTournamentId =
            Regex("\"id\"\\s*:\\s*(\\d+)").find(competitionTournamentJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            post("/api/v1/admin/tournaments/$competitionTournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Competition Started Day","gameNumFrom":1,"gameNumTo":3,"gameStartedOn":"2026-10-05","status":"UPCOMING",
                    "startsAt":"2026-10-01T12:00:00Z","teamDeadline":"2026-10-10T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)

        val competitionSeriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$competitionTournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Competition Series","gameNumFrom":1,"gameNumTo":3,"status":"UPCOMING",
                    "startsAt":"2026-10-01T12:00:00Z","teamDeadline":"2026-10-10T12:00:00Z"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gameStartedOn").value(nullValue()))
            .andReturn().response.contentAsString
        val competitionSeriesId = Regex("\"id\"\\s*:\\s*(\\d+)").find(competitionSeriesJson)!!.groupValues[1].toLong()

        mockMvc.perform(
            put("/api/v1/admin/series/$competitionSeriesId")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gameStartedOn":"2026-10-07"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(24)
    fun `series player replacements are saved returned and validated`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Replacement Cup","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tJson)!!.groupValues[1].toLong()

        val p1Json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":111001,"nickname":"Main One"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val p1Id = Regex("\"id\"\\s*:\\s*(\\d+)").find(p1Json)!!.groupValues[1].toLong()

        val p2Json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":111002,"nickname":"Main Two"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val p2Id = Regex("\"id\"\\s*:\\s*(\\d+)").find(p2Json)!!.groupValues[1].toLong()

        val seriesJson = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Replacement Round","namePrefix":"RR","status":"UPCOMING",
                    "startsAt":"2026-11-01T12:00:00Z","teamDeadline":"2026-11-10T12:00:00Z"}
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
                .content(
                    """
                    {"tournamentPlayerIds":[$p1Id,$p2Id],"replacementPolemicaUserIds":{"$p1Id":999001}}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tournamentPlayerIds.length()").value(2))
            .andExpect(jsonPath("$.replacementPolemicaUserIds['$p1Id']").value(999001))

        mockMvc.perform(
            get("/api/v1/admin/series/$seriesId").header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replacementPolemicaUserIds['$p1Id']").value(999001))

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[$p1Id],"replacementPolemicaUserIds":{"$p2Id":999002}}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[$p1Id],"replacementPolemicaUserIds":{"$p1Id":0}}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[$p1Id],"replacementPolemicaUserIds":{"$p1Id":111001}}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tournamentPlayerIds":[$p1Id,$p2Id],"replacementPolemicaUserIds":{"$p1Id":111002}}"""),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tournamentPlayerIds":[$p1Id,$p2Id],"replacementPolemicaUserIds":{"$p1Id":999003,"$p2Id":999003}}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(25)
    fun `tournament html report filters series deduplicates top cards and renders template perks`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Report Cup","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()

        val includedSeriesId = createReportSeries(auth, tournamentId, "Report Included", "RI", "2026-12-01T12:00:00Z")
        val excludedSeriesId = createReportSeries(auth, tournamentId, "Report Excluded", "RE", "2026-12-02T12:00:00Z")
        insertSeriesGame(includedSeriesId, 881001)
        insertSeriesGame(excludedSeriesId, 881002)

        val repeatPlayerId = createReportPlayer(auth, tournamentId, 881101, "Repeat Star")
        val secondPlayerId = createReportPlayer(auth, tournamentId, 881102, "Second Star")
        val repeatLegendaryTemplateId = createReportTemplate(auth, repeatPlayerId, "LEGENDARY")
        val repeatEpicTemplateId = createReportTemplate(auth, repeatPlayerId, "EPIC")
        val secondTemplateId = createReportTemplate(auth, secondPlayerId, "RARE")
        addTemplatePerk(auth, repeatLegendaryTemplateId, "sniper")
        addTemplatePerk(auth, repeatLegendaryTemplateId, "voteForBlack")

        val userOneCards = giveReportCards(auth, 881201, listOf(repeatLegendaryTemplateId, secondTemplateId))
        val userTwoCards = giveReportCards(auth, 881202, listOf(repeatEpicTemplateId))
        val mainLeagueId = mainSeriesLeagueId(includedSeriesId)
        val teamOneId = insertFantasyTeam(881201, includedSeriesId, mainLeagueId, 31.0)
        insertFantasyTeamCard(teamOneId, userOneCards[0], 1, 19.5)
        insertFantasyTeamCard(teamOneId, userOneCards[1], 2, 11.5)
        val teamTwoId = insertFantasyTeam(881202, includedSeriesId, mainLeagueId, 22.0)
        insertFantasyTeamCard(teamTwoId, userTwoCards[0], 1, 18.0)

        val html = mockMvc.perform(
            get("/api/v1/admin/tournaments/$tournamentId/report.html")
                .param("seriesIds", includedSeriesId.toString())
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andReturn().response.contentAsString

        assertTrue(html.contains("Report Cup"))
        assertTrue(html.contains("Report Included"))
        assertTrue(!html.contains("Report Excluded"))
        assertTrue(html.contains("Снайпер"))
        assertTrue(html.contains("Изгнать этого черныша!"))
        assertEquals(1, Regex("card-row__title\">Repeat Star").findAll(html).count())
        assertTrue(html.contains("Second Star"))

        mockMvc.perform(
            get("/api/v1/admin/tournaments/$tournamentId/report.html")
                .header("Authorization", auth),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/v1/admin/tournaments/$tournamentId/report.html")
                .param("seriesIds", excludedSeriesId.plus(999_999).toString())
                .header("Authorization", auth),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(26)
    fun `admin can list and delete series games from scoring`() {
        val auth = basicAuth("admin", "test-admin-secret")
        val tournamentJson = mockMvc.perform(
            post("/api/v1/admin/tournaments")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Games Admin Cup","status":"DRAFT"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val tournamentId = Regex("\"id\"\\s*:\\s*(\\d+)").find(tournamentJson)!!.groupValues[1].toLong()
        val seriesId = createReportSeries(auth, tournamentId, "Games Admin Series", "GAS", "2026-12-03T12:00:00Z")
        val gameOneId = insertSeriesGameWithCache(
            seriesId = seriesId,
            polemicaGameId = 882001,
            gameNum = 11,
            table = 3,
            phase = 0,
            scoreCalculated = true,
        )
        val gameTwoId = insertSeriesGameWithCache(
            seriesId = seriesId,
            polemicaGameId = 882002,
            gameNum = 12,
            table = 4,
            phase = 1,
            scoreCalculated = true,
        )
        val playerId = createReportPlayer(auth, tournamentId, 882101, "Games Player")
        val templateId = createReportTemplate(auth, playerId, "COMMON")
        val userCardId = giveReportCards(auth, 882201, listOf(templateId)).single()
        val mainLeagueId = mainSeriesLeagueId(seriesId)
        val teamId = insertFantasyTeam(882201, seriesId, mainLeagueId, 30.0)
        val teamCardId = insertFantasyTeamCard(teamId, userCardId, 1, 30.0)
        insertCardGameScore(teamCardId, gameOneId, 10.0)
        insertCardGameScore(teamCardId, gameTwoId, 20.0)

        mockMvc.perform(get("/api/v1/admin/series/$seriesId/games").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(gameOneId.toInt()))
            .andExpect(jsonPath("$[0].polemicaGameId").value(882001))
            .andExpect(jsonPath("$[0].displayName").value("Игра 11"))
            .andExpect(jsonPath("$[0].gameNum").value(11))
            .andExpect(jsonPath("$[0].table").value(3))
            .andExpect(jsonPath("$[0].phase").value(0))
            .andExpect(jsonPath("$[0].finished").value(true))
            .andExpect(jsonPath("$[0].scored").value(true))

        mockMvc.perform(
            delete("/api/v1/admin/series/$seriesId/games/$gameOneId").header("Authorization", auth),
        )
            .andExpect(status().isOk)

        assertEquals(0, countRows("series_game", gameOneId))
        assertEquals(0, countRows("fantasy_team_card_game_score", gameOneId, "series_game_id"))
        assertEquals(20.0, selectDouble("SELECT score FROM fantasy_team_card WHERE id = ?", teamCardId))
        assertEquals(20.0, selectDouble("SELECT total_score FROM fantasy_team WHERE id = ?", teamId))

        jdbcTemplate.update("UPDATE series SET finalized = TRUE WHERE id = ?", seriesId)
        mockMvc.perform(
            delete("/api/v1/admin/series/$seriesId/games/$gameTwoId").header("Authorization", auth),
        )
            .andExpect(status().isConflict)
        mockMvc.perform(
            post("/api/v1/admin/series/$seriesId/games")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaGameId":882003}"""),
        )
            .andExpect(status().isConflict)
    }

    private fun createReportSeries(auth: String, tournamentId: Long, name: String, prefix: String, startsAt: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/series")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"$name","namePrefix":"$prefix","status":"ACTIVE",
                    "startsAt":"$startsAt","teamDeadline":"$startsAt"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return Regex("\"id\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toLong()
    }

    private fun createReportPlayer(auth: String, tournamentId: Long, polemicaUserId: Long, nickname: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/tournaments/$tournamentId/players")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"polemicaUserId":$polemicaUserId,"nickname":"$nickname"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return Regex("\"fantasyPlayerId\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toLong()
    }

    private fun createReportTemplate(auth: String, fantasyPlayerId: Long, rarity: String): Long {
        val json = mockMvc.perform(
            post("/api/v1/admin/card-templates")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fantasyPlayerId":$fantasyPlayerId,"rarity":"$rarity"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return Regex("\"id\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toLong()
    }

    private fun addTemplatePerk(auth: String, templateId: Long, perkId: String) {
        mockMvc.perform(
            post("/api/v1/admin/card-templates/$templateId/perks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"perkId":"$perkId"}"""),
        )
            .andExpect(status().isOk)
    }

    private fun giveReportCards(auth: String, telegramId: Long, templateIds: List<Long>): List<Long> {
        val json = mockMvc.perform(
            post("/api/v1/admin/users/$telegramId/give-cards")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardTemplateIds":[${templateIds.joinToString(",")}]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val ids = JsonPath.read<List<Number>>(json, "$[*].id")
        return ids.map { it.toLong() }
    }

    private fun insertSeriesGame(seriesId: Long, polemicaGameId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO series_game (series_id, polemica_game_id, game_name, scored, played_at)
            VALUES (?, ?, ?, true, now())
            """.trimIndent(),
            seriesId,
            polemicaGameId,
            "Game $polemicaGameId",
        )
    }

    private fun insertSeriesGameWithCache(
        seriesId: Long,
        polemicaGameId: Long,
        gameNum: Int,
        table: Int,
        phase: Int,
        scoreCalculated: Boolean,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO series_game (series_id, polemica_game_id, game_name, game_data_cache, scored, played_at)
            VALUES (
                ?,
                ?,
                '(no name)',
                jsonb_build_object(
                    'id', ?,
                    'name', NULL,
                    'started', '2026-12-03T12:00:00',
                    'result', 1,
                    'num', ?,
                    'table', ?,
                    'phase', ?
                ),
                ?,
                now()
            )
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            seriesId,
            polemicaGameId,
            polemicaGameId,
            gameNum,
            table,
            phase,
            scoreCalculated,
        )!!

    private fun mainSeriesLeagueId(seriesId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT sl.id
            FROM series_league sl
            JOIN league l ON l.id = sl.league_id
            WHERE sl.series_id = ?
              AND l.code = 'MAIN'
            """.trimIndent(),
            Long::class.java,
            seriesId,
        )!!

    private fun insertFantasyTeam(telegramId: Long, seriesId: Long, seriesLeagueId: Long, score: Double): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO fantasy_team (telegram_user_id, series_id, series_league_id, total_score, submitted_at)
            VALUES ((SELECT id FROM telegram_user WHERE telegram_id = ?), ?, ?, ?, now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            telegramId,
            seriesId,
            seriesLeagueId,
            score,
        )!!

    private fun insertFantasyTeamCard(teamId: Long, userCardId: Long, slot: Int, score: Double): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO fantasy_team_card (fantasy_team_id, user_card_id, slot, score)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            teamId,
            userCardId,
            slot,
            score,
        )!!

    private fun insertCardGameScore(fantasyTeamCardId: Long, seriesGameId: Long, totalScore: Double): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO fantasy_team_card_game_score (
                fantasy_team_card_id,
                series_game_id,
                base_points,
                perk_bonus,
                rarity_modifier,
                total_score
            )
            VALUES (?, ?, ?, 0, 1, ?)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            fantasyTeamCardId,
            seriesGameId,
            totalScore,
            totalScore,
        )!!

    private fun countRows(tableName: String, id: Long, columnName: String = "id"): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $tableName WHERE $columnName = ?",
            Int::class.java,
            id,
        )!!

    private fun selectDouble(sql: String, vararg args: Any): Double =
        jdbcTemplate.queryForObject(sql, Double::class.java, *args)!!

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
