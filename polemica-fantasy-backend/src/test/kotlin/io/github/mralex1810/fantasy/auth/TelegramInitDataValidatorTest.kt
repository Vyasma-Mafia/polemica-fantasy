package io.github.mralex1810.fantasy.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramInitDataValidatorTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val validator = TelegramInitDataValidator(objectMapper)

    @Test
    fun `accepts valid signature`() {
        val botToken = "test-token"
        val authDate = Instant.now().epochSecond
        val userJson = """{"id":999001,"first_name":"FN","username":"un"}"""
        val initData = buildSignedInitData(botToken, authDate, userJson)
        val parsed = validator.validateAndParse(initData, botToken)
        assertEquals(999001L, parsed.telegramUserId)
        assertEquals("un", parsed.username)
        assertEquals("FN", parsed.firstName)
    }

    @Test
    fun `rejects wrong hash`() {
        val botToken = "test-token"
        val userJson = """{"id":1}"""
        val broken = "auth_date=${Instant.now().epochSecond}&user=${java.net.URLEncoder.encode(userJson, Charsets.UTF_8)}&hash=deadbeef"
        assertThrows<IllegalStateException> {
            validator.validateAndParse(broken, botToken)
        }
    }

    /**
     * Локально: снять @Disabled, выполнить
     * `./gradlew :polemica-fantasy-backend:test --tests "*TelegramInitDataValidatorTest.printViteDevInitDataSample"`.
     * Строка подписана [test-token] как в [application-test.yml]; для docker/backend задайте в .env
     * `TELEGRAM_BOT_TOKEN=test-token` или перегенерируйте под свой токен.
     */
    @Test
    fun printViteDevInitDataSample() {
        val botToken = "test-token"
        val authDate = Instant.now().epochSecond
        val userJson = """{"id":888001,"first_name":"LocalDev","username":"localdev"}"""
        val initData = buildSignedInitData(botToken, authDate, userJson)
        println()
        println("--- paste into polemica-fantasy-webapp/.env.local ---")
        println("VITE_DEV_INIT_DATA=\"$initData\"")
        println("--- end ---")
        println()
    }

    @Test
    fun `rejects stale auth_date`() {
        val botToken = "test-token"
        val old = Instant.now().minus(25, ChronoUnit.HOURS).epochSecond
        val userJson = """{"id":42}"""
        val initData = buildSignedInitData(botToken, old, userJson)
        val ex = assertThrows<IllegalStateException> {
            validator.validateAndParse(initData, botToken)
        }
        assertTrue(ex.message!!.contains("expired", ignoreCase = true))
    }

    private fun buildSignedInitData(botToken: String, authDate: Long, userJson: String): String {
        val userEncoded = java.net.URLEncoder.encode(userJson, StandardCharsets.UTF_8)
        val pairs = linkedMapOf(
            "auth_date" to authDate.toString(),
            "user" to userJson,
        )
        val dataCheckString = pairs.keys.sorted().joinToString("\n") { k -> "$k=${pairs[k]}" }
        val secretKey = hmacSha256(WEB_APP_DATA_KEY, botToken.toByteArray(StandardCharsets.UTF_8))
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
        private val WEB_APP_DATA_KEY = "WebAppData".toByteArray(StandardCharsets.UTF_8)
    }
}
