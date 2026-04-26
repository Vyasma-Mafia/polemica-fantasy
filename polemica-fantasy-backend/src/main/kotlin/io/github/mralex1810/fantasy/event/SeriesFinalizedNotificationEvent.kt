package io.github.mralex1810.fantasy.event

data class LeagueResult(
    val leagueName: String,
    val winnerPublicName: String?,
    val place: Int,
    val total: Int,
    val reward: Long,
)

data class SeriesFinalizedRecipient(
    val telegramId: Long,
    val leagueResults: List<LeagueResult>,
    val totalReward: Long,
    val balanceAfter: Long,
)

data class SeriesFinalizedNotificationEvent(
    val seriesId: Long,
    val tournamentName: String,
    val seriesName: String,
    val recipients: List<SeriesFinalizedRecipient>,
)
