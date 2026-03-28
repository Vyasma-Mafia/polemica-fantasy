package io.github.mralex1810.fantasy.auth

import java.time.Instant

data class ParsedInitData(
    val telegramUserId: Long,
    val username: String?,
    val firstName: String?,
    val authDate: Instant,
)
