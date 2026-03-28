package io.github.mralex1810.fantasy

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.StandardCharsets
import java.time.Instant
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
    }
}
