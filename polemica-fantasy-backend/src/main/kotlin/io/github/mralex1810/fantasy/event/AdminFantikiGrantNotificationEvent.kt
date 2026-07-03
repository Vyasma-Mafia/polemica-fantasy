package io.github.mralex1810.fantasy.event

data class AdminFantikiGrantNotificationEvent(
    val telegramUserId: Long,
    val amount: Long,
    val balanceAfter: Long,
    val adminReason: String,
)
