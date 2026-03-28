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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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
            get("/api/v1/admin/card-packs").header("Authorization", auth),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/admin/card-packs")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Starter","tournamentId":$tournamentId,"active":true,
                    "rarityConfigs":[
                      {"rarity":"COMMON","probability":1.0,"cardsCount":1}
                    ]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/admin/card-packs").param("tournamentId", tournamentId.toString())
                .header("Authorization", auth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Starter"))
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
