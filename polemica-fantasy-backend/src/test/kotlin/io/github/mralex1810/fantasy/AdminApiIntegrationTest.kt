package io.github.mralex1810.fantasy

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
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
import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.Base64

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AdminApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

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
    @Order(3)
    fun `sync games returns 400 when polemica credentials not configured`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/series/999/sync-games").header("Authorization", auth),
        )
            .andExpect(status().isBadRequest)
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
                .content("""{"amount":250}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.telegramId").value(777500))
            .andExpect(jsonPath("$.fantiki").value(1250))
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
                .content("""{"amount":0}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(9)
    fun `put series status FINISHED runs finalization so finalized flag is true`() {
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
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("FINISHED"))
            .andExpect(jsonPath("$.finalized").value(true))
    }

    @Test
    @Order(10)
    fun `list admin users without series returns null cardsInSeries`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").exists())
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
