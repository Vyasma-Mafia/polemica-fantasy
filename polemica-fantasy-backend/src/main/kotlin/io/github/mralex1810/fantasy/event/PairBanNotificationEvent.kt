package io.github.mralex1810.fantasy.event

data class PairBanNotificationEvent(
    val telegramChatId: Long,
    val reason: String,
    val fantikiConfiscated: Long,
    val newBalance: Long,
    /** One line per card, e.g. "Name (RARITY)". */
    val cardsConfiscated: List<String>,
)
