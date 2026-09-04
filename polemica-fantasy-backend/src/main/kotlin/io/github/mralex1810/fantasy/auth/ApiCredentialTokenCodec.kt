package io.github.mralex1810.fantasy.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.springframework.stereotype.Component

@Component
class ApiCredentialTokenCodec {
    private val secureRandom = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(SECRET_BYTES)
        secureRandom.nextBytes(bytes)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isWellFormed(token: String): Boolean = TOKEN_PATTERN.matches(token)

    fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun hint(token: String): String = token.take(HINT_LENGTH)

    companion object {
        private const val PREFIX = "pfa_"
        private const val SECRET_BYTES = 32
        private const val HINT_LENGTH = 12
        private val TOKEN_PATTERN = Regex("^pfa_[A-Za-z0-9_-]{43}$")
    }
}
