package io.github.mralex1810.fantasy.event

data class SeriesRosterReplacementRecipient(
    val telegramChatId: Long,
    val messageText: String,
)

data class SeriesRosterReplacementNotificationEvent(
    val recipients: List<SeriesRosterReplacementRecipient>,
)
