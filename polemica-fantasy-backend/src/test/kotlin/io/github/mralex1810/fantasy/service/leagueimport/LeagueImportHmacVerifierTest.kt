package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class LeagueImportHmacVerifierTest {
    private val properties = TelegramLeagueImportProperties(
        ingestKeyId = "k1",
        ingestCurrentSecret = "secret",
        ingestClockSkewSeconds = 300,
    )
    private val verifier = LeagueImportHmacVerifier(properties)

    @Test
    fun `signature binds delivery id key timestamp and exact body`() {
        val timestamp = Instant.now().epochSecond.toString()
        val body = "{\"messageId\":1}".toByteArray()
        val delivery = "11111111-1111-1111-1111-111111111111"
        val signature = sign("k1", delivery, timestamp, body)
        assertNotNull(verifier.verify("k1", delivery, timestamp, signature, body))
        assertNull(verifier.verify("k1", "22222222-2222-2222-2222-222222222222", timestamp, signature, body))
        assertNull(verifier.verify("k1", delivery, timestamp, signature, "{\"messageId\":2}".toByteArray()))
    }

    @Test
    fun `previous key rotation is explicit`() {
        properties.ingestPreviousKeyId = "old"
        properties.ingestPreviousSecret = "old-secret"
        val timestamp = Instant.now().epochSecond.toString()
        val body = "{}".toByteArray()
        val delivery = "11111111-1111-1111-1111-111111111111"
        assertNotNull(verifier.verify("old", delivery, timestamp, sign("old", delivery, timestamp, body, "old-secret"), body))
        assertNull(verifier.verify("", delivery, timestamp, sign("", delivery, timestamp, body), body))
    }

    @Test
    fun `out of range epoch is rejected without server error`() {
        val timestamp = Long.MAX_VALUE.toString()
        val body = "{}".toByteArray()
        val delivery = "11111111-1111-1111-1111-111111111111"

        assertNull(verifier.verify("k1", delivery, timestamp, sign("k1", delivery, timestamp, body), body))
    }

    private fun sign(key: String, delivery: String, timestamp: String, body: ByteArray, secret: String = "secret"): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal("$key\n$delivery\n$timestamp\n".toByteArray() + body)
            .joinToString("") { "%02x".format(it) }
    }
}
