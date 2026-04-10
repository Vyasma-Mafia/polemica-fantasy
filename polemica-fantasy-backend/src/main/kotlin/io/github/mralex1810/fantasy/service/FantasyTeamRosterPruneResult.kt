package io.github.mralex1810.fantasy.service

data class FantasyRosterPrunedCard(
    val telegramChatId: Long,
    val fantasyPlayerId: Long,
    val playerNickname: String,
)

data class FantasyTeamRosterPruneResult(
    val prunedCards: List<FantasyRosterPrunedCard>,
)
