package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class LeagueImportHmacVerifier(
    private val properties: TelegramLeagueImportProperties,
) {
    private val clock: Clock = Clock.systemUTC()
    fun verify(keyId: String, deliveryId: String, timestampRaw: String, signatureHex: String, body: ByteArray): Instant? {
        val timestamp = timestampRaw.toLongOrNull()
            ?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() }
            ?: return null
        if (Duration.between(timestamp, clock.instant()).abs().seconds > properties.ingestClockSkewSeconds) return null
        val secret = when (keyId) {
            properties.ingestKeyId -> properties.ingestCurrentSecret
            properties.ingestPreviousKeyId -> properties.ingestPreviousSecret
            else -> return null
        }.takeIf { it.isNotBlank() } ?: return null
        val expected = hmac(secret, keyId, deliveryId, timestampRaw, body)
        val supplied = signatureHex.hexBytes() ?: return null
        return timestamp.takeIf { MessageDigest.isEqual(expected, supplied) }
    }

    private fun hmac(secret: String, keyId: String, deliveryId: String, timestamp: String, body: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        mac.update("$keyId\n$deliveryId\n$timestamp\n".toByteArray(StandardCharsets.US_ASCII))
        return mac.doFinal(body)
    }

    private fun String.hexBytes(): ByteArray? {
        if (length != 64 || any { it !in "0123456789abcdefABCDEF" }) return null
        return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }
}
