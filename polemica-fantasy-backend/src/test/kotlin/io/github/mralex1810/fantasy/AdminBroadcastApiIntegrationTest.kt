package io.github.mralex1810.fantasy

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import io.github.mralex1810.fantasy.telegram.TelegramBotApiClient
import java.util.Base64

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AdminBroadcastApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var telegramBotApiClient: TelegramBotApiClient

    @Test
    fun `broadcast without auth returns 401`() {
        mockMvc.perform(
            post("/api/v1/admin/notifications/broadcast")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Hello everyone"}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `broadcast with auth returns 202 and recipientCount`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/notifications/broadcast")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Hello everyone"}"""),
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.recipientCount").exists())
    }

    @Test
    fun `direct message without auth returns 401`() {
        mockMvc.perform(
            post("/api/v1/admin/notifications/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramUserId":123,"text":"Hello"}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `direct message to missing user returns 404`() {
        val auth = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/notifications/direct")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramUserId":123,"text":"Hello"}"""),
        )
            .andExpect(status().isNotFound)
    }

    private fun basicAuth(user: String, pass: String): String {
        val raw = "$user:$pass"
        return "Basic " + Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
