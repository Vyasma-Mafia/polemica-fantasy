package io.github.mralex1810.fantasy.event

import io.github.mralex1810.fantasy.service.FantasyRosterPrunedCard

internal fun buildSeriesRosterReplacementTelegramMessage(
    tournamentName: String,
    seriesName: String,
    prunedCardsForUser: List<FantasyRosterPrunedCard>,
    replacementNicknameByRemovedFantasyPlayerId: Map<Long, String>,
): String {
    require(prunedCardsForUser.isNotEmpty())
    val header = "Турнир «$tournamentName», серия «$seriesName».\n\n"
    if (prunedCardsForUser.size == 1) {
        val card = prunedCardsForUser.first()
        val oldName = card.playerNickname.trim().ifEmpty { "игрок" }
        val replacement = replacementNicknameByRemovedFantasyPlayerId[card.fantasyPlayerId]?.trim()?.takeIf { it.isNotEmpty() }
        val body = if (replacement != null) {
            "У вас в составе была карта игрока $oldName. Вместо него серию играет $replacement. Мы уже убрали её из вашей команды.\n\nМожете выставить нового игрока."
        } else {
            "У вас в составе была карта игрока $oldName. Он больше не входит в состав серии. Мы уже убрали её из вашей команды.\n\nМожете выставить нового игрока."
        }
        return header + body
    }
    val lines = prunedCardsForUser.map { card ->
        val oldName = card.playerNickname.trim().ifEmpty { "игрок" }
        val replacement = replacementNicknameByRemovedFantasyPlayerId[card.fantasyPlayerId]?.trim()?.takeIf { it.isNotEmpty() }
        if (replacement != null) {
            "• $oldName — вместо него серию играет $replacement (карта снята)."
        } else {
            "• $oldName — больше не в составе серии (карта снята)."
        }
    }
    return buildString {
        append(header)
        append(lines.joinToString("\n"))
        append("\n\nМожете выставить новых игроков в свободные слоты.")
    }
}
