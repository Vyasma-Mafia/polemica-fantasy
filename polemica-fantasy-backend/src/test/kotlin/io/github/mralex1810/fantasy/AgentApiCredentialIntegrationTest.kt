package io.github.mralex1810.fantasy

import com.jayway.jsonpath.JsonPath
import io.github.mralex1810.fantasy.auth.ApiCredentialTokenCodec
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AgentApiCredentialIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var tokenCodec: ApiCredentialTokenCodec

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Test
    fun `agent credential is one-time bearer secret and revocation is immediate`() {
        val telegramId = 9_800_001L
        val admin = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/agent-users")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"telegramId":$telegramId,"username":"quiet_player","firstName":"Quiet","displayName":"Quiet Player"}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.fantiki").value(1000))
            .andExpect(jsonPath("$.automatedAgent").value(true))

        val created = mockMvc.perform(
            post("/api/v1/admin/users/$telegramId/api-credentials")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"runtime","expiresAt":"2035-01-01T00:00:00Z"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val token = JsonPath.read<String>(created, "$.token")
        val credentialId = JsonPath.read<Int>(created, "$.credential.id").toLong()
        assertThat(tokenCodec.isWellFormed(token)).isTrue()
        assertThat(created).doesNotContain(tokenCodec.hash(token))

        val stored = jdbcTemplate.queryForMap(
            "SELECT token_hash, is_automated_agent FROM api_credential c JOIN telegram_user u ON u.id=c.telegram_user_id WHERE c.id=?",
            credentialId,
        )
        assertThat(stored["token_hash"]).isEqualTo(tokenCodec.hash(token))
        assertThat(stored["token_hash"]).isNotEqualTo(token)
        assertThat(stored["is_automated_agent"]).isEqualTo(true)

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.telegramId").value(telegramId))
            .andExpect(jsonPath("$.displayName").value("Quiet Player"))
            .andExpect(jsonPath("$.automatedAgent").doesNotExist())

        val listed = mockMvc.perform(
            get("/api/v1/admin/users/$telegramId/api-credentials").header("Authorization", admin),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(credentialId))
            .andReturn().response.contentAsString
        assertThat(listed).doesNotContain(token).doesNotContain(tokenCodec.hash(token))

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_credential_auth_audit WHERE api_credential_id=? AND outcome='SUCCESS'",
                Long::class.java,
                credentialId,
            ),
        ).isEqualTo(1L)

        mockMvc.perform(
            delete("/api/v1/admin/users/$telegramId/api-credentials/$credentialId").header("Authorization", admin),
        ).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_credential_auth_audit WHERE api_credential_id=? AND outcome='REVOKED'",
                Long::class.java,
                credentialId,
            ),
        ).isEqualTo(1L)
    }

    @Test
    fun `bearer is isolated from admin and admin basic is not user authentication`() {
        val admin = basicAuth("admin", "test-admin-secret")
        val telegramId = 9_800_002L
        mockMvc.perform(
            post("/api/v1/admin/agent-users")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramId":$telegramId,"firstName":"Isolation"}"""),
        ).andExpect(status().isCreated)
        val created = mockMvc.perform(
            post("/api/v1/admin/users/$telegramId/api-credentials")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"isolation","expiresAt":"2035-01-01T00:00:00Z"}"""),
        ).andReturn().response.contentAsString
        val token = JsonPath.read<String>(created, "$.token")

        mockMvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/me").header("Authorization", admin))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer malformed"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer pfa_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `moderator cannot provision agent users or credentials`() {
        val moderator = basicAuth("moderator", "test-moderator-secret")
        mockMvc.perform(
            post("/api/v1/admin/agent-users")
                .header("Authorization", moderator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramId":9800003,"firstName":"Forbidden"}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `automated agent cannot use TMA auth and profile is not mutated`() {
        val telegramId = 9_800_004L
        val admin = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/agent-users")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramId":$telegramId,"username":"original","firstName":"Original"}"""),
        ).andExpect(status().isCreated)
        jdbcTemplate.update("UPDATE telegram_user SET bot_blocked=TRUE WHERE telegram_id=?", telegramId)
        val credentialJson = mockMvc.perform(
            post("/api/v1/admin/users/$telegramId/api-credentials")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"bearer-only","expiresAt":"2035-01-01T00:00:00Z"}"""),
        ).andReturn().response.contentAsString
        val token = JsonPath.read<String>(credentialJson, "$.token")
        val initData = buildSignedInitData(
            telegramId = telegramId,
            username = "mutated",
            firstName = "Mutated",
        )

        mockMvc.perform(get("/api/v1/me").header("Authorization", "tma $initData"))
            .andExpect(status().isUnauthorized)
        val row = jdbcTemplate.queryForMap(
            "SELECT username, first_name, bot_blocked FROM telegram_user WHERE telegram_id=?",
            telegramId,
        )
        assertThat(row["username"]).isEqualTo("original")
        assertThat(row["first_name"]).isEqualTo("Original")
        assertThat(row["bot_blocked"]).isEqualTo(true)

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.telegramId").value(telegramId))
    }

    @Test
    fun `expired and unknown credentials return the same generic 401 and recognized failure is audited`() {
        val telegramId = 9_800_005L
        val admin = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/agent-users")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramId":$telegramId,"firstName":"Expired"}"""),
        ).andExpect(status().isCreated)
        val userId = jdbcTemplate.queryForObject(
            "SELECT id FROM telegram_user WHERE telegram_id=?",
            Long::class.java,
            telegramId,
        )!!
        val expiredToken = tokenCodec.generate()
        val credentialId = jdbcTemplate.queryForObject(
            """
            INSERT INTO api_credential
                (telegram_user_id,label,token_hash,token_hint,created_at,expires_at,created_by)
            VALUES (?, 'expired', ?, ?, now() - interval '2 days', now() - interval '1 day', 'test')
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            userId,
            tokenCodec.hash(expiredToken),
            tokenCodec.hint(expiredToken),
        )!!

        val expired = mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer $expiredToken"))
            .andExpect(status().isUnauthorized)
            .andReturn().response
        val unknown = mockMvc.perform(
            get("/api/v1/me").header("Authorization", "Bearer pfa_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
        )
            .andExpect(status().isUnauthorized)
            .andReturn().response
        assertThat(expired.errorMessage).isEqualTo("Invalid bearer token")
        assertThat(unknown.errorMessage).isEqualTo(expired.errorMessage)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_credential_auth_audit WHERE api_credential_id=? AND outcome='EXPIRED'",
                Long::class.java,
                credentialId,
            ),
        ).isEqualTo(1L)
    }

    @Test
    fun `recognized credential audit is trimmed to configured bound`() {
        val telegramId = 9_800_006L
        val admin = basicAuth("admin", "test-admin-secret")
        mockMvc.perform(
            post("/api/v1/admin/agent-users")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramId":$telegramId,"firstName":"Audit"}"""),
        ).andExpect(status().isCreated)
        val created = mockMvc.perform(
            post("/api/v1/admin/users/$telegramId/api-credentials")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"audit","expiresAt":"2035-01-01T00:00:00Z"}"""),
        ).andReturn().response.contentAsString
        val token = JsonPath.read<String>(created, "$.token")
        val credentialId = JsonPath.read<Int>(created, "$.credential.id").toLong()
        jdbcTemplate.update(
            """
            INSERT INTO api_credential_auth_audit
                (api_credential_id,outcome,request_method,request_path,occurred_at)
            SELECT ?, 'SUCCESS', 'GET', '/seed', now() - (n || ' seconds')::interval
            FROM generate_series(1, 1005) n
            """.trimIndent(),
            credentialId,
        )

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_credential_auth_audit WHERE api_credential_id=?",
                Long::class.java,
                credentialId,
            ),
        ).isEqualTo(1000L)
    }

    @Test
    fun `automatic recipient selectors exclude agent while ordinary TMA write remains available`() {
        val humanTelegramId = 9_800_007L
        val agentTelegramId = 9_800_008L
        val humanTma = "tma ${buildSignedInitData(humanTelegramId, "human", "Human")}"
        mockMvc.perform(
            patch("/api/v1/me")
                .header("Authorization", humanTma)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"Human Player"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Human Player"))
        mockMvc.perform(
            post("/api/v1/admin/agent-users")
                .header("Authorization", basicAuth("admin", "test-admin-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"telegramId":$agentTelegramId,"firstName":"Agent"}"""),
        ).andExpect(status().isCreated)

        assertThat(telegramUserRepository.findAllTelegramIds())
            .contains(humanTelegramId)
            .doesNotContain(agentTelegramId)
        assertThat(telegramUserRepository.findAllEligibleForSeriesStart(listOf(Long.MAX_VALUE)).map { it.telegramId })
            .contains(humanTelegramId)
            .doesNotContain(agentTelegramId)
        assertThat(
            telegramUserRepository.findDeadlineReminderRecipients(Long.MAX_VALUE - 1, Long.MAX_VALUE),
        )
            .contains(humanTelegramId)
            .doesNotContain(agentTelegramId)
    }

    private fun basicAuth(user: String, password: String): String {
        val token = Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
        return "Basic $token"
    }

    private fun buildSignedInitData(telegramId: Long, username: String, firstName: String): String {
        val userJson = """{"id":$telegramId,"username":"$username","first_name":"$firstName"}"""
        val authDate = Instant.now().epochSecond
        val pairs = linkedMapOf(
            "auth_date" to authDate.toString(),
            "query_id" to "agent-auth-test",
            "user" to userJson,
        )
        val dataCheckString = pairs.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
        val secret = hmacSha256("WebAppData".toByteArray(), "test-token".toByteArray())
        val hash = hmacSha256(secret, dataCheckString.toByteArray()).joinToString("") { "%02x".format(it) }
        return (pairs + ("hash" to hash)).entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8)}"
        }
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
