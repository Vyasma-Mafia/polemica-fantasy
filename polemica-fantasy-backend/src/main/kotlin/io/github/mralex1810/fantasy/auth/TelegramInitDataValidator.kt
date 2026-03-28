package io.github.mralex1810.fantasy.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class TelegramInitDataValidator(
    private val objectMapper: ObjectMapper,
) {

    fun validateAndParse(initDataRaw: String, botToken: String, maxAge: Duration = DEFAULT_MAX_AGE): ParsedInitData {
        require(botToken.isNotBlank()) { "Telegram bot token is not configured" }

        val pairs = parseQueryPairs(initDataRaw)
        val hashHex = pairs["hash"] ?: error("initData missing hash")
        val hashBytes = hexDecode(hashHex)

        val dataCheckString = pairs.keys
            .asSequence()
            .filter { it != "hash" }
            .sorted()
            .joinToString("\n") { key -> "$key=${pairs[key]}" }

        val secretKey = hmacSha256(key = WEB_APP_DATA_KEY, data = botToken.toByteArray(StandardCharsets.UTF_8))
        val computed = hmacSha256(key = secretKey, data = dataCheckString.toByteArray(StandardCharsets.UTF_8))

        if (!MessageDigest.isEqual(computed, hashBytes)) {
            error("Invalid initData signature")
        }

        val authDateSec = pairs["auth_date"]?.toLongOrNull() ?: error("initData missing auth_date")
        val authDate = Instant.ofEpochSecond(authDateSec)
        if (Instant.now().isAfter(authDate.plus(maxAge))) {
            error("initData expired")
        }

        val userJson = pairs["user"] ?: error("initData missing user")
        val userNode = objectMapper.readTree(userJson)
        val telegramUserId = userNode.get("id")?.asLong() ?: error("user.id missing")

        val username = userNode.get("username")?.asText()?.takeIf { it.isNotBlank() }
        val firstName = userNode.get("first_name")?.asText()?.takeIf { it.isNotBlank() }

        return ParsedInitData(
            telegramUserId = telegramUserId,
            username = username,
            firstName = firstName,
            authDate = authDate,
        )
    }

    private fun parseQueryPairs(initDataRaw: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (part in initDataRaw.split("&")) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8)
            result[key] = value
        }
        return result
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hexDecode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hash hex" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        private val WEB_APP_DATA_KEY = "WebAppData".toByteArray(StandardCharsets.UTF_8)
        private val DEFAULT_MAX_AGE: Duration = Duration.ofHours(24)
    }
}
