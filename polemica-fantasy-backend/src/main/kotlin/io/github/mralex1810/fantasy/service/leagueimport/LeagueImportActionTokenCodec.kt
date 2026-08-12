package io.github.mralex1810.fantasy.service.leagueimport

import io.github.mralex1810.fantasy.config.TelegramLeagueImportProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class LeagueImportActionTokenCodec(
    private val properties: TelegramLeagueImportProperties,
) {
    fun encode(actionId: UUID): String {
        val compactId = actionId.toString().replace("-", "")
        val mac = sign(compactId)
        return "lic:$compactId:$mac".also { require(it.length <= 64) }
    }

    fun decode(value: String): UUID? {
        val parts = value.split(':')
        if (parts.size != 3 || parts[0] != "lic" || parts[1].length != 32) return null
        val expected = sign(parts[1]).toByteArray()
        if (!MessageDigest.isEqual(expected, parts[2].toByteArray())) return null
        val raw = parts[1]
        return runCatching {
            UUID.fromString("${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}")
        }.getOrNull()
    }

    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun sign(value: String): String {
        val secret = properties.callbackSigningSecret
        require(secret.isNotBlank()) { "Telegram league import callback signing secret is not configured" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.toByteArray())).take(16)
    }
}
