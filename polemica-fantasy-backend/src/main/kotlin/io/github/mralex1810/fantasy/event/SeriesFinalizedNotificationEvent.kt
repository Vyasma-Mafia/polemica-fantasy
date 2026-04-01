package io.github.mralex1810.fantasy.event

data class SeriesFinalizedRecipient(
    val telegramId: Long,
    val place: Int,
    val total: Int,
    val reward: Long,
    val balanceAfter: Long,
)

data class SeriesFinalizedNotificationEvent(
    val tournamentName: String,
    val seriesName: String,
    val recipients: List<SeriesFinalizedRecipient>,
)
